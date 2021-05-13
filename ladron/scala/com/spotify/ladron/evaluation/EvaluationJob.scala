package com.spotify.ladron.evaluation

import com.google.common.annotations.VisibleForTesting
import com.spotify.bcd.schemas.scala.NonEmptyString
import com.spotify.ladron.Coders._
import com.spotify.ladron.agent.ModelArm
import com.spotify.ladron.agent.policy.{EpsilonGreedyPolicy, RandomPolicy}
import com.spotify.ladron.common.{MessageChannel, MessagingModule}
import com.spotify.ladron.evaluation.Evaluation.{EvaluationContext, EvaluationResults}
import com.spotify.ladron.metrics.UserMessageInteraction
import com.spotify.ladron.syntax.agent.BanditContextSyntax
import com.spotify.ladron.syntax.textfile.CsvExtension
import com.spotify.ladron.train.UserMessageInteractionAgentTrainingJob.getReward
import com.spotify.ladron.util.{MetricRegistry, ScioJobBase}
import com.spotify.scio
import com.spotify.scio.values.SCollection
import com.spotify.scio.ContextAndArgs
import com.spotify.scio.tensorflow._
import com.spotify.multiple.syntax._
import org.apache.beam.sdk.metrics.Gauge
import org.tensorflow.example.Example

/**
 * The evaluation algorithm base on this paper: https://arxiv.org/abs/1003.0146.
 * It was adapted for context-free and contextual use cases.
 * In this job, we evaluate different bandits algorithms and the result of
 * evaluation is stored in a csv file which will be stored in {output}/result.csv.
 */
object EvaluationJob extends ScioJobBase {
  case class EvaluationInfo(
    candidates: List[NonEmptyString],
    sentCampaign: NonEmptyString,
    reward: Double
  )

  override val metricRegistry: MetricRegistry =
    MetricRegistry.createMetricRegistry("ladron_evaluation_metrics")

  object Counters {
    // Gauge counter only supports Long, so we multiply our values by 100k
    val MetricName: String = "clicksPer100kSamples"

    val gauges: Map[String, Map[MessageChannel, Gauge]] =
      List(Evaluation.Contextual, Evaluation.ContextFree).map { banditType =>
        banditType -> MessageChannel.values.map { channel =>
          channel -> metricRegistry.gauge(s"$banditType-Egreedy(${Evaluation.Epsilon})", MetricName)
        }.toMap
      }.toMap
  }

  final case class Args(
    userMessageInteraction: List[String],
    outputPath: String,
    matchedContexts: String,
    contextFreeModel: String,
    contextualModel: String,
    module: String,
    compareWithRandomBaseline: Boolean
  )

  object Args {
    def parse(scioArgs: scio.Args): Args = Args(
      userMessageInteraction = scioArgs.list("userMessageInteraction"),
      outputPath = scioArgs("output"),
      matchedContexts = scioArgs("matchedContexts"),
      contextualModel = scioArgs("contextualModel"),
      contextFreeModel = scioArgs("contextFreeModel"),
      module = scioArgs("module"),
      compareWithRandomBaseline = scioArgs.boolean("compareWithRandom", false)
    )
  }

  def main(cmdArgs: Array[String]): Unit = {
    val (sc, scioArgs) = ContextAndArgs(cmdArgs)
    val args = Args.parse(scioArgs)

    val userInteractions = sc
      .avroFiles[UserMessageInteraction.Avro](args.userMessageInteraction)
      .withName("MapFromAvro")
      .map(UserMessageInteraction.fromAvro)

    val matchedContexts = sc
      .withName("ReadMatchedContexts")
      .tfRecordExampleFile(args.matchedContexts)
      .withName("toMatchedContext")
      .flatMap(MatchedContext.fromExample) // we ignore the empty Examples

    val evaluations = createEvaluationDataset(args.module)(userInteractions, matchedContexts)
    val banditPolicy = EpsilonGreedyPolicy(Evaluation.Epsilon)

    val bandits = sc.readBanditWithContext(args.contextFreeModel)
    ContextFreeEvaluation
      .pipeline(evaluations, banditPolicy, bandits)
      .map(updateCounters)
      .map(convertToString)
      .saveAsSingleTextFile(s"${args.outputPath}/result-context-free", Evaluation.header)

    ContextualEvaluation
      .pipeline(evaluations, banditPolicy, args.contextualModel)
      .map(updateCounters)
      .map(convertToString)
      .saveAsSingleTextFile(s"${args.outputPath}/result-contextual", Evaluation.header)

    if (args.compareWithRandomBaseline) {
      val randomPolicy = RandomPolicy()

      ContextualEvaluation
        .pipeline(evaluations, randomPolicy, args.contextualModel)
        .map(updateCounters)
        .map(convertToString)
        .saveAsSingleTextFile(s"${args.outputPath}/result-random", Evaluation.header)
    }

    close(sc)
  }

  @VisibleForTesting
  def createEvaluationDataset(module: String)(
    interactions: SCollection[UserMessageInteraction],
    matchedContext: SCollection[MatchedContext]
  ): SCollection[EvaluationContext] = {
    val userInteractionsFiltered = interactions
      .filter(_.randomlySelected)
      .filter(Evaluation.removeTestCampaigns)
      .filter(_.module == MessagingModule.withName(module))
      .keyBy(_.selectionId.id)

    matchedContext
      .groupBy(_.selection_id)
      .join(userInteractionsFiltered)
      .values
      .map { case (contexts, interaction) => createEvaluationContextRecord(interaction, contexts) }
  }

  private def createEvaluationContextRecord(
    interaction: UserMessageInteraction,
    matchedContexts: Iterable[MatchedContext]
  ): EvaluationContext = {
    val candidates = matchedContexts
      .map { context =>
        val example = removeSelectionIdFromExample(context.context)
        ModelArm(context.campaign_id.value, example, context.campaign_id)
      }

    val allCountries = matchedContexts.map(_.country).toSet
    assert(
      allCountries.size == 1,
      "Candidate features have different user country parameters. That should never happen."
    )

    assert(candidates.toSet.size == candidates.size, "There are duplicate messages")

    EvaluationContext(
      candidates.toList,
      interaction.campaignId,
      allCountries.head,
      interaction.channel,
      getReward(interaction)
    )
  }

  @VisibleForTesting
  def removeSelectionIdFromExample(example: Example): Example = {
    val filteredFeatures = example.getFeatures.toBuilder.removeFeature("selection_id").build()
    Example.newBuilder().setFeatures(filteredFeatures).build()
  }

  private def updateCounters(result: EvaluationResults): EvaluationResults = {
    val value = (result.averageCtr * 100000).toLong // convert to Long as Gauge supports only Long
    Counters.gauges(result.scoreModel)(result.channel).set(value)
    result
  }

  private def convertToString(result: EvaluationResults): String = {
    val channel = result.channel
    val policy = result.banditPolicyName
    val average = result.averageCtr
    val numSamples = result.numSamples
    s"$channel,$policy,$numSamples,$average"
  }
}
