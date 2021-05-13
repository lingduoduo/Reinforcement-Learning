package com.spotify.ladron.selection

import com.spotify.ladron.Coders._
import com.spotify.ladron.agent.model.{ExampleModel, NullModel}
import com.spotify.ladron.agent.policy.RandomPolicy
import com.spotify.ladron.agent.{Bandit, BanditPolicy, Model, ModelArm}
import com.spotify.ladron.candidate.CandidateMessage
import com.spotify.ladron.common.{AssignmentPolicy, MessageChannel, MessagingModule}
import com.spotify.ladron.config.assignment.policy.ActivationAssignmentPolicies
import com.spotify.ladron.model.{
  AssignedCandidateMessages,
  AssignedSelectedMessage,
  CandidateMessageWithContext,
  UserWithContext
}
import com.spotify.ladron.selection.CandidateContext.CandidateId
import com.spotify.ladron.selection.MessageSelectionBandits.{BanditModelType, BanditPolicyType}
import com.spotify.ladron.syntax.tensorflow._
import com.spotify.ladron.train.{Context, ContextFreeModelByContext}
import com.spotify.ladron.util.{ActivationBaselineCampaigns, MetricRegistry, ScioJobBase}
import com.spotify.scio.values.SCollection
import org.apache.beam.sdk.metrics.Counter
import org.tensorflow.example.Example

/**
 * Selects which message template should be used per user by ranking the provided options.
 */
object MessageSelectionJob extends ScioJobBase {
  override val metricRegistry: MetricRegistry =
    MetricRegistry.createMetricRegistry("selection_metrics")

  object Counters {
    val MultipleMessageCount: Counter =
      metricRegistry.counter("input", "multiple_candidates")

    val NoAgentCount: Counter = metricRegistry.counter("selection", "no_agent")
    val MissingContext: Counter = metricRegistry.counter("missing_context", "count")

    val NoMessagesCount: Counter = metricRegistry.counter("output", "no_message")
  }

  case class Parameters(
    optionsByPolicy: Map[AssignmentPolicy, BanditPolicy],
    partition: PartitionString,
    module: MessagingModule
  )

  /**
   * Select one candidate message based on assigned group policy.
   *
   * @see select
   */
  def selectCandidates(
    candidates: SCollection[AssignedCandidateMessages],
    contextFreeModels: SCollection[ContextFreeModelByContext],
    contextualModels: Map[String, String],
    optionsByPolicy: Map[AssignmentPolicy, BanditPolicy],
    partition: PartitionString,
    module: MessagingModule,
    userToCountry: SCollection[UserWithContext],
    candidateContexts: SCollection[CandidateContext]
  ): SCollection[AssignedSelectedMessage] = {
    val c = joinWithContext(candidates, userToCountry, candidateContexts)
    val parameters = Parameters(optionsByPolicy, partition, module)
    select(parameters, c, contextFreeModels, contextualModels)
  }

  /**
   * Group by userId and collect context needed for selecting bandits and scoring models.
   */
  private[selection] def joinWithContext(
    candidates: SCollection[AssignedCandidateMessages],
    countries: SCollection[UserWithContext],
    contexts: SCollection[CandidateContext]
  ): SCollection[(AssignedCandidateMessages, SelectionContext)] = {
    val candidatesByUser = candidates.keyBy(_.userId)
    val countriesByUser = countries.keyBy(_.userId)
    val contextsByUser = contexts.keyBy(_.userId)
    candidatesByUser
      .cogroup(countriesByUser, contextsByUser)
      .flatMap {
        case (userId, (groupedCandidates, countries, contexts)) =>
          if (groupedCandidates.isEmpty) {
            None
          } else if (groupedCandidates.size > 1) {
            val msg = "Invalid selection, candidates must already be grouped by userId"
            throw new IllegalArgumentException(msg)
          } else {
            val candidateAssignedSelectedMessage = groupedCandidates.head
            val country: String =
              countries.headOption.map(_.registrationCountry).getOrElse(Context.Global)
            val contextByCandidate: Map[CandidateId, Example] = contexts.map { context =>
              context.candidateId -> context.context
            }(collection.breakOut)
            Option(
              candidateAssignedSelectedMessage -> SelectionContext(
                userId,
                country,
                contextByCandidate
              )
            )
          }
      }
  }

