package com.spotify.ladron.selection

import com.google.protobuf.ByteString
import enumeratum.{Enum, EnumEntry}
import org.tensorflow.example.Example
import com.spotify.bcd.schemas.scala.{NonEmptyString, UserId}
import com.spotify.ladron.common.MessageChannel
import com.spotify.ladron.tf.Encoding._
import magnolify.tensorflow.ExampleType

final case class CampaignId(channel: MessageChannel, id: NonEmptyString)
final case class RecordId(id: NonEmptyString)
final case class CandidateContext(
  userId: UserId,
  candidateId: CandidateContext.CandidateId,
  context: Example
)

object CandidateContext {
  case class ExampleContext(
    user_id: Option[ByteString],
    channel: Option[MessageChannel],
    campaign_id: Option[NonEmptyString],
    record_id: Option[NonEmptyString],
    country: Option[NonEmptyString]
  )

  val exampleContextExampleType: ExampleType[ExampleContext] =
    ExampleType[ExampleContext]

  type CandidateId = Either[CampaignId, RecordId]

  sealed abstract class Error(private val descriptionText: String)
      extends EnumEntry
      with Product
      with Serializable {
    def name: String = toString

    def description: String = "Invalid context: " + descriptionText
  }

  object Error extends Enum[Error] {
    val values: scala.collection.immutable.IndexedSeq[Error] = findValues

    case object CampaignIdWithoutChannel extends Error("campaign id without channel id")

    case object BothCampaignAndRecord extends Error("both campaign id and record id")

    case object NeitherCampaignNorRecord extends Error("neither campaign id nor request id")

    case object MissingFeature extends Error("missing feature")
  }

  def fromExample(example: Example): Either[Error, CandidateContext] = {
    val parsed: ExampleContext = exampleContextExampleType.from(example)
    fromExampleContext(example)(parsed)
  }

  private def fromExampleContext(
    example: Example
  )(context: ExampleContext): Either[Error, CandidateContext] =
    for {
      candidateId <- errorOrCandidateId(context)
      userId <- errorOrUserId(context.user_id)
    } yield CandidateContext(userId, candidateId, example)

  private def errorOrCandidateId(
    context: ExampleContext
  ): Either[Error, CandidateId] =
    (context.channel, context.campaign_id, context.record_id) match {
      case (_, Some(_), Some(_)) =>
        Left(Error.BothCampaignAndRecord)
      case (Some(channel), Some(campaignId), _) =>
        Right(Left(CampaignId(channel, campaignId)))
      case (_, Some(_), _) => Left(Error.CampaignIdWithoutChannel)
      case (_, _, Some(x)) => Right(Right(RecordId(x)))
      case (_, _, _) =>
        Left(Error.NeitherCampaignNorRecord)
    }

  private def errorOrUserId(
    maybeUserByteString: Option[ByteString]
  ): Either[Error, UserId] = {
    maybeUserByteString
      .flatMap(b => UserId.fromBytes(b.toByteArray))
      .map(u => Right(u))
      .getOrElse(Left(Error.MissingFeature))
  }
}
