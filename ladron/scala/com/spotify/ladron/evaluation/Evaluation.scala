package com.spotify.ladron.evaluation

import com.spotify.bcd.schemas.scala.NonEmptyString
import com.spotify.ladron.agent.{BanditPolicy, ModelArm}
import com.spotify.ladron.common.MessageChannel
import com.spotify.ladron.config.Campaigns
import com.spotify.ladron.config.Campaigns.LadronCampaign
import com.spotify.ladron.metrics.UserMessageInteraction

object Evaluation {
  val Epsilon = 0.1
  val NumExperiments = 1

  val Contextual = "contextual"
  val ContextFree = "contextFree"

  case class EvaluationContext(
    candidates: List[ModelArm[NonEmptyString]],
    sentCampaign: NonEmptyString,
    country: NonEmptyString,
    channel: MessageChannel,
    reward: Double
  )

  case class EvaluationResults(
    channel: MessageChannel,
    banditPolicyName: String,
    scoreModel: String,
    numSamples: Long,
    averageCtr: Double
  )

  /**
   * Convert bandit reward { rejected : 0.0, non-action: 0.5,  click: 1.0}
   * to CTR by only letting 1.0 through.
   */
  def rewardToCtr(banditReward: Double): Double =
    if (banditReward == 1.0) banditReward else 0.0

  def header: Option[String] = Some("policy,channel,runs,avg")

  def string2NonEmptyString(s: String): NonEmptyString =
    NonEmptyString.parse(s).get

  def removeTestCampaigns(message: UserMessageInteraction): Boolean =
    !Campaigns.TestCampaigns.contains(message.channel -> message.campaignId)
}