  /**
   * Select one option out of several based on assigned group policy.
   * The logic used to select an option depends on the user assignment policy.
   */
  private def select(
    parameters: Parameters,
    candidates: SCollection[(AssignedCandidateMessages, SelectionContext)],
    contextFreeModels: SCollection[ContextFreeModelByContext],
    contextualModels: Map[String, String]
  ): SCollection[AssignedSelectedMessage] = {
    val (contextual, contextFree) = candidates.partition {
      case (candidate, _) =>
        MessageSelectionBandits.modelType(candidate.group) == BanditModelType.Contextual
    }

    val selectedContextual = selectContextual(parameters, contextual, contextualModels)

    val selectedContextFree = selectContextFree(
      parameters,
      contextFree,
      contextFreeModels
    )

    selectedContextual match {
      case Some(selected) => selected ++ selectedContextFree
      case None           => selectedContextFree
    }
  }

  /**
   * Provide Session#Runner to contextual bandit selections for each model, reduce left to
   * combine into one SCollection of selections return option (for if non
   */
  def selectContextual(
    parameters: Parameters,
    candidates: SCollection[(AssignedCandidateMessages, SelectionContext)],
    contextualModels: Map[String, String]
  ): Option[SCollection[AssignedSelectedMessage]] = {
    contextualModels
      .map {
        case (modelName, modelUri) =>
          candidates
            .filter {
              case (assignedBucket, _) =>
                assignedBucket.group.model.exists(_.value == modelName)
            }
            .predictWithExample(modelUri) {
              case ((candidate, context), runner) =>
                selectWithDependency(
                  parameters,
                  candidate,
                  context,
                  Right(runner)
                )
            }
            .flatten
      }
      .reduceOption(_ ++ _)
  }

  /**
   * Provide Map[Context, ContextFreeModel] to context free bandit selections.
   */
  def selectContextFree(
    parameters: Parameters,
    candidates: SCollection[(AssignedCandidateMessages, SelectionContext)],
    mabs: SCollection[ContextFreeModelByContext]
  ): SCollection[AssignedSelectedMessage] = {
    val contextFreeSide = mabs.keyBy(_.context).mapValues(_.bandit).asMapSideInput
    candidates
      .withSideInputs(contextFreeSide)
      .flatMap {
        case ((candidate, context), ctx) =>
          val contextFree = ctx(contextFreeSide)
          selectWithDependency(
            parameters,
            candidate,
            context,
            Left(contextFree)
          )
      }
      .toSCollection
  }

  type SelectionDependency = Either[Map[Context, Model], Example => Float]

  /**
   * Select context free or contextual with correct dependency.
   * NB an exception will be thrown if the assigned bandit dependency is not supplied,
   * this should have been taken care of in the split in the initial select method.
   */
  def selectWithDependency(
    parameters: Parameters,
    candidates: AssignedCandidateMessages,
    context: SelectionContext,
    dependency: SelectionDependency
  ): Seq[AssignedSelectedMessage] = {
    countAlternatives(candidates.messages)

    val selection: Seq[AssignedSelectedMessage] =
      selectByChannel(
        parameters,
        candidates,
        context,
        dependency
      )

    countSelected(selection)

    selection
  }

  /**
   * Select a message by channel by using the assigned bandit.
   */
  //scalastyle:off method.length
  def selectByChannel(
    parameters: Parameters,
    candidates: AssignedCandidateMessages,
    context: SelectionContext,
    selectionDependency: SelectionDependency
  ): Seq[AssignedSelectedMessage] =
    candidates.messages
      .groupBy(_.channel)
      .toSeq
      .flatMap {
        case (channel, messages) =>
          val bandit = banditFor(
            candidates.group,
            channel,
            context,
            parameters,
            selectionDependency
          )

          val selectionId = SelectionId.create(
            candidates.userId,
            channel,
            parameters.module,
            parameters.partition
          )

          val alternatives = messagesForAssignment(
            decorateMessagesWithContext(messages, context.contextByCandidate),
            candidates.group
          )

          if (alternatives.isEmpty) {
            None
          } else {
            val arms = buildArmsFor(alternatives)
            val selectedArm = bandit.selectArm(arms)
            Some(
              AssignedSelectedMessage(
                candidates.userId,
                selectionId,
                alternatives,
                selectedArm.value,
                candidates.group,
                selectedArm.score,
                selectedArm.explore
              )
            )
          }
      }

