package com.spotify.ladron.candidate

import java.time.LocalDate

import com.spotify.bcd.schemas.scala.{NonEmptyString, UserId, UserSnapshot4, UserStatus}
import com.spotify.ladron.Coders._
import com.spotify.ladron.NonNegativeInt
import com.spotify.ladron.candidate.record._
import com.spotify.ladron.syntax.metrics._
import com.spotify.ladron.util.DateUtil.calculateDaysFromDateRange
import com.spotify.ladron.util.{MetricRegistry, ScioJobBase}
import com.spotify.optouts.UserOptOuts
import com.spotify.scio
import com.spotify.scio.ContextAndArgs
import com.spotify.scio.avro._
import com.spotify.scio.util.MultiJoin
import com.spotify.scio.values.SCollection
import com.spotify.userdata.schema.UserdataSourceBroad
import org.apache.beam.sdk.metrics.Counter

/**
 * Users eligible for the push/email messaging, allows filtering on OptOuts, locales, and activities
 *
 * Users are only included if they had activities in the given period
 * (since the genesis of UserPlatformActivities).
 * Missing opt-out settings are considered as opt-in.
 */
object UserSelectionInfoJob extends ScioJobBase {
  override val metricRegistry: MetricRegistry =
    MetricRegistry.createMetricRegistry("user_info_export")

  object Counters {
    val MissingJoinData: Counter = metricRegistry.counter("missing_join_data", "count")
    val MissingUserLocale: Counter = metricRegistry.counter("missing_user_locale", "count")
    val MissingNotificationOptOut: Counter =
      metricRegistry.counter("missing_notification_optout", "count")
  }

  final case class Args(
    userSnapshot: String,
    userPlatformActivities: String,
    userDataSource: String,
    notificationOptouts: String,
    date: LocalDate,
    output: String
  )
  object Args {
    def parse(scioArgs: scio.Args): Args = Args(
      userSnapshot = scioArgs("userSnapshot"),
      userPlatformActivities = scioArgs("userPlatformActivities"),
      userDataSource = scioArgs("userDataSource"),
      notificationOptouts = scioArgs("notificationOptouts"),
      date = LocalDate.parse(scioArgs("date")),
      output = scioArgs("output")
    )
  }

  def main(cmdArgs: Array[String]): Unit = {
    val (sc, scioArgs) = ContextAndArgs(cmdArgs)
    val args = Args.parse(scioArgs)

    val userInfos: SCollection[UserInfo] =
      sc.withName("Read UserSnapshot4")
        .avroFile[UserSnapshot4.Avro](args.userSnapshot)
        .transform("Normalize UserSnapshot4")(parseUserSnapshot)

    val userActivities: SCollection[UserPlatformActivity] =
      sc.withName("Read UserPlatformActivities")
        .avroFile[UserPlatformActivity.Avro](args.userPlatformActivities)
        .withName("Normalize UserPlatformActivities")
        .map(UserPlatformActivity.fromAvro)

    val userLocales: SCollection[UserLocale] =
      sc.withName("Read UserDataSourceBroad")
        .avroFile[UserdataSourceBroad](args.userDataSource)
        .transform("Normalize UserDataSourceBroad")(convertUserDataSource)

    val notificationOptouts: SCollection[UserNotificationOptOut] =
      sc.withName("Read NotificationOptOuts")
        .avroFile[UserOptOuts](args.notificationOptouts)
        .transform("Normalize NotificationOptOuts")(convertNotificationOptOuts)

    pipeline(args.date, userActivities, userInfos, userLocales, notificationOptouts)
      .withName("To Avro")
      .map(UserSelectionInfo.toAvro)
      .saveAsAvroFile(args.output)

    close(sc)
  }

  private[candidate] def pipeline(
    date: LocalDate,
    userActivities: SCollection[UserPlatformActivity],
    userInfos: SCollection[UserInfo],
    userLocales: SCollection[UserLocale],
    notificationOptouts: SCollection[UserNotificationOptOut]
  ): SCollection[UserSelectionInfo] = {
    val latestActivities = userActivities.transform("Latest activity dates")(getLatestActivities)

    MultiJoin
      .left(
        latestActivities,
        userInfos.keyBy(_.userId),
        userLocales.keyBy(_.userId),
        notificationOptouts.keyBy(_.userId)
      )
      .values
      .withName("To UserSelectionInfo")
      .flatMap {
        case (activities, Some(info), Some(locales), optOuts) =>
          createUserSelectionInfo(date, activities, info, locales, optOuts)
        case _ =>
          Counters.MissingJoinData.inc()
          None
      }
  }

  private def convertUserDataSource(
    userdataSourceBroad: SCollection[UserdataSourceBroad]
  ): SCollection[UserLocale] = {
    userdataSourceBroad
      .map(UserLocale.fromUserdataSourceBroad)
      .accumulateIf(_.isEmpty)(Counters.MissingUserLocale)
      .flatten
  }

  private def convertNotificationOptOuts(
    notificationOptOuts: SCollection[UserOptOuts]
  ): SCollection[UserNotificationOptOut] = {
    notificationOptOuts
      .map(UserNotificationOptOut.fromUserOptOuts)
      .accumulateIf(_.isEmpty)(Counters.MissingNotificationOptOut)
      .flatten
  }

  private[candidate] def getLatestActivities(
    userPlatformActivity: SCollection[UserPlatformActivity]
  ): SCollection[(UserId, UserActivity)] = {
    userPlatformActivity
      .map(UserActivity.fromUserPlatformActivity)
      .keyBy(_.userId)
      .sumByKey
  }

  private[candidate] def parseUserSnapshot(
    userSnapshots: SCollection[UserSnapshot4.Avro]
  ): SCollection[UserInfo] = {
    userSnapshots
      .filter { us =>
        val status = UserStatus.fromAvro(us.getStatus)
        status != UserStatus.Deleted && status != UserStatus.Disabled
      }
      .map(UserInfo.fromUserSnapshot)
  }

  private[candidate] def createUserSelectionInfo(
    date: LocalDate,
    userActivity: UserActivity,
    userInfo: UserInfo,
    userLocale: UserLocale,
    notificationOptOut: Option[UserNotificationOptOut]
  ): Option[UserSelectionInfo] = {
    def calculateDays(endDate: LocalDate): Option[NonNegativeInt] =
      calculateDaysFromDateRange(endDate, date).toOption
        .map(_.toInt)
        .flatMap(NonNegativeInt.safeFromInt)

    for {
      reportingProduct <- NonEmptyString.parse(userInfo.reportingProduct.value)
      language <- NonEmptyString.parse(userLocale.languageCode)
      daysSinceReg <- calculateDays(userInfo.registrationDate)
      daysSinceActive <- calculateDays(userActivity.lastActive)
      daysSinceActiveMobile = userActivity.lastActiveMobile.flatMap(calculateDays)
    } yield UserSelectionInfo(
      userId = userInfo.userId,
      daysSinceRegistration = daysSinceReg,
      daysSinceActive = daysSinceActive,
      registrationCountry = userInfo.registrationCountry.value,
      reportingProduct = reportingProduct,
      locale = language,
      daysSinceActiveOnMobile = daysSinceActiveMobile,
      okToEmail = notificationOptOut.forall(_.wantEmail),
      okToPush = notificationOptOut.forall(_.wantPush)
    )
  }
}
