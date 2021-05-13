package com.spotify.ladron.candidate

import java.time.Instant

import com.spotify.bcd.schemas.scala.UserId
import com.spotify.coredata.schemas.scala.EndContentFactXTDaily
import com.spotify.ladron.Coders._
import com.spotify.ladron.util.{MetricRegistry, ScioJobBase}
import com.spotify.scio
import com.spotify.scio.avro._
import com.spotify.scio.values.SCollection
import com.spotify.scio.{ContextAndArgs, ScioContext}

/**
 * Aggregate of the user streams. Allows to determine when the user
 * was last active on given platform. The history starts with the "genesis" block,
 * only users which had activity since then are included.
 *
 * Depends on the yesterdays partition of itself.
 */
object UserPlatformActivitiesJob extends ScioJobBase {
  override val metricRegistry: MetricRegistry =
    MetricRegistry.createMetricRegistry("user_platform_activities")

  final case class Args(
    endContentFact: String,
    yesterdayActivities: Option[String],
    output: String
  )

  object Args {
    def parse(scioArgs: scio.Args): Args = Args(
      endContentFact = scioArgs("endContentFact"),
      yesterdayActivities = scioArgs.optional("yesterdayActivities"),
      output = scioArgs("output")
    )
  }

  def main(cmdArgs: Array[String]): Unit = {
    implicit val (sc, scioArgs) = ContextAndArgs(cmdArgs)
    val args = Args.parse(scioArgs)

    val todayActivities: SCollection[UserPlatformActivity] =
      sc.withName("Read EndContentFact")
        .avroFile[EndContentFactXTDaily.Avro](args.endContentFact)
        .withName("Project")
        .flatMap(userPlatformActivityFromEndContentFact)

    val yesterdayActivities: SCollection[UserPlatformActivity] =
      readYesterdayActivities(args.yesterdayActivities)

    pipeline(todayActivities, yesterdayActivities)
      .withName("To Avro")
      .map(UserPlatformActivity.toAvro)
      .saveAsAvroFile(args.output)

    close(sc)
  }

  private[candidate] def pipeline(
    todayActivities: SCollection[UserPlatformActivity],
    yesterdayActivities: SCollection[UserPlatformActivity]
  ): SCollection[UserPlatformActivity] = {
    implicit val userPlatformActivitiesOrdering: Ordering[UserPlatformActivity] =
      Ordering.by(_.latestActivityAt.toEpochMilli)

    todayActivities
      .union(yesterdayActivities)
      .keyBy(upa => upa.userId -> upa.hardwareType)
      .maxByKey
      .values
  }

  /**
   * @return nothing if no path is present, used to seed the job
   */
  private[candidate] def readYesterdayActivities(
    path: Option[String]
  )(
    implicit sc: ScioContext
  ): SCollection[UserPlatformActivity] = {
    path match {
      case Some(path) =>
        sc.withName("Read Yesterday Partition")
          .avroFile[UserPlatformActivity.Avro](path)
          .withName("From Avro")
          .map(UserPlatformActivity.fromAvro)

      case None =>
        sc.empty[UserPlatformActivity]
    }
  }

  private[candidate] def userPlatformActivityFromEndContentFact(
    endContentFactXTDaily: EndContentFactXTDaily.Avro
  ): Option[UserPlatformActivity] = {
    for {
      epochMillis <- Option(endContentFactXTDaily.getTime.getUTCTimeISO)
    } yield UserPlatformActivity(
      userId = UserId.unsafeFromBytes(endContentFactXTDaily.getUserId.array()),
      hardwareType = Option(endContentFactXTDaily.getPlatform.getType).map(HardwareType.fromAvro),
      latestActivityAt = Instant.ofEpochMilli(epochMillis)
    )
  }
}
