package com.spotify.ladron.candidate.record

import com.spotify.bcd.schemas.scala.UserId
import com.spotify.optouts.UserOptOuts

import scala.collection.JavaConverters._

/**
 * Notification settings for the `notify-news-and-offers` opt-out type
 *
 * @param wantEmail if user wants to be emailed, no answer is agreement
 * @param wantPush if user wants to be pushed, no answer is agreement
 */
final case class UserNotificationOptOut(
  userId: UserId,
  wantEmail: Boolean,
  wantPush: Boolean
)

object UserNotificationOptOut {

  /**
   * @return defaults to allow if the setting for specific platform is not found
   */
  def fromUserOptOuts(userOptOuts: UserOptOuts): Option[UserNotificationOptOut] = {
    for {
      userId <- UserId.fromString(userOptOuts.getUserId)
      notficationOptOuts = userOptOuts.getOptOuts.asScala
        .filter(_.getType == "notify-news-and-offers")
      email = notficationOptOuts
        .find(_.getChannel == "email")
        .forall(_.getValue.booleanValue())
      push = notficationOptOuts
        .find(_.getChannel == "push")
        .forall(_.getValue.booleanValue())
    } yield UserNotificationOptOut(userId, email, push)
  }
}
