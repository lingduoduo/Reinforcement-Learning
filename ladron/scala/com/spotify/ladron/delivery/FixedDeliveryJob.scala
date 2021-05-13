package com.spotify.ladron.delivery

import java.time.LocalTime

import scala.util.Random
import com.spotify.bcd.schemas.scala.{NonEmptyString, UserId}
import com.spotify.ladron.Coders._
import com.spotify.emailmessenger.v1.{Message => EmailMessage}
import com.spotify.ladron.candidate.CandidateMessage
import com.spotify.ladron.common.MessageChannel
import com.spotify.ladron.config.Campaigns
import com.spotify.ladron.config.Campaigns.LadronCampaign
import com.spotify.ladron.delivery.DeliveryJob._
import com.spotify.ladron.util.{MetricRegistry, ScioJobBase}
import com.spotify.pushmessenger.v1.{Message => PushMessage}
import com.spotify.scio
import com.spotify.scio.ContextAndArgs
import com.spotify.scio.io.PubsubIO

object FixedDeliveryJob extends ScioJobBase {
  val metricRegistry: MetricRegistry = MetricRegistry.createMetricRegistry("fixed_delivery")
  metricRegistry.register(DeliveryJob.metricRegistry)

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

  private val Partition = NonEmptyString.fromAvro("2019-01-31")

  final case class Args(emailTopic: String, pushTopic: String, userIds: List[String])
  object Args {
    def parse(scioArgs: scio.Args): Args = Args(
      emailTopic = scioArgs("email-topic"),
      pushTopic = scioArgs("push-topic"),
      userIds = scioArgs.list("user-ids")
    )
  }

  def main(cmdArgs: Array[String]): Unit = {
    val (sc, scioArgs) = ContextAndArgs(cmdArgs)
    val args = Args.parse(scioArgs)

    val emailMessages = args.userIds
      .map(UserId.unsafeFromString)
      .map(createMessageForUser(EmailTemplateIds, EmailCampaign))

    val pushMessages = args.userIds
      .map(UserId.unsafeFromString)
      .map(createMessageForUser(PushTemplateIds, PushCampaign))

    val (emails, pushes) = pipeline(sc.parallelize(pushMessages ++ emailMessages))

    emails
      .map { m =>
        m -> Map(EmailContentTypeAttribute)
      }
      .write(
        PubsubIO.withAttributes[EmailMessage.SingleMessage](args.emailTopic)
      )(PubsubIO.WriteParam(None, None))

    pushes
      .map { m =>
        m -> Map(PushContentTypeAttribute)
      }
      .write(
        PubsubIO.withAttributes[PushMessage.SingleMessage](args.pushTopic)
      )(PubsubIO.WriteParam(None, None))

    close(sc)
  }

  private def createMessageForUser(candidates: Seq[NonEmptyString], campaign: LadronCampaign)(
    user: UserId
  ): Message = {
    val templateId = Random.shuffle(candidates).head
    val (channel, campaignId) = campaign
    Message(
      recordId = CandidateMessage.createRecordId(user, channel, campaignId, templateId, Partition),
      campaignId = campaignId,
      templateId = Random.shuffle(candidates).head,
      userId = user,
      sendAt = DeliverySendAtTime(LocalTime.of(10, 0)),
      dryRun = false,
      channel = channel,
      specific = EmailSpecific(),
      templateValues = Map()
    )
  }
}
