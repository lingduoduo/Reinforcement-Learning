package com.spotify.ladron.metrics.summary

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import scala.reflect.ClassTag

import com.spotify.bcd.schemas.scala.NonEmptyString
import com.spotify.ladron.Coders._
import com.spotify.ladron.assignment.UserAssignment
import com.spotify.ladron.candidate.UserSelectionInfo
import com.spotify.ladron.common.MessagingModule
import com.spotify.ladron.delivery.Message
import com.spotify.ladron.eligibility.MatchedUser
import com.spotify.ladron.metrics.UserDayOperationalSummary
import com.spotify.ladron.util.{MetricRegistry, ScioJobBase}
import com.spotify.scio
import com.spotify.scio.{ContextAndArgs, ScioContext}
import com.spotify.scio.avro._
import com.spotify.scio.util.MultiJoin
import com.spotify.scio.values.SCollection
import org.apache.beam.sdk.metrics.Counter

import com.spotify.scio.coders.Coder

/***
 * The purpose of this job is to gather summary facts about each user that goes through Ladron
 * each day. This data will be output in GCS and BQ and will form a view into what Ladron
 * picked for that user and how, for use in analysis, offline evaluation, and dashboards.
 */
object UserDayOperationalSummaryJob extends ScioJobBase {
  override val metricRegistry: MetricRegistry =
    MetricRegistry.createMetricRegistry("user_day_operational_summary")

  metricRegistry.register(UserDayOperationalSummaryJob.metricRegistry)

  def pipeline(
    module: MessagingModule,
    matchedUsers: SCollection[MatchedUser],
    userAssignments: SCollection[UserAssignment],
    userSelectionInfo: SCollection[UserSelectionInfo],
    chosenMessages: SCollection[Message]
  ): SCollection[UserDayOperationalSummary] = {

    MultiJoin
      .apply(
        userSelectionInfo.keyBy(_.userId),
        userAssignments.keyBy(_.userId),
        matchedUsers.keyBy(_.userId),
        chosenMessages.keyBy(_.userId)
      )
      .map {
        case (userId, (selectionInfo, assignment, matches, picked)) =>
          UserDayOperationalSummary(userId, selectionInfo, module, assignment, matches, picked)
      }
      .filter {
        case UserDayOperationalSummary(_, _, _, _, matches, picked) =>
          filterValidMessageChoice(matches, picked.campaignId)
      }
  }

  implicit class OptionalAvroContext(sc: ScioContext) {

    /**
     * Shortcut for reading optional avro dataset of type T returning empty if not present.
     *
     * T must be an Avro class (extend SpecificRecordBase), .avroFile require a ClassTag
     * instance for deserialization and scio require a Coder instance for de-/serialization.
     */
    def optionalAvro[T <: org.apache.avro.specific.SpecificRecordBase: ClassTag: Coder](
      path: Option[String],
      name: String
    ): SCollection[T] =
      path
        .map(sc.withName(s"read $name").avroFile[T](_))
        .getOrElse(sc.withName(s"empty $name").empty[T])
  }

  //scalastyle:off method.length
  def main(cmdArgs: Array[String]): Unit = {
    val (sc, scioArgs) = ContextAndArgs(cmdArgs)
    val args = Args.parse(scioArgs)

    val userSelectionInfo = sc
      .withName("Read UserSelectionInfo")
      .avroFile[UserSelectionInfo.Avro](args.userSelectionInfo)
      .transform("fromAvro UserSelectionInfo")(_.map(UserSelectionInfo.fromAvro))

    /*
     * Gather all the neccessary data from avro files in various places, for reactivation,
     * engagement, and activation modules
     */
    val engagementMatchedUsers = sc
      .optionalAvro[MatchedUser.Avro](args.engagementMatchedUsers, "EngagementMatchedCampaigns")
      .transform("fromAvro EngagementMatchedCampaigns")(_.map(MatchedUser.fromAvro))

    val reactivationMatchedUsers = sc
      .optionalAvro[MatchedUser.Avro](args.reactivationMatchedUsers, "ReactivationMatchedCampaigns")
      .transform("fromAvro ReactivationMatchedCampaigns")(_.map(MatchedUser.fromAvro))

    val reactivationUserAssignments = sc
      .optionalAvro[UserAssignment.Avro](args.reactivationUserAssignment, "ReactivationAssignment")
      .transform("fromAvro ReactivationAssignment")(_.map(UserAssignment.fromAvro))

    val engagementUserAssignments = sc
      .optionalAvro[UserAssignment.Avro](args.engagementUserAssignment, "EngagementAssignment")
      .transform("fromAvro EngagementAssignment")(_.map(UserAssignment.fromAvro))

    val reactivationChosenMessages = sc
      .optionalAvro[Message.Avro](args.reactivationChosenMessages, "ReactivationChosenMessages")
      .transform("fromAvro ReactivationChosenMessages")(_.map(Message.fromAvro))

    val engagementChosenMessages = sc
      .optionalAvro[Message.Avro](args.engagementChosenMessages, "EngagementChosenMessages")
      .transform("fromAvro EngagementChosenMessages")(_.map(Message.fromAvro))

    /**
     * We want to combine all these events on userId, to figure out for each user, which
     * messages were possibilities (after filtering message history), and which was chosen,
     * along with the policy used for the choosing
     */
    val outputReactivation = pipeline(
      MessagingModule.Reactivation,
      reactivationMatchedUsers,
      reactivationUserAssignments,
      userSelectionInfo,
      reactivationChosenMessages
    )

    val outputEngagement = pipeline(
      MessagingModule.Engagement,
      engagementMatchedUsers,
      engagementUserAssignments,
      userSelectionInfo,
      engagementChosenMessages
    )

    val output = outputEngagement ++ outputReactivation

    output
      .transform("toAvro UserDayOperationalSummary")(_.map(UserDayOperationalSummary.toAvro))
      .withName("Write UserDayOperationalSummary")
      .saveAsAvroFile(args.output)
    close(sc)
  }

  /** *
   * Returns true where the picked message is one of the matched options
   *
   * @param matches
   * @param pickedCampaignId
   * @return
   */
  def filterValidMessageChoice(matches: MatchedUser, pickedCampaignId: NonEmptyString): Boolean =
    matches.matched.map(_.campaignId).contains(pickedCampaignId)

  case class Args(
    engagementUserAssignment: Option[String],
    reactivationUserAssignment: Option[String],
    engagementMatchedUsers: Option[String],
    reactivationMatchedUsers: Option[String],
    userSelectionInfo: String,
    engagementChosenMessages: Option[String],
    reactivationChosenMessages: Option[String],
    date: LocalDate,
    output: String
  )

  object Counters {
    val InputCount: Counter = metricRegistry.counter("input", "count")
    val InputRelevantCount: Counter = metricRegistry.counter("input_relevant", "count")
  }

  object Args {
    def parse(scioArgs: scio.Args): Args = (
      Args(
        engagementUserAssignment = scioArgs.optional("engagementUserAssignment"),
        reactivationUserAssignment = scioArgs.optional("reactivationUserAssignment"),
        engagementMatchedUsers = scioArgs.optional("engagementMatchedUsers"),
        reactivationMatchedUsers = scioArgs.optional("reactivationMatchedUsers"),
        userSelectionInfo = scioArgs("userSelectionInfo"),
        engagementChosenMessages = scioArgs.optional("engagementChosenMessages"),
        reactivationChosenMessages = scioArgs.optional("reactivationChosenMessages"),
        date = LocalDate.parse(scioArgs("date"), DateTimeFormatter.ISO_LOCAL_DATE),
        output = scioArgs("output")
      )
    )
  }
}
