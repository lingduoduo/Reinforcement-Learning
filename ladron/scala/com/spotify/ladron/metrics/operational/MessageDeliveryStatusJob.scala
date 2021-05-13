package com.spotify.ladron.metrics.operational

import java.time.{Duration, LocalDate}

import com.spotify.bcd.schemas.scala.NonEmptyString
import org.apache.beam.sdk.metrics.{Counter, Distribution}
import com.spotify.data.counters
import com.spotify.data.counters.Distributions.Mean
import com.spotify.data.counters.validation.rule.{StandardDeviationRule, ThresholdRule}
import com.spotify.ladron.Coders._
import com.spotify.ladron.config.Campaigns
import com.spotify.ladron.config.Campaigns.DeliveryCampaign
import com.spotify.ladron.delivery.{MessageActions, SystemMessageAction}
import com.spotify.ladron.syntax.metrics._
import com.spotify.ladron.util.{MetricRegistry, ScioJobBase}
import com.spotify.message.data.{Channel, EventType, MessageHistory}
import com.spotify.scio
import com.spotify.scio.ContextAndArgs
import com.spotify.scio.avro._
import com.spotify.scio.values.SCollection

object MessageDeliveryStatusJob extends ScioJobBase {
  override val metricRegistry: MetricRegistry =
    MetricRegistry.createMetricRegistry("delivery_metrics")

  final case class Args(
    messageDeliveryView: String,
    date: LocalDate,
    campaigns: Set[DeliveryCampaign]
  )
  object Args {
    def parse(scioArgs: scio.Args): Args = Args(
      messageDeliveryView = scioArgs("messageDeliveryView"),
      // scio will not pass dataPartition as an argument since it is being read as an option
      date = LocalDate.parse(scioArgs("date")),
      campaigns = Campaigns.MessageDeliveryCampaigns
    )
  }

  object Counters {
    val InputCount: Counter = metricRegistry.counter("input", "count")
    val CampaignCount: Counter = metricRegistry.counter("configured_campaigns", "count")
    val SentCount: Counter = metricRegistry.counter("sent", "count")

    val Types: Map[EventType, Counter] = EventType.values.map { eventType =>
      (eventType, metricRegistry.counter("events", eventType.name))
    }.toMap
  }

  object Ratios {
    private val UnknownRatioName = "Contains" + EventType.UnknownEventType.name
    val Unknown: Distribution = metricRegistry.distribution("messages", UnknownRatioName)

    private val PublishedRatioName = "Contains" + EventType.Published.name
    val Published: Distribution = metricRegistry.distribution("messages", PublishedRatioName)

    val SystemActions: Map[SystemMessageAction, Distribution] = SystemMessageAction.values.map {
      action =>
        (action, metricRegistry.distribution("system", action.name))
    }.toMap
  }

  object CampaignCounters {
    val AcceptedCount: Map[Campaigns.DeliveryCampaign, Counter] =
      Campaigns.MessageDeliveryCampaigns.map { campaign =>
        (campaign, metricRegistry.counter("accepted", s"${campaign._1}_${campaign._2}"))
      }.toMap

    val SentCount: Map[Campaigns.DeliveryCampaign, Counter] =
      Campaigns.MessageDeliveryCampaigns.map { campaign =>
        (campaign, metricRegistry.counter("sent", s"${campaign._1}_${campaign._2}"))
      }.toMap

    val FailedCount: Map[Campaigns.DeliveryCampaign, Counter] =
      Campaigns.MessageDeliveryCampaigns.map { campaign =>
        (campaign, metricRegistry.counter("failed", s"${campaign._1}_${campaign._2}"))
      }.toMap

    val UpstreamFailedCount: Map[Campaigns.DeliveryCampaign, Counter] =
      Campaigns.MessageDeliveryCampaigns.map { campaign =>
        (campaign, metricRegistry.counter("upstreamfailed", s"${campaign._1}_${campaign._2}"))
      }.toMap
  }

  def main(cmdArgs: Array[String]): Unit = {
    val (sc, scioArgs) = ContextAndArgs(cmdArgs)
    val args = Args.parse(scioArgs)

    val messageDeliveryView: SCollection[MessageHistory] = sc
      .withName("ReadMessageHistory")
      .avroFile[MessageHistory.Avro](args.messageDeliveryView)
      .transform("MapFromAvro")(_.map(MessageHistory.fromAvro))

    pipeline(args.campaigns)(messageDeliveryView)

    campaignMetricsPipeline(args.campaigns)(messageDeliveryView)

    close(sc)
  }