  def banditFor(
    assignment: AssignmentPolicy,
    channel: MessageChannel,
    context: SelectionContext,
    parameters: Parameters,
    selectionDependency: SelectionDependency
  ): Bandit = {
    val model = modelFor(assignment, channel, context, selectionDependency)
    Bandit(
      model,
      policyFor(assignment, channel, parameters, selectionDependency, model)
    )
  }

  def modelFor(
    assignment: AssignmentPolicy,
    channel: MessageChannel,
    context: SelectionContext,
    selectionDependency: SelectionDependency
  ): Model = {
    // This throws exceptions on Either.get if the dependency is not the correct type.
    MessageSelectionBandits.modelType(assignment) match {
      case BanditModelType.Contextual => ExampleModel(selectionDependency.right.get)
      case BanditModelType.ContextFree =>
        val mabByContext = selectionDependency.left.get
        val countryBandit = mabByContext.get(Context(channel, context.registrationCountry))
        // In case when we don't have bandit policy for certain country we want to fallback
        // to global bandit policy
        val globalBandit = mabByContext.get(Context(channel, Context.Global))
        countryBandit.orElse(globalBandit).getOrElse(NullModel)
      case BanditModelType.Null => NullModel
    }
  }

  def policyFor(
    assignment: AssignmentPolicy,
    channel: MessageChannel,
    parameters: Parameters,
    selectionDependency: SelectionDependency,
    model: Model
  ): BanditPolicy = {
    val policyType = MessageSelectionBandits.policyType(assignment)
    val policy = parameters.optionsByPolicy.get(assignment)
    // Until a model has been trained, only send Random activation/engagement emails.
    // Test spec https://docs.google.com/document/d/1kFoTaMu2rt7nSA4KE-pU5Dtm8UEWcOHkspFCIB-3sbY
    if (channel == MessageChannel.Email && (parameters.module == MessagingModule.Engagement ||
        parameters.module == MessagingModule.Activation)) {
      RandomPolicy()
    } else if (policy.isEmpty) {
      throw new RuntimeException("No policy configured for assignment " + assignment)
    } else if (model == NullModel && policyType != BanditPolicyType.Random) {
      // All non random policy types require models, fallback to Random if missing.
      Counters.NoAgentCount.inc()
      RandomPolicy()
    } else {
      policy.get
    }
  }

  def decorateMessagesWithContext(
    alternatives: Seq[CandidateMessage],
    contextByCandidate: Map[CandidateId, Example]
  ): Seq[CandidateMessageWithContext] = {
    alternatives.map { msg =>
      val campaignId = CampaignId(msg.channel, msg.campaignId)
      val recordId = RecordId(msg.recordId)
      val example = contextByCandidate
        .get(Left(campaignId))
        .orElse(contextByCandidate.get(Right(recordId)))
        .getOrElse({ Counters.MissingContext.inc(); Example.getDefaultInstance })
      CandidateMessageWithContext(msg, example)
    }
  }

  /**
   * !HACK!
   *
   * Needed in order to replicate the behaviour of old nancy manually setup campaigns
   *
   * @return might be empty if no matching campaigns are found
   */
  def messagesForAssignment(
    messages: Seq[CandidateMessageWithContext],
    assignment: AssignmentPolicy
  ): Seq[CandidateMessageWithContext] = {
    assignment match {
      case ActivationAssignmentPolicies.ActivationBaselinePolicy =>
        messages.filter(ActivationBaselineCampaigns.filterMessages)
      case _ =>
        messages
    }
  }

  def buildArmsFor(messages: Seq[CandidateMessageWithContext]): Seq[ModelArm[CandidateMessage]] =
    messages.map(msg => ModelArm(msg.message.campaignId.value, msg.context, msg.message))

  def countAlternatives[A](alternatives: Seq[A]): Unit =
    if (alternatives.size > 1) Counters.MultipleMessageCount.inc()

  def countSelected[AssignedSelectedMessage](
    selected: Seq[AssignedSelectedMessage]
  ): Unit = if (selected.isEmpty) Counters.NoMessagesCount.inc()
}
