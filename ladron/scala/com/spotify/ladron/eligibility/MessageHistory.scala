package com.spotify.ladron.eligibility

import com.twitter.algebird.Semigroup

import com.spotify.bcd.schemas.scala.{NonEmptyString, UserId}
import com.spotify.ladron.NonNegativeInt
import com.spotify.message.data.Channel

final case class MessageHistory(
  userId: UserId,
  messages: Seq[MessageHistory.Message]
)

object MessageHistory {

  /**
   * An sent/triggered message summary.
   * @param channel The channel the message was sent on.
   * @param campaignId The campaign id of the message.
   * @param daysSinceSent The number of days from "this" partition this message was sent.
   *                      In the case of invalid (future) timestamps this is zero.
   */
  final case class Message(
    channel: Channel,
    campaignId: NonEmptyString,
    daysSinceSent: NonNegativeInt
  )

  private final val MessageOrdering: Ordering[Message] = Ordering.by { message =>
    (message.daysSinceSent.value, message.campaignId.value)
  }

  implicit val MessageHistorySemigroup: Semigroup[MessageHistory] = Semigroup.from[MessageHistory] {
    (x, y) =>
      MessageHistory(x.userId, (x.messages ++ y.messages).sorted(MessageOrdering))
  }
}
