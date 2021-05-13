package com.spotify.ladron

import java.time._
import java.util.Locale

import com.spotify.bcd.schemas.scala._
import com.spotify.ladron.agent.{ModelArm, SelectedArm}
import com.spotify.ladron.agent.model.{ContextFreeArm, ContextFreeModel}
import com.spotify.ladron.assignment.UserAssignment
import com.spotify.ladron.candidate.record._
import com.spotify.ladron.candidate.{
  CandidateMessage,
  HardwareType,
  TemplateValue,
  UserPlatformActivity,
  UserSelectionInfo
}
import com.spotify.ladron.common.{CampaignState, MessageChannel}
import com.spotify.ladron.delivery.{Message, Platform, SystemMessageAction}
import com.spotify.ladron.eligibility.{MatchedCampaign, MatchedUser, MessageHistory, SelectionInfo}
import com.spotify.ladron.evaluation.Evaluation.{EvaluationContext, EvaluationResults}
import com.spotify.ladron.evaluation.MatchedContext
import com.spotify.ladron.metrics.{
  UserDayOperationalSummary,
  UserMessageInteraction,
  UserMessageInteractionHistory
}
import com.spotify.ladron.metrics.business.IntermediateUserMessageInteraction
import com.spotify.ladron.metrics.business.UserMessageInteractionEnrichmentJob
import com.spotify.ladron.model.{
  AssignedCandidateMessages,
  AssignedSelectedMessage,
  CandidateMessageWithContext,
  CandidateMessages,
  UserWithContext
}
import com.spotify.ladron.reward.Reward
import com.spotify.ladron.selection.{
  CampaignId,
  CandidateContext,
  RecordId,
  SelectionContext,
  SelectionId
}
import com.spotify.ladron.train.{Context, ContextFreeModelByContext}
import com.spotify.message.data.{
  Channel,
  EventType,
  Metadata,
  NormalizedDeliveryEvent,
  UserNotificationUnsubscribe,
  MessageHistory => MessageHistoryData
}
import com.spotify.scio.coders.Coder
import com.twitter.algebird.AveragedValue
import com.spotify.ladron.eligibility.MessageHistory.{Message => MessageHistoryMessage}

// scalastyle:off number.of.methods
object Coders {
  implicit def userIdCoder: Coder[UserId] =
    Coder.xmap(Coder[Array[Byte]])(UserId.unsafeFromBytes, _.bytes.toArray)

  implicit def nonEmptyStringCoder: Coder[NonEmptyString] =
    Coder.xmap(Coder[String])(NonEmptyString.fromAvro, NonEmptyString.toAvro(_).toString)

  implicit def nonNegativeIntCoder: Coder[NonNegativeInt] =
    Coder.xmap(Coder[Int])(NonNegativeInt.fromAvro(_), NonNegativeInt.toAvro)

  implicit def nonNegativeLongCoder: Coder[NonNegativeLong] =
    Coder.xmap(Coder[Long])(NonNegativeLong.fromAvro(_), NonNegativeLong.toAvro)

  implicit def instantCoder: Coder[Instant] =
    Coder.xmap(Coder[InstantCoded])(
      c => Instant.ofEpochSecond(c.seconds, c.nanos),
      i => InstantCoded(i.getEpochSecond, i.getNano)
    )

  implicit def localDateTimeCoder: Coder[LocalDateTime] =
    Coder.xmap(Coder[Long])(
      { i: Long =>
        LocalDateTime.ofInstant(Instant.ofEpochMilli(i), ZoneOffset.UTC)
      },
      _.toInstant(ZoneOffset.UTC).toEpochMilli
    )

  implicit def localDateCoder: Coder[LocalDate] =
    Coder.xmap(Coder[Long])(LocalDate.ofEpochDay, _.toEpochDay)

  implicit def localTimeCoder: Coder[LocalTime] =
    Coder.xmap(Coder[Long])(LocalTime.ofNanoOfDay, _.toNanoOfDay)

  implicit def localeCoder: Coder[Locale] =
    // forLanguageTag <=> toLanguageTag is not guaranteed by Locale
    Coder.xmap(Coder[String])(Locale.forLanguageTag, _.toLanguageTag)

  implicit def countryCoder: Coder[Country] =
    Coder.xmap(Coder[String])(Country.fromAvro, Country.toAvro)

  implicit def repotingProductCoder: Coder[ReportingProduct] =
    Coder.xmap(Coder[String])(ReportingProduct.fromAvro, ReportingProduct.toAvro)

  implicit def productPeriodCoder: Coder[ProductPeriodId] =
    Coder.xmap(Coder[String])(ProductPeriodId.fromAvro, ProductPeriodId.toAvro(_).toString)

  implicit def selectionIdCoder: Coder[SelectionId] =
    Coder.xmap(Coder[String])(SelectionId.fromAvro, SelectionId.toAvro)

  // Pre allocated implicits
  // See https://www.scala-lang.org/blog/2018/06/04/scalac-profiling.html
  // Re-generate using bin/coders.py

