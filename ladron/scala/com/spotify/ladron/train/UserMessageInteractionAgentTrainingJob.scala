package com.spotify.ladron.train

import com.spotify.ladron.Coders._
import com.spotify.ladron.candidate.UserSelectionInfo
import com.spotify.ladron.common.MessagingModule
import com.spotify.ladron.config.Campaigns
import com.spotify.ladron.config.Campaigns.LadronCampaign
import com.spotify.ladron.metrics.UserMessageInteraction
import com.spotify.ladron.reward.Reward
import com.spotify.ladron.syntax.agent.BanditSyntax
import com.spotify.ladron.util.{MetricRegistry, ScioJobBase}
import com.spotify.scio
import com.spotify.scio.ContextAndArgs
import com.spotify.scio.avro._
import com.spotify.multiple.syntax._
import com.spotify.scio.values.SCollection

/**
 * The job goes through the user interactions and "trains" agents based on the interactions.
 * Different actions will have different rewards
 * ([[UserMessageInteractionAgentTrainingJob.toContextFreeReward]] defines how each interaction can
 * be mapped to a reward.)
 * The job will create one agent per context, and will save each agent as json, each one in a
 * different path.
 */
object UserMessageInteractionAgentTrainingJob extends ScioJobBase {
  val metricRegistry: MetricRegistry =
    MetricRegistry.createMetricRegistry("reactivation_bandit_training")

  final case class Args(
    messageInteraction: List[String],
    output: String,
    userSelectionInfo: String,
    minimalPullsPerCountry: Integer,
    module: MessagingModule
  )
  object Args {
    def parse(scioArgs: scio.Args): Args = Args(
      messageInteraction = scioArgs.list("messageInteraction"),
      output = scioArgs("output"),
      userSelectionInfo = scioArgs("userSelectionInfo"),
      minimalPullsPerCountry = scioArgs.int("minimalPullsPerCountry"),
      module = MessagingModule.withNameInsensitive(scioArgs("module"))
    )
  }

  def main(cmdArgs: Array[String]): Unit = {
    val (sc, scioArgs) = ContextAndArgs(cmdArgs)
    val args = Args.parse(scioArgs)

    val interactions = sc
      .avroFiles[UserMessageInteraction.Avro](args.messageInteraction)
      .withName("MapFromAvro")
      .map(UserMessageInteraction.fromAvro)

    val userSelectionInfo = sc
      .withName("ReadUserSelectionInfo")
      .avroFile[UserSelectionInfo.Avro](args.userSelectionInfo)
      .withName("MapFromAvro")
      .map(UserSelectionInfo.fromAvro)

    val campaigns: Set[LadronCampaign] = args.module match {
      case MessagingModule.Engagement   => Campaigns.EngagementCampaigns
      case MessagingModule.Reactivation => Campaigns.ReactivationCampaigns
      case MessagingModule.Activation   => Campaigns.ActivationCampaigns
      case _                            => throw new RuntimeException("Unknown messaging module")
    }

    pipeline(interactions, userSelectionInfo, args.minimalPullsPerCountry, campaigns)
      .saveBanditWithContext(args.output)

    close(sc)
  }

  def pipeline(
    interaction: SCollection[UserMessageInteraction],
    userSelectionInfo: SCollection[UserSelectionInfo],
    minimalPullsPerCountry: Int,
    campaigns: Set[LadronCampaign]
  ): SCollection[ContextFreeModelByContext] = {
    val rewards = createRewardFromInteraction(campaigns)(interaction)
    AgentTrainingJob.pipeline(rewards, userSelectionInfo, minimalPullsPerCountry)
  }

  /**
   * Filters the interactions for the given campaigns and transforms them into context free rewards
   * to be used for creating the context free bandits.
   */
  def createRewardFromInteraction(campaigns: Set[LadronCampaign])(
    interaction: SCollection[UserMessageInteraction]
  ): SCollection[Reward] = {
    interaction
      .filter(i => !Campaigns.TestCampaigns.contains(i.channel -> i.campaignId))
      .filter(i => campaigns.contains(i.channel -> i.campaignId))
      .map(toContextFreeReward)
  }

  private[train] def toContextFreeReward(
    interaction: UserMessageInteraction
  ): Reward =
    Reward(
      userId = interaction.userId,
      campaignId = interaction.campaignId,
      channel = interaction.channel,
      reward = getReward(interaction)
    )

  def getReward(interaction: UserMessageInteraction): Float = {
    if (interaction.isReject) {
      0f
    } else if (interaction.isClick) {
      1.0f
    } else {
      0.5f
    }
  }
}
