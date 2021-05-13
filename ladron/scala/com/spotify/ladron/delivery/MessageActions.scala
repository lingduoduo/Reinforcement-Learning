package com.spotify.ladron.delivery

import scala.collection.immutable.HashSet

import com.spotify.message.data.{Channel, EventType, MessageHistory}

object MessageActions {

  /**
   * Determine the system's action in response to this message.
   * If there are only user actions Missed is returned to indicate
   * that the system action is outside the aggregation window.
   */
  // scalastyle:off cyclomatic.complexity
  def systemAction(agg: MessageHistory): SystemMessageAction = {
    val events: Set[EventType] = agg.events.map(_.eventType).toSet
    def has(et: EventType*): Boolean = List(et: _*).exists(events.contains)

    import EventType._

    agg.channel match {
      case _ if has(Capped)                 => SystemMessageAction.Capped
      case _ if has(Sent, Delivered)        => SystemMessageAction.Sent
      case _ if has(Published)              => SystemMessageAction.Published
      case _ if has(OptOut)                 => SystemMessageAction.OptedOut
      case _ if has(Unreachable)            => SystemMessageAction.Unreachable
      case _ if has(Failed, UpstreamFailed) => SystemMessageAction.Failed
      case _ if has(Invalid)                => SystemMessageAction.Filtered
      // Fallback when the user action comes outside the event aggregation window.
      case _ if has(Click, Open, Unsubscribe, SpamReport) => SystemMessageAction.Missed
      case _                                              => SystemMessageAction.Unknown
    }
  }
}