  implicit def messageHistoryDataCoder: Coder[MessageHistoryData] = Coder.gen
  implicit def messageHistoryMessageCoder: Coder[MessageHistoryMessage] = Coder.gen
  implicit def optionNonNegativeIntCoder: Coder[Option[NonNegativeInt]] = Coder.gen
  implicit def optionUserLocaleCoder: Coder[Option[UserLocale]] = Coder.gen
  implicit def optionUserNotificationOptOutCoder: Coder[Option[UserNotificationOptOut]] = Coder.gen
  implicit def modelArmNonEmptyStringCoder: Coder[ModelArm[NonEmptyString]] = Coder.gen
  implicit def selectedArmNonEmptyStringCoder: Coder[SelectedArm[NonEmptyString]] = Coder.gen
  implicit def contextFreeArmCoder: Coder[ContextFreeArm] = Coder.gen
  implicit def contextFreeModelCoder: Coder[ContextFreeModel] = Coder.gen
  implicit def userAssignmentCoder: Coder[UserAssignment] = Coder.gen
  implicit def candidateMessageCoder: Coder[CandidateMessage] = Coder.gen
  implicit def hardwareTypeCoder: Coder[HardwareType] = Coder.gen
  implicit def templateValueCoder: Coder[TemplateValue] = Coder.gen
  implicit def userPlatformActivityCoder: Coder[UserPlatformActivity] = Coder.gen
  implicit def userSelectionInfoCoder: Coder[UserSelectionInfo] = Coder.gen
  implicit def userActivityCoder: Coder[UserActivity] = Coder.gen
  implicit def userInfoCoder: Coder[UserInfo] = Coder.gen
  implicit def userLocaleCoder: Coder[UserLocale] = Coder.gen
  implicit def userNotificationOptOutCoder: Coder[UserNotificationOptOut] = Coder.gen
  implicit def campaignStateCoder: Coder[CampaignState] = Coder.gen
  implicit def messageChannelCoder: Coder[MessageChannel] = Coder.gen
  implicit def messageCoder: Coder[Message] = Coder.gen
  implicit def platformCoder: Coder[Platform] = Coder.gen
  implicit def systemMessageActionCoder: Coder[SystemMessageAction] = Coder.gen
  implicit def matchedCampaignCoder: Coder[MatchedCampaign] = Coder.gen
  implicit def matchedUserCoder: Coder[MatchedUser] = Coder.gen
  implicit def messageHistoryCoder: Coder[MessageHistory] = Coder.gen
  implicit def selectionInfoCoder: Coder[SelectionInfo] = Coder.gen
  implicit def evaluationContextCoder: Coder[EvaluationContext] = Coder.gen
  implicit def evaluationResultsCoder: Coder[EvaluationResults] = Coder.gen
  implicit def matchedContextCoder: Coder[MatchedContext] = Coder.gen
  implicit def userDayOperationalSummaryCoder: Coder[UserDayOperationalSummary] = Coder.gen
  implicit def userMessageInteractionCoder: Coder[UserMessageInteraction] = Coder.gen
  implicit def userMessageInteractionHistoryCoder: Coder[UserMessageInteractionHistory] = Coder.gen
  implicit def intermediateUserMessageInteractionCoder: Coder[IntermediateUserMessageInteraction] =
    Coder.gen
  implicit def userMessageInteractionWithSelectionInfoCoder
    : Coder[UserMessageInteractionEnrichmentJob.UserMessageInteractionWithSelectionInfo] = Coder.gen
  implicit def assignedCandidateMessagesCoder: Coder[AssignedCandidateMessages] = Coder.gen
  implicit def assignedSelectedMessageCoder: Coder[AssignedSelectedMessage] = Coder.gen
  implicit def candidateMessageWithContextCoder: Coder[CandidateMessageWithContext] = Coder.gen
  implicit def candidateMessagesCoder: Coder[CandidateMessages] = Coder.gen
  implicit def userWithContextCoder: Coder[UserWithContext] = Coder.gen
  implicit def rewardCoder: Coder[Reward] = Coder.gen
  implicit def campaignIdCoder: Coder[CampaignId] = Coder.gen
  implicit def candidateContextCoder: Coder[CandidateContext] = Coder.gen
  implicit def recordIdCoder: Coder[RecordId] = Coder.gen
  implicit def selectionContextCoder: Coder[SelectionContext] = Coder.gen
  implicit def contextCoder: Coder[Context] = Coder.gen
  implicit def contextFreeModelByContextCoder: Coder[ContextFreeModelByContext] = Coder.gen
  implicit def channelCoder: Coder[Channel] = Coder.gen
  implicit def eventTypeCoder: Coder[EventType] = Coder.gen
  implicit def metadataCoder: Coder[Metadata] = Coder.gen
  implicit def normalizedDeliveryEventCoder: Coder[NormalizedDeliveryEvent] = Coder.gen
  implicit def platformOsCoder: Coder[PlatformOs] = Coder.gen
  implicit def userNotificationUnsubscribeCoder: Coder[UserNotificationUnsubscribe] = Coder.gen
  implicit def averagedValueCoder: Coder[AveragedValue] = Coder.gen
}
// scalastyle:on number.of.methods

private final case class InstantCoded(seconds: Long, nanos: Int)
