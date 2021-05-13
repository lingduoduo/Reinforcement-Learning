package com.spotify.ladron.eligibility

import java.time.LocalDate

import org.apache.beam.sdk.metrics.Counter

import com.spotify.bcd.schemas.scala.UserId
import com.spotify.ladron.Coders._
import com.spotify.ladron.NonNegativeInt
import com.spotify.ladron.candidate.CandidateMessage
import com.spotify.ladron.config.Campaigns.DeliveryCampaign
import com.spotify.ladron.model.AssignedCandidateMessages
import com.spotify.ladron.syntax.metrics._
import com.spotify.ladron.util.{MetricRegistry, ScioJobBase}
import com.spotify.scio.values.SCollection
import com.spotify.message.data.{MessageHistory => DeliveryHistory}

/**
 * Filter message that has recently been sent to the user.
 */
object MessageHistoryFilterJob extends ScioJobBase {
  override val metricRegistry: MetricRegistry =
    MetricRegistry.createMetricRegistry("message_history_metrics")

  object Counters {
    val Input: Counter = metricRegistry.counter("input", "count")
    val RecentlySent: Counter = metricRegistry.counter("recently_sent", "count")
    val Output: Counter = metricRegistry.counter("output", "count")
  }

  type ReSendInterval = CandidateMessage => NonNegativeInt

  def pipeline(date: LocalDate, campaigns: Set[DeliveryCampaign], resend: ReSendInterval)(
    candidates: SCollection[AssignedCandidateMessages],
    messageDeliveryView: SCollection[DeliveryHistory]
  ): SCollection[AssignedCandidateMessages] = {
    val historyByUser: SCollection[(UserId, MessageHistory)] = MessageHistoryJob
      .pipeline(date, campaigns)(messageDeliveryView)
      .keyBy(_.userId)

    val candidatesByUser: SCollection[(UserId, AssignedCandidateMessages)] = candidates
      .accumulateCount(Counters.Input)
      .keyBy(_.userId)

    historyByUser
      .rightOuterJoin(candidatesByUser)
      .values
      .flatMap((recentlySentFilter(resend) _).tupled)
      .accumulateCount(Counters.Output)
  }

  private def recentlySentFilter(resend: ReSendInterval)(
    maybeHistory: Option[MessageHistory],
    userMessages: AssignedCandidateMessages
  ): TraversableOnce[AssignedCandidateMessages] = {
    maybeHistory
    // Filter if history is present
      .map { history =>
        userMessages.copy(
          messages = userMessages.messages
            .filter(recentlySentFilterSingle(resend, history))
        )
      }
      // If no history, retain all templates
      .orElse(Some(userMessages))
      // If all templates were filtered, skip this user
      .filter(_.messages.nonEmpty)
  }

  private def recentlySentFilterSingle(
    resend: ReSendInterval,
    history: MessageHistory
  )(message: CandidateMessage): Boolean = {
    val recentlySent = history.messages.toStream
      .filter(m => m.channel == message.channel.asDelivery && m.campaignId == message.campaignId)
      .exists(_.daysSinceSent.value <= resend(message).value)

    if (recentlySent) {
      Counters.RecentlySent.inc()
    }

    !recentlySent
  }
}
