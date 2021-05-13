package com.spotify.ladron.eligibility

import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDate, ZoneOffset}

import com.spotify.ladron.NonNegativeInt
import com.spotify.ladron.Coders._
import com.spotify.ladron.config.Campaigns
import com.spotify.ladron.config.Campaigns.DeliveryCampaign
import com.spotify.ladron.syntax.metrics._
import com.spotify.ladron.util.{MetricRegistry, ScioJobBase}
import com.spotify.message.data.{EventType, MessageHistory => DeliveryHistory}
import com.spotify.scio
import com.spotify.scio.ContextAndArgs
import com.spotify.scio.values.SCollection
import com.spotify.multiple.syntax._
import org.apache.beam.sdk.metrics.Counter

/**
 * Job that filters message history for a user.
 * We might want to produce this in the message-delivery-view,
 * but it's not clear if it will be re-used.
 */
object MessageHistoryJob extends ScioJobBase {
  override val metricRegistry: MetricRegistry =
    MetricRegistry.createMetricRegistry("message_history")

  final case class Args(date: LocalDate, messageDeliveryView: List[String])
  object Args {
    def parse(scioArgs: scio.Args): Args = Args(
      date = LocalDate.parse(scioArgs("date"), DateTimeFormatter.ISO_LOCAL_DATE),
      scioArgs.list("messageDeliveryView")
    )
  }

  object Counters {
    val Input: Counter = metricRegistry.counter("input", "count")
    val Campaign: Counter = metricRegistry.counter("campaign", "count")
    val Sent: Counter = metricRegistry.counter("sent", "count")
    val Future: Counter = metricRegistry.counter("future_history", "count")
    val Output: Counter = metricRegistry.counter("output", "count")
  }

  def main(cmdArgs: Array[String]): Unit = {
    val (sc, scioArgs) = ContextAndArgs(cmdArgs)
    val args = Args.parse(scioArgs)

    val messageDeliveryView: SCollection[DeliveryHistory] = sc
      .avroFiles[DeliveryHistory.Avro](args.messageDeliveryView)
      .withName("MapFromAvro")
      .map(DeliveryHistory.fromAvro)

    pipeline(args.date, Campaigns.ReactivationMessageDeliveryCampaigns)(messageDeliveryView)

    close(sc)
  }

  def pipeline(
    date: LocalDate,
    campaigns: Set[DeliveryCampaign]
  )(events: SCollection[DeliveryHistory]): SCollection[MessageHistory] = {
    events
      .transform("FilterOnlyConfiguredCampaigns")(filterCampaignsPipeline(campaigns))
      .transform("FilterOnlySentMessages")(filterSentPipeline)
      .transform("ConvertToMessageHistory")(toHistoryPipeline(date))
  }

  private def filterCampaignsPipeline(
    campaigns: Set[DeliveryCampaign]
  )(events: SCollection[DeliveryHistory]): SCollection[DeliveryHistory] =
    events
      .accumulateCount(Counters.Input)
      .filter(Campaigns.hasValidDeliveryCampaign(campaigns))
      .accumulateCount(Counters.Campaign)

  private def maybePublished(history: DeliveryHistory): Option[DeliveryHistory] = {
    for {
      published <- history.events.find(EventType.Published == _.eventType)
    } yield history.copy(events = List(published))
  }

  private def filterSentPipeline(
    events: SCollection[DeliveryHistory]
  ): SCollection[DeliveryHistory] = {
    events
      .flatMap(maybePublished)
      .accumulateCount(Counters.Sent)
  }

  /**
   * Converts a normalized delivery event to message history.
   * Increments Counters.Invalid when either campaignId or userId is invalid.
   */
  private[eligibility] def toHistory(
    date: LocalDate
  )(history: DeliveryHistory): MessageHistory = {
    val daysSinceSent = history.events.map(ev => calculateDaysSince(date, ev.time))

    MessageHistory(
      userId = history.userId,
      messages =
        daysSinceSent.map(d => MessageHistory.Message(history.channel, history.campaignId, d))
    )
  }

  /**
   * Calculate the number of days since this message was sent.
   * This assumes that the timestamp of the event is less or equal than the partition date
   * since the partition date is the last partition included in the history.
   * This may not be true if a client/source reports a future timestamp in an event,
   * in that case we fall back to zero and increments the Counters.Future.
   */
  def calculateDaysSince(now: LocalDate, timestamp: Instant): NonNegativeInt = {
    val timestampDay = timestamp.atOffset(ZoneOffset.UTC).toLocalDate
    val epochDays = now.toEpochDay - timestampDay.toEpochDay

    if (epochDays < 0) {
      Counters.Future.inc()
    }

    Some(epochDays)
      .filter(_ >= 0)
      .map(_.toInt)
      .flatMap(NonNegativeInt.safeFromInt)
      .getOrElse(NonNegativeInt.Zero)
  }

  private def toHistoryPipeline(
    date: LocalDate
  )(events: SCollection[DeliveryHistory]): SCollection[MessageHistory] = {
    events
      .map(toHistory(date))
      .keyBy(_.userId)
      .sumByKey
      .values
      .accumulateCount(Counters.Output)
  }
}
