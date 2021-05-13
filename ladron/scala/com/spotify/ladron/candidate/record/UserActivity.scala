package com.spotify.ladron.candidate.record

import java.time.{Instant, LocalDate}

import com.spotify.bcd.schemas.scala.UserId
import com.spotify.ladron.candidate.HardwareType.Mobile
import com.spotify.ladron.candidate.UserPlatformActivity
import com.twitter.algebird.Semigroup

/**
 * @param lastActive the day user was last active on, any platform
 * @param lastActiveMobile last active on mobile platform
 */
final case class UserActivity(
  userId: UserId,
  lastActive: LocalDate,
  lastActiveMobile: Option[LocalDate]
)

object UserActivity {
  implicit val userActivitySemigroup: Semigroup[UserActivity] = Semigroup.from { (a, b) =>
    implicit val localDateOrdering: Ordering[LocalDate] = Ordering.by(_.toEpochDay)

    val localDateOrderingImpl = implicitly[Ordering[LocalDate]]
    val localDateOptOrderingImpl = implicitly[Ordering[Option[LocalDate]]]

    UserActivity(
      userId = a.userId,
      lastActive = localDateOrderingImpl.max(a.lastActive, b.lastActive),
      lastActiveMobile = localDateOptOrderingImpl.max(a.lastActiveMobile, b.lastActiveMobile)
    )
  }

  def fromUserPlatformActivity(userPlatformActivity: UserPlatformActivity): UserActivity = {
    def instantToLocalDate(instant: Instant): LocalDate =
      instant.atZone(UserPlatformActivity.timezone).toLocalDate

    userPlatformActivity.hardwareType match {
      case Some(Mobile) =>
        UserActivity(
          userId = userPlatformActivity.userId,
          lastActive = instantToLocalDate(userPlatformActivity.latestActivityAt),
          lastActiveMobile = Some(instantToLocalDate(userPlatformActivity.latestActivityAt))
        )
      case _ =>
        UserActivity(
          userId = userPlatformActivity.userId,
          lastActive = instantToLocalDate(userPlatformActivity.latestActivityAt),
          lastActiveMobile = None
        )
    }
  }
}