  def pipeline(
    campaigns: Set[Campaigns.DeliveryCampaign]
  )(messages: SCollection[MessageHistory]): SCollection[MessageHistory] = {
    val campaignMessages = messages
      .transform("FilterOnlyConfiguredCampaigns")(
        _.accumulateCount(Counters.InputCount)
          .filter(Campaigns.hasValidDeliveryCampaign(campaigns))
          .accumulateCount(Counters.CampaignCount)
      )

    campaignMessages
      .transform("CountMessageStatus")(
        _.map(messageStatus)
          .accumulateCount(Counters.Types)
      )

    campaignMessages
      .transform("MessagesWithUnknown")(
        _.accumulateBinaryRatio(Ratios.Unknown)(messageWithType(EventType.UnknownEventType))
      )

    campaignMessages
      .transform("AcceptedMessagesWithPublished")(
        _.filter(messageWithType(EventType.Accepted))
          .accumulateBinaryRatio(Ratios.Published)(messageWithType(EventType.Published))
      )

    campaignMessages
      .transform("SystemMessageActionRatios")(
        _.map(MessageActions.systemAction)
          .accumulateCount(Counters.SentCount)
          .accumulateBinaryRatios(Ratios.SystemActions)
      )

    campaignMessages
  }

  def campaignMetricsPipeline(
    campaigns: Set[Campaigns.DeliveryCampaign]
  )(messages: SCollection[MessageHistory]): SCollection[MessageHistory] = {
    val campaignsDeliveryView = messages
      .transform("FilterValidCampaigns")(
        _.filter(Campaigns.hasValidDeliveryCampaign(campaigns))
      )

    campaignsDeliveryView.transform("CountAcceptedCampaignEvents")(
      _.filter(messageWithType(EventType.Accepted))
        .map(messageWithChannel)
        .accumulateCount(CampaignCounters.AcceptedCount)
    )

    campaignsDeliveryView.transform("CountSentCampaignEvents")(
      _.filter(messageWithType(EventType.Sent))
        .map(messageWithChannel)
        .accumulateCount(CampaignCounters.SentCount)
    )

    campaignsDeliveryView.transform("CountFailedCampaignEvents")(
      _.filter(messageWithType(EventType.Failed))
        .map(messageWithChannel)
        .accumulateCount(CampaignCounters.FailedCount)
    )

    campaignsDeliveryView.transform("CountUpstreamFailedCampaignEvents")(
      _.filter(messageWithType(EventType.UpstreamFailed))
        .map(messageWithChannel)
        .accumulateCount(CampaignCounters.UpstreamFailedCount)
    )

    campaignsDeliveryView
  }

  def messageStatus(event: MessageHistory): EventType =
    event.events.last.eventType

  def messageWithType(eventType: EventType): MessageHistory => Boolean = { history =>
    history.events.exists(_.eventType == eventType)
  }

  def messageWithChannel(history: MessageHistory): (Channel, NonEmptyString) =
    (history.channel, NonEmptyString.fromAvro(history.campaignId.toString))

  override def validateCounters(ctx: counters.DataCountersValidationContext): Unit = {
    import Thresholds._
    ctx
      .validateCounter(Counters.InputCount, value().warningBelow(1))
      .validateDistribution(Ratios.Unknown, Mean, value().warningAbove(0.2))
      .validateDistribution(Ratios.Unknown, Mean, stdDevChange().warningAbove(1))
      .validateDistribution(Ratios.Published, Mean, value().warningBelow(0.8))
      .validateDistribution(Ratios.Published, Mean, stdDevChange().warningBelow(-1))
      .failOnValidationError()

//    The following code is to trigger warnings. will add it back after we figure out the cutoffs.
//    CampaignCounters.FailedCount.values.foreach(counter =>
//      ctx.validateCounter(
//        counter,
//        value()
//          .warningAbove(1000)
//      )
//    )
//    CampaignCounters.UpstreamFailedCount.values.foreach(counter =>
//      ctx.validateCounter(
//        counter,
//        value()
//          .warningAbove(1000)
//      )
//    )

  }

  object Thresholds {
    private val DaysInOneWeek: Int = 7
    private val LookBackDays: Int = 30

    def stdDevChange(): StandardDeviationRule =
      new StandardDeviationRule(Duration.ofDays(1))
        .maxLookback(LookBackDays)
        .minSampleSize(DaysInOneWeek)

    def value(): ThresholdRule = new ThresholdRule()
  }

}
