package com.spotify.ladron.metrics.business

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime, ZoneOffset}

import com.spotify.bcd.schemas.scala.{NonEmptyString, UserId}
import com.spotify.ladron.Coders._
import com.spotify.ladron.assignment.UserAssignment
import com.spotify.ladron.common.{MessageChannel, MessagingModule}
import com.spotify.ladron.config.Campaigns
import com.spotify.ladron.config.Campaigns.DeliveryCampaign
import com.spotify.ladron.delivery.{MessageActions, SystemMessageAction}
import com.spotify.ladron.eligibility.SelectionInfo
import com.spotify.ladron.metrics.UserMessageInteraction
import com.spotify.ladron.syntax.metrics._
import com.spotify.ladron.util.{MetricRegistry, ScioJobBase}
import com.spotify.message.data.EventType.{Click, Open, SpamReport, Unsubscribe}
import com.spotify.message.data.Metadata.RecordId
import com.spotify.message.data.{Channel, EventType, MessageHistory, UserNotificationUnsubscribe}
import com.spotify.scio
import com.spotify.scio.ContextAndArgs
import com.spotify.scio.avro._
import com.spotify.multiple.syntax._
import com.spotify.scio.values.SCollection
import org.apache.beam.sdk.metrics.Counter

import scala.collection.immutable.HashSet

object UserMessageInteractionAggregationJob extends ScioJobBase {
  override val metricRegistry: MetricRegistry =
    MetricRegistry.createMetricRegistry("user_interaction_metrics")

  metricRegistry.register(UserMessageInteractionEnrichmentJob.metricRegistry)

  object Counters {
    val InputCount: Counter = metricRegistry.counter("input", "count")
    val InputRelevantCount: Counter = metricRegistry.counter("input_relevant", "count")
    val MsgNoRecordIdCount: Counter = metricRegistry.counter("no_record_id", "count")
    val MsgMissingMetadata: Counter = metricRegistry.counter("no_metadata", "count")
  }

  case class Args(
    messageDeliveryView: String,
    engagementUserAssignment: Option[String],
    reactivationUserAssignment: Option[String],
    activationUserAssignment: List[String],
    engagementSelectionInfo: Option[String],
    reactivationSelectionInfo: Option[String],
    activationSelectionInfo: List[String],
    userNotificationUnsubscribe: List[String],
    date: LocalDate,
    output: String,
    messageOffset: Int,
    campaigns: Map[DeliveryCampaign, MessagingModule]
  )

  object Args {
    def parse(scioArgs: scio.Args): Args = Args(
      messageDeliveryView = scioArgs("messageDeliveryView"),
      engagementUserAssignment = scioArgs.optional("engagementUserAssignment"),
      reactivationUserAssignment = scioArgs.optional("reactivationUserAssignment"),
      activationUserAssignment = scioArgs.list("activationUserAssignment"),
      engagementSelectionInfo = scioArgs.optional("engagementSelectionInfo"),
      reactivationSelectionInfo = scioArgs.optional("reactivationSelectionInfo"),
      activationSelectionInfo = scioArgs.list("activationSelectionInfo"),
      userNotificationUnsubscribe = scioArgs.list("userNotificationUnsubscribe"),
      date = LocalDate.parse(scioArgs("date"), DateTimeFormatter.ISO_LOCAL_DATE),
      output = scioArgs("output"),
      messageOffset = scioArgs.int("messageOffset"),
      campaigns = Campaigns.MessageDeliveryCampaignToOriginModule
    )
  }

  // scalastyle:off method.length
  def main(cmdArgs: Array[String]): Unit = {
    val (sc, scioArgs) = ContextAndArgs(cmdArgs)
    val args = Args.parse(scioArgs)

    val messageHistory = sc
      .withName("Read MessageHistory")
      .avroFile[MessageHistory.Avro](args.messageDeliveryView)
      .transform("fromAvro MessageHistory")(_.map(MessageHistory.fromAvro))

    val userUnsub = sc
      .avroFiles[UserNotificationUnsubscribe.Avro](args.userNotificationUnsubscribe)
      .withName("fromAvro UserNotificationUnsubscribe")
      .map(UserNotificationUnsubscribe.fromAvro)

    val reactivationUserAssignment = args.reactivationUserAssignment
      .map(sc.withName("Read ReactivationAssignment").avroFile[UserAssignment.Avro](_))
      .getOrElse(sc.withName("Empty ReactivationAssignment").empty[UserAssignment.Avro])
      .transform("fromAvro ReactivationAssignment")(_.map(UserAssignment.fromAvro))

    val engagementUserAssignment = args.engagementUserAssignment
      .map(sc.withName("Read EngagementAssignment").avroFile[UserAssignment.Avro](_))
      .getOrElse(sc.withName("Empty EngagementAssignment").empty[UserAssignment.Avro])
      .transform("fromAvro EngagementAssignment")(_.map(UserAssignment.fromAvro))

    val activationUserAssignment = sc
      .avroFiles[UserAssignment.Avro](args.activationUserAssignment)
      .withName("fromAvro ActivationAssignment")
      .map(UserAssignment.fromAvro)

    val userAssignment: Map[MessagingModule, SCollection[UserAssignment]] = Map(
      MessagingModule.Reactivation -> reactivationUserAssignment,
      MessagingModule.Engagement -> engagementUserAssignment,
      MessagingModule.Activation -> activationUserAssignment
    )

    val reactivationSelectionInfo = args.reactivationSelectionInfo
      .map(sc.withName("Read ReactivationSelectionInfo").avroFile[SelectionInfo.Avro](_))
      .getOrElse(sc.withName("Empty ReactivationSelectionInfo").empty[SelectionInfo.Avro])
      .transform("fromAvro ReactivationSelectionInfo")(_.map(SelectionInfo.fromAvro))

    val engagementSelectionInfo = args.engagementSelectionInfo
      .map(sc.withName("Read EngagementSelectionInfo").avroFile[SelectionInfo.Avro](_))
      .getOrElse(sc.withName("Empty EngagementSelectionInfo").empty[SelectionInfo.Avro])
      .transform("fromAvro EngagementSelectionInfo")(_.map(SelectionInfo.fromAvro))

    val activationSelectionInfo = sc
      .avroFiles[SelectionInfo.Avro](args.activationSelectionInfo)
      .withName("fromAvro ActivationSelectionInfo")
      .map(SelectionInfo.fromAvro)

    val selectionInfo: SCollection[SelectionInfo] = SCollection.unionAll(
      Seq(
        reactivationSelectionInfo,
        engagementSelectionInfo,
        activationSelectionInfo
      )
    )

    val userMetrics = messageHistory
      .transform("MapToUserInteraction")(userMetricsPipeline(args))

    val output = UserMessageInteractionEnrichmentJob
      .enrichPipeline(userMetrics, userAssignment, userUnsub, selectionInfo)
      .transform("toAvro UserMessageInteraction")(_.map(UserMessageInteraction.toAvro))

    output.withName("Write UserMessageInteraction").saveAsAvroFile(args.output)

    close(sc)
  }

