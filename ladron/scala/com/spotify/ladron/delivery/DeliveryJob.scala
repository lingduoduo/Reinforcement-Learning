package com.spotify.ladron.delivery

import java.time.format.DateTimeFormatter
import scala.collection.JavaConverters._
import org.apache.beam.sdk.metrics.Counter

import com.spotify.bcd.schemas.scala.NonEmptyString
import com.spotify.ladron.Coders._
import com.spotify.emailmessenger.v1.{Message => EmailMessage}
import com.spotify.ladron.candidate.{ResolvedValues, SimpleValue, TemplateValue}
import com.spotify.ladron.common.MessageChannel
import com.spotify.ladron.syntax.metrics.MetricsSyntax
import com.spotify.ladron.util.{MetricRegistry, ScioJobBase}
import com.spotify.pushmessenger.v1.{Message => PushMessage}
import com.spotify.scio
import com.spotify.scio.ContextAndArgs
import com.spotify.scio.avro._
import com.spotify.scio.io.PubsubIO
import com.spotify.scio.values.SCollection

/**
 * Job that delivers email and push messages to the delivery layer via a provided pubsub topic.
 *
 * As input, the job takes messages from Avro. However, each message is an instance of
 * [[Message.Avro]], which means it can either be an email, a push message or an in app message.
 */
object DeliveryJob extends ScioJobBase {
  val metricRegistry: MetricRegistry = MetricRegistry.createMetricRegistry("delivery")
  val EmailContentTypeAttribute: (String, String) =
    "Content-Type" -> "application/protobuf; proto=spotify.emailmessenger.v1"
  val PushContentTypeAttribute: (String, String) =
    "Content-Type" -> "application/protobuf; proto=spotify.pushmessenger.v1"

  final case class Args(emailTopic: String, pushTopic: String, messages: String)
  object Args {
    def parse(scioArgs: scio.Args): Args = Args(
      emailTopic = scioArgs("email-topic"),
      pushTopic = scioArgs("push-topic"),
      // when invoked as dependency from upstream job parameter will
      // be "messageOutput" else "messages".
      messages = scioArgs.optional("messageOutput").getOrElse(scioArgs("messages"))
    )
  }

  def main(cmdArgs: Array[String]): Unit = {
    val (sc, scioArgs) = ContextAndArgs(cmdArgs)
    val args = Args.parse(scioArgs)

    val input = sc
      .avroFile[Message.Avro](args.messages)
      .map(Message.fromAvro)

    val (emails, pushes) = pipeline(input)

    emails
      .map(m => m -> Map(EmailContentTypeAttribute))
      .write(
        PubsubIO.withAttributes[EmailMessage.SingleMessage](args.emailTopic)
      )(PubsubIO.WriteParam(None, None))

    pushes
      .map(m => m -> Map(PushContentTypeAttribute))
      .write(
        PubsubIO.withAttributes[PushMessage.SingleMessage](args.pushTopic)
      )(PubsubIO.WriteParam(None, None))

    close(sc)
  }

  object Counters {
    val InputCount: Counter = metricRegistry.counter("input", "count")
    val EmailMessageCount: Counter = metricRegistry.counter("input", "email-message-count")
    val EmailDryRunMessageCount: Counter =
      metricRegistry.counter("input", "email-dry-run-message-count")
    val PushMessageCount: Counter = metricRegistry.counter("input", "push-message-count")
    val PushDryRunMessageCount: Counter =
      metricRegistry.counter("input", "push-dry-run-message-count")
  }

  private[delivery] val PushMessageTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
  private[delivery] val MessageTimeFormatter = DateTimeFormatter.ISO_LOCAL_TIME
  private[delivery] val MessageDateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

  /* Send messages to delivery layer service via pub-sub */
  def pipeline(
    messages: SCollection[Message]
  ): (SCollection[EmailMessage.SingleMessage], SCollection[PushMessage.SingleMessage]) = {
    val (emails, pushes) = messages
      .accumulateCount(Counters.InputCount)
      .partition(_.channel == MessageChannel.Email)

    val protoEmails = emails
      .accumulateIf(_.dryRun)(Counters.EmailDryRunMessageCount)
      .accumulateCount(Counters.EmailMessageCount)
      .map(createProtoEmailMessage)

    val protoPushes = pushes
      .accumulateIf(_.dryRun)(Counters.PushDryRunMessageCount)
      .accumulateCount(Counters.PushMessageCount)
      .map(createProtoPushMessage)

    (protoEmails, protoPushes)
  }

  private[delivery] def createProtoEmailMessage(message: Message): EmailMessage.SingleMessage = {
    // Template values are not yet implemented for email
    val (templateParams, _) = parametersToProto(message.templateValues)

    val builder = EmailMessage.SingleMessage
      .newBuilder()
      .setRecordId(message.recordId.value)
      .setCampaignId(message.campaignId.value)
      .setUserId(message.userId.hex)
      .setDryRun(message.dryRun)
      .setTemplateId(message.templateId.value)
      .putAllTemplateParameters(templateParams.asJava)

    message.sendAt match {
      case DeliverySendAtDateTime(dateTime) =>
        builder.setSendAt(
          EmailMessage.LocalDateTime
            .newBuilder()
            .setValue(MessageDateTimeFormatter.format(dateTime))
        )
      case DeliverySendAtTime(time) =>
        builder.setSendAtTime(
          EmailMessage.LocalTime
            .newBuilder()
            .setValue(MessageTimeFormatter.format(time))
        )
      case DeliveryImmediately =>
    }

    builder.build()
  }

  private[delivery] def createProtoPushMessage(message: Message): PushMessage.SingleMessage = {
    val (templateParams, templateValues) = parametersToProto(message.templateValues)

    val builder = PushMessage.SingleMessage
      .newBuilder()
      .setRecordId(message.recordId.value)
      .setCampaignId(message.campaignId.value)
      .setUserId(message.userId.hex)
      .setDryRun(message.dryRun)
      .setTemplateId(message.templateId.value)
      .putAllTemplateParameters(templateParams.asJava)
      .putAllTemplateValues(templateValues.asJava)

    message.sendAt match {
      case DeliverySendAtDateTime(dateTime) =>
        builder.setSendAt(
          PushMessage.LocalDateTime
            .newBuilder()
            .setValue(MessageDateTimeFormatter.format(dateTime))
        )
      case DeliverySendAtTime(time) => builder.setSendAtTime(PushMessageTimeFormatter.format(time))
      case DeliveryImmediately      =>
    }

    builder.build()
  }

  private def parametersToProto(
    templateValues: Map[NonEmptyString, TemplateValue]
  ): (Map[String, String], Map[String, PushMessage.TemplateValue]) = {
    val params = templateValues.collect {
      case (name: NonEmptyString, value: SimpleValue) =>
        name.toString -> value.value.toString
    }
    val values = templateValues.collect {
      case (name: NonEmptyString, value: ResolvedValues) => {
        val rv = PushMessage.ResolvedValues
          .newBuilder()
          .setResolver(value.resolver.name)
          .setUri(value.uri.toString)
        val tv = PushMessage.TemplateValue.newBuilder().setResolvedValues(rv).build
        name.toString -> tv
      }
    }
    (params, values)
  }
}
