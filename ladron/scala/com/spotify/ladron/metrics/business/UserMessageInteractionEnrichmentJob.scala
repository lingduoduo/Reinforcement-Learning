package com.spotify.ladron.metrics.business

import java.time.LocalDate

import com.google.common.base.Preconditions
import org.apache.beam.sdk.metrics.Counter
import com.spotify.bcd.schemas.scala.{NonEmptyString, UserId}
import com.spotify.ladron.Coders._
import com.spotify.ladron.assignment.UserAssignment
import com.spotify.ladron.common.{AssignmentPolicy, MessageChannel, MessagingModule}
import com.spotify.ladron.eligibility.SelectionInfo
import com.spotify.ladron.metrics.UserMessageInteraction
import com.spotify.ladron.util.{MetricRegistry, ScioJobBase}
import com.spotify.ladron.syntax.metrics._
import com.spotify.message.data.{Channel, UserNotificationUnsubscribe}
import com.spotify.scio.coders.Coder
import com.spotify.scio.util.MultiJoin
import com.spotify.scio.values.SCollection

/*
 * This is a sub-pipeline of `UserMessageInteractionAggregationJob`
 * It resides here in a separate object
 * to separate between the creation and the enrichment of `UserMessageInteraction`.
 * */
object UserMessageInteractionEnrichmentJob extends ScioJobBase {

  override val metricRegistry: MetricRegistry =
    MetricRegistry.createMetricRegistry("user_message_interaction_extension_metrics")

  object Counters {
    val InputCounters: Map[MessagingModule, Counter] =
      moduleCounters("input")
    val OutputCounters: Map[MessagingModule, Counter] =
      moduleCounters("output")
    val AssignedMissingInteraction: Map[MessagingModule, Counter] =
      moduleCounters("assigned_user_missing_interaction")
    val MissingUserAssignment: Map[MessagingModule, Counter] =
      moduleCounters("missing_user_assignment")
    val MissingSelectionInfo: Map[MessagingModule, Counter] =
      moduleCounters("missing_selection_info")
    val MultipleUserAssignment: Map[MessagingModule, Counter] =
      moduleCounters("multiple_user_assignment")
    val MultipleMessageInteraction: Map[MessagingModule, Counter] =
      moduleCounters("multiple_user_interaction")

    private def moduleCounters(what: String): Map[MessagingModule, Counter] =
      MessagingModule.values.map { module =>
        module -> metricRegistry.counter(s"${module.name}_$what", "count")
      }.toMap
  }

  // scalastyle:off cyclomatic.complexity
  def enrichPipeline(
    userMessageInteraction: SCollection[IntermediateUserMessageInteraction],
    userAssignment: Map[MessagingModule, SCollection[UserAssignment]],
    userNotificationUnsubscribe: SCollection[UserNotificationUnsubscribe],
    selectionInfo: SCollection[SelectionInfo]
  ): SCollection[UserMessageInteraction] = {
    val countedInteractions = userMessageInteraction.accumulateCount { interaction =>
      Counters.InputCounters(interaction.module)
    }

    val umiWithSelectionId = decorateWithSelectionInfo(countedInteractions, selectionInfo)

    val keyedUserAssignment = UserIdModule.key(userAssignment)
    val keyedUnsub = UserIdModule.expandKey(userNotificationUnsubscribe)(_.userId)
    val keyedInteraction = UserIdModule.key(umiWithSelectionId)

    MultiJoin
      .cogroup(keyedInteraction, keyedUserAssignment, keyedUnsub)
      .flatMap {
        // filter out entries that is only there because of the unsubscribe expansion
        case (_, (interactions, assignment, _)) if interactions.isEmpty && assignment.isEmpty =>
          None
        // filter out those record where we only have assignment
        case (ki, (interactions, assignment, _)) if interactions.isEmpty && assignment.nonEmpty =>
          Counters.AssignedMissingInteraction(ki.module).inc(); None
        // count those messages we do have an interaction but we don't have assignment
        case (ki, (_, assignments, _)) if assignments.isEmpty =>
          Counters.MissingUserAssignment(ki.module).inc(); None
        case (ki, (msgInteractions, assignments, unsub)) =>
          if (assignments.view.slice(0, 2).size > 1) {
            Counters.MultipleUserAssignment(ki.module).inc()
          }
          if (msgInteractions.view.slice(0, 2).size > 1) {
            Counters.MultipleMessageInteraction(ki.module).inc()
          }
          msgInteractions.map {
            decorateUserMessageInteraction(ki.userId, _, assignments.last.policy, unsub)
          }
      }
      .accumulateCount { interaction =>
        Counters.OutputCounters(interaction.module)
      }
  }