  def userMetricsPipeline(args: Args)(
    msgDeliveryView: SCollection[MessageHistory]
  ): SCollection[IntermediateUserMessageInteraction] =
    msgDeliveryView
      .transform(_.accumulateCount(Counters.InputCount))
      .transform("FilterCampaigns")(filterValidCampaigns(args.campaigns.keys.toSet))
      .transform("FilterSentDateSpecificEvents") {
        _.filter { mh =>
          hasBeenSent(mh) && wasAcceptedOnDay(args.date.minusDays(args.messageOffset), mh)
        }
      }
      .transform("FilterDryRun")(_.filter(isNonDryRun))
      .transform("BuildIntermediateUMI")(_.flatMap(fromMessageHistory(_, args.campaigns)))

  def filterValidCampaigns(campaigns: Set[DeliveryCampaign])(
    events: SCollection[MessageHistory]
  ): SCollection[MessageHistory] =
    events
      .filter(Campaigns.hasValidDeliveryCampaign(campaigns))
      .accumulateCount(Counters.InputRelevantCount)

  private def isNonDryRun(value: MessageHistory): Boolean =
    !value.metadata.flatMap(_.dryRun).getOrElse(false)

  private[business] def fromMessageHistory(
    value: MessageHistory,
    campaigns: Map[DeliveryCampaign, MessagingModule]
  ): Option[IntermediateUserMessageInteraction] = {
    val sent = MessageActions.systemAction(value) == SystemMessageAction.Sent
    val events: Set[EventType] = HashSet(value.events.map(_.eventType): _*)

    val sendDate: LocalDate = messageSendDate(value)

    def has(et: EventType*): Boolean = List(et: _*).exists(events.contains)
    val isClick = value.channel match {
      case Channel.Push => has(Open, Click)
      case _            => has(Click)
    }

    for {
      metadata <- value.metadata.countEmpty(Counters.MsgMissingMetadata)
      recordId <- metadata.recordId.countEmpty(Counters.MsgNoRecordIdCount)
    } yield IntermediateUserMessageInteraction(
      userId = value.userId,
      recordId = recordId,
      campaignId = value.campaignId,
      messageId = value.messageId,
      channel = MessageChannel.withName(value.channel.name),
      isOpen = has(Open),
      isClick = isClick,
      isReject = has(Unsubscribe, SpamReport),
      isNoAction = !has(Open, Unsubscribe, SpamReport, Click) && sent,
      module = module(value.channel -> value.campaignId, campaigns),
      partitionDate = sendDate
    )
  }

  /*
   * We look at the `Accepted` event to match the day we created the assignment for that user.
   * `.head` is unsafe call but we expect Accepted event type to always be present.
   * */
  private def messageSendDate(value: MessageHistory) = {
    value.events
      .filter(_.eventType == EventType.Accepted)
      .map(_.time)
      .map(LocalDateTime.ofInstant(_, ZoneOffset.UTC).toLocalDate)
      .head
  }

  /**
   * We look at the `Accepted` event to match the day we created the assignment for that user.
   * This allows us to easily match what happen to the events we "sent" to the messenger APIs, since
   * we just need to care about when we send it to the APIs, and not when the actual message was
   * sent.
   */
  private def wasAcceptedOnDay(date: LocalDate, history: MessageHistory): Boolean =
    history.events.exists { event =>
      event.eventType == EventType.Accepted &&
      LocalDateTime.ofInstant(event.time, ZoneOffset.UTC).toLocalDate.equals(date)
    }

  /**
   * Implicit class/method for elegantly counting empty options and incrementing a counter
   */
  implicit class CountedOption[A](o: Option[A]) {
    def countEmpty(counter: Counter): Option[A] = {
      if (o.isEmpty) counter.inc()
      o
    }
  }

  /**
   * We only consider messages that were sent by the delivery layer.
   */
  private def hasBeenSent(history: MessageHistory): Boolean =
    history.events.exists(_.eventType == EventType.Sent)

  private def module(
    campaign: DeliveryCampaign,
    campaigns: Map[DeliveryCampaign, MessagingModule]
  ): MessagingModule =
    campaigns.getOrElse(campaign, MessagingModule.Unknown)
}

/*
 * This is the trimmed version of `UserMessageInteractionAggregationExtension`
 * This schema doesn't contain `assignmentPolicy`
 * */
final case class IntermediateUserMessageInteraction(
  userId: UserId,
  recordId: RecordId,
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
