package com.spotify.ladron.delivery

import java.time.LocalTime

import com.spotify.bcd.schemas.scala.UserId
import com.spotify.ladron.Coders._
import com.spotify.ladron.candidate._
import com.spotify.ladron.common.MessageChannel.{Email, InApp, Push}
import com.spotify.ladron.common.{AssignmentPolicy, MessageChannel, PolicyType}
import com.spotify.ladron.model.AssignedSelectedMessage
import com.spotify.ladron.util.{MetricRegistry, ScioJobBase}
import com.spotify.scio.values.SCollection
import org.apache.beam.sdk.metrics.Counter

object MessageBuilder extends ScioJobBase {
  override val metricRegistry: MetricRegistry =
    MetricRegistry.createMetricRegistry("delivery_metrics")

  // scalastyle:off magic.number
  val SendTime: LocalTime = LocalTime.of(17, 0)
  // scalastyle:on magic.number

  object Counters {
    val CreatedCount: Map[MessageChannel, Boolean => Counter] = MessageChannel.values.map {
      channel =>
        (channel, createdCounter(channel))
    }.toMap
    val TotalCreated: Counter = metricRegistry.counter("created", "total")
  }

  /**
   * For ladron candidate message let candidate decide dry-run except for hold-out group.
   */
  private def isDryRun(policy: AssignmentPolicy, candidate: CandidateMessage): Boolean =
    if (policy.`type` == PolicyType.Holdout) {
      true
    } else {
      candidate.dryRun
    }

  def buildMessagesFromCandidates(
    selected: SCollection[AssignedSelectedMessage]
  ): SCollection[Message] = {
    selected.map { selected =>
      Counters.CreatedCount(selected.selectedMessage.channel)(selected.selectedMessage.dryRun).inc()
      Counters.TotalCreated.inc()
      message(selected.userId, selected.selectedMessage, selected.group, SendTime)
    }
  }

  /**
   * Creates a message wrapper for a message that should be sent to delivery layer
   * service via pub-sub.
   *
   * Note that there are some differences between the message format used internally compared to
   * the format used by the delivery layer.
   * The following mapping is applied:
   *
   *    SendAtDateTime(dateTime) -> SendAtDateTime(dateTime
   *    SendAtTime(time) -> SendAtTime(time)
   *    SendAtAny -> SendAtTime(defaultTime e.g 17:00)
   *    SendImmediately -> Neither SendAtDateTime or SendAtTime will be set.
   */
  private def message(
    userId: UserId,
    message: CandidateMessage,
    policy: AssignmentPolicy,
    defaultSendAt: LocalTime
  ): Message = {
    Message(
      recordId = message.recordId,
      userId = userId,
      campaignId = message.campaignId,
      templateId = message.templateId,
      sendAt = schedule(message.sendAt, defaultSendAt),
      dryRun = isDryRun(policy, message),
      channel = message.channel,
      specific = message.channel match {
        case Push  => PushSpecific(Set.empty)
        case Email => EmailSpecific()
        case InApp => InAppSpecific()
      },
      templateValues = message.templateValues
    )
  }

  def schedule(at: SendAt, defaultSendAt: LocalTime): DeliverySendAt = at match {
    case SendAtDateTime(dateTime) => DeliverySendAtDateTime(dateTime)
    case SendAtTime(time)         => DeliverySendAtTime(time)
    case SendAtAny                => DeliverySendAtTime(defaultSendAt)
    case SendImmediately          => DeliveryImmediately
    case DropImmediately =>
      throw new RuntimeException("DropImmediately should be filtered before building a message")
  }

  private def createdCounter(channel: MessageChannel): Boolean => Counter = {
    val liveCounter: Counter = metricRegistry.counter("created", channel.name)
    val dryRunCounter: Counter = metricRegistry.counter("created", channel.name, "dry_run")

    dryRun =>
      if (dryRun) {
        dryRunCounter
      } else {
        liveCounter
      }
  }
}