  private case class UserIdModule(userId: UserId, module: MessagingModule)

  private object UserIdModule {

    /**
     * Expand t into all modules.
     */
    def expandKey[T: Coder](
      ts: SCollection[T]
    )(userId: T => UserId): SCollection[(UserIdModule, T)] =
      ts.flatMap { t =>
        MessagingModule.values.map { m =>
          UserIdModule(userId(t), m) -> t
        }
      }

    /**
     * Join all assignments into one SCollection keyed by user_id and module.
     */
    def key(
      ua: Map[MessagingModule, SCollection[UserAssignment]]
    ): SCollection[(UserIdModule, UserAssignment)] = {
      Preconditions.checkArgument(ua.nonEmpty)

      val context = ua.values.head.context
      val taggedAssignments = ua.map {
        case (module: MessagingModule, assignments: SCollection[UserAssignment]) =>
          assignments
            .withName(s"Tag $module assignments")
            .map { assignment =>
              UserIdModule(assignment.userId, module) -> assignment
            }
      }
      context.withName(ua.keys.mkString(" ++ ")).unionAll(taggedAssignments)
    }

    def key(
      uis: SCollection[UserMessageInteractionWithSelectionInfo]
    ): SCollection[(UserIdModule, UserMessageInteractionWithSelectionInfo)] = uis.keyBy { ui =>
      UserIdModule(ui.userId, ui.module)
    }
  }

  def decorateWithSelectionInfo(
    umis: SCollection[IntermediateUserMessageInteraction],
    selectionInfos: SCollection[SelectionInfo]
  ): SCollection[UserMessageInteractionWithSelectionInfo] = {
    umis
      .keyBy(_.recordId)
      .leftOuterJoin(selectionInfos.keyBy(_.recordId))
      .values
      .flatMap {
        case (umi, None)                => Counters.MissingSelectionInfo(umi.module).inc(); None
        case (umi, Some(selectionInfo)) => Some((umi, selectionInfo))
      }
      .map {
        case (umi, selectionInfo) =>
          UserMessageInteractionWithSelectionInfo(
            userId = umi.userId,
            selectionInfo = selectionInfo,
            campaignId = umi.campaignId,
            messageId = umi.messageId,
            channel = umi.channel,
            isOpen = umi.isOpen,
            isClick = umi.isClick,
            isReject = umi.isReject,
            isNoAction = umi.isNoAction,
            module = umi.module,
            partitionDate = umi.partitionDate
          )
      }
  }

  private def decorateUserMessageInteraction(
    userId: UserId,
    userMessageInteraction: UserMessageInteractionWithSelectionInfo,
    assignmentPolicy: AssignmentPolicy,
    unsub: Iterable[UserNotificationUnsubscribe]
  ): UserMessageInteraction = {
    val (pushUnsubs, emailUnsubs) = unsub.partition(_.channel == Channel.Push)

    val pushUnsub = pushUnsubs
      .foldLeft(false)((acc, uns) => acc || uns.appUnsubscribed || uns.osUnsubscribed)

    val emailUnsub = emailUnsubs
      .foldLeft(false)((acc, uns) => acc || uns.appUnsubscribed || uns.osUnsubscribed)

    val unsubscribed = userMessageInteraction.channel match {
      case MessageChannel.Push => pushUnsub
      case _                   => emailUnsub
    }

    UserMessageInteraction(
      userId = userId,
      module = userMessageInteraction.module,
      assignmentPolicy = assignmentPolicy,
      randomlySelected = userMessageInteraction.selectionInfo.explore,
      selectionId = userMessageInteraction.selectionInfo.selectionId,
      campaignId = userMessageInteraction.campaignId,
      messageId = userMessageInteraction.messageId,
      channel = userMessageInteraction.channel,
      isClick = userMessageInteraction.isClick,
      isOpen = userMessageInteraction.isOpen,
      isReject = userMessageInteraction.isReject || unsubscribed,
      isNoAction = userMessageInteraction.isNoAction,
      partitionDate = userMessageInteraction.partitionDate
    )
  }

  case class UserMessageInteractionWithSelectionInfo(
    userId: UserId,
    selectionInfo: SelectionInfo,
    campaignId: NonEmptyString,
    messageId: NonEmptyString,
    channel: MessageChannel,
    isOpen: Boolean,
    isClick: Boolean,
    isReject: Boolean,
    isNoAction: Boolean,
    module: MessagingModule,
    partitionDate: LocalDate
  )
}
