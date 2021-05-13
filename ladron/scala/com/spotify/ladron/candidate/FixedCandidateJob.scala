package com.spotify.ladron.candidate

import scala.util.Random

import com.spotify.bcd.schemas.scala.{NonEmptyString, UserId}
import com.spotify.ladron.Coders._
import com.spotify.ladron.common.MessageChannel
import com.spotify.ladron.config.Campaigns
import com.spotify.ladron.config.Campaigns.LadronCampaign
import com.spotify.ladron.util.{MetricRegistry, ScioJobBase}
import com.spotify.scio
import com.spotify.scio.ContextAndArgs
import com.spotify.scio.avro._

/**
 * Create fixed candidates for the white-mouse team.
 */
object FixedCandidateJob extends ScioJobBase {
  val metricRegistry: MetricRegistry = MetricRegistry.createMetricRegistry("fixed_delivery")

  private val PushTemplateIds =
    Seq("165", "166", "167", "168", "170", "171").map(NonEmptyString.fromAvro)
  private val EmailTemplateIds = Seq(
    "2018q4-global-global-alwayson-reactivation-email-1a",
    "2018q4-global-global-alwayson-reactivation-email-1b",
    "2018q4-global-global-alwayson-reactivation-email-1c",
    "2018q4-global-global-alwayson-reactivation-email-1d",
    "2018q4-global-global-alwayson-reactivation-email-2a",
    "2018q4-global-global-alwayson-reactivation-email-2b",
    "2018q4-global-global-alwayson-reactivation-email-2c",
    "2018q4-global-global-alwayson-reactivation-email-3a",
    "2018q4-global-global-alwayson-reactivation-email-3b"
  ).map(NonEmptyString.fromAvro)

  private val EmailCampaign =
    (MessageChannel.Email, Campaigns.ReactivationEmailTestCampaignId)
  private val PushCampaign =
    (MessageChannel.Push, Campaigns.ReactivationPushTestCampaignId)

  final case class Args(userIds: List[String], partition: NonEmptyString, output: String)
  object Args {
    def parse(scioArgs: scio.Args): Args = Args(
      userIds = scioArgs.list("user-ids"),
      partition = NonEmptyString.fromAvro(scioArgs("date")),
      output = scioArgs("output")
    )
  }

  def main(cmdArgs: Array[String]): Unit = {
    val (sc, scioArgs) = ContextAndArgs(cmdArgs)
    val args = Args.parse(scioArgs)

    val emailMessages = args.userIds
      .map(UserId.unsafeFromString)
      .map(createCandidateForUser(args.partition, EmailTemplateIds, EmailCampaign))

    val pushMessages = args.userIds
      .map(UserId.unsafeFromString)
      .map(createCandidateForUser(args.partition, PushTemplateIds, PushCampaign))

    val candidates = sc.parallelize(emailMessages) ++ sc.parallelize(pushMessages)

    candidates
      .map(CandidateMessage.toAvro)
      .saveAsAvroFile(args.output)

    close(sc)
  }

  private def createCandidateForUser(
    partition: NonEmptyString,
    candidates: Seq[NonEmptyString],
    campaign: LadronCampaign
  )(
    user: UserId
  ): CandidateMessage = CandidateMessage.createWithPartition(
    partition = partition,
    userId = user,
    channel = campaign._1,
    campaignId = campaign._2,
    templateId = candidates(Random.nextInt(candidates.length)),
    dryRun = false,
    sendAt = SendAtAny
  )
}
