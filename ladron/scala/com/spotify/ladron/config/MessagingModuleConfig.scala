package com.spotify.ladron.config

import com.spotify.ladron.NonNegativeInt
import com.spotify.ladron.agent.BanditPolicy
import com.spotify.ladron.agent.policy.{EpsilonGreedyPolicy, RandomPolicy}
import com.spotify.ladron.assignment.Assignment
import com.spotify.ladron.assignment.Assignment.BucketConfigHistory
import com.spotify.ladron.common.{AssignmentPolicy, MessagingModule}
import com.spotify.ladron.config.Campaigns.DeliveryCampaign
import com.spotify.ladron.config.assignment.policy.{
  ActivationAssignmentPolicies,
  CommonAssignmentPolicies,
  EngagementAssignmentPolicies,
  EngagementHistoricalAssignmentPolicies,
  ReactivationAssignmentPolicies
}
import com.spotify.ladron.config.assignment.{
  ActivationAssignment,
  EngagementAssignment,
  ReactivationAssignment
}
import com.spotify.ladron.config.bucket.{CommonBucketConfigs, EngagementBucketConfigs}
import com.spotify.ladron.eligibility.MessageHistoryFilterJob.ReSendInterval

/**
 * Per Module global configuration
 */
final case class MessagingModuleConfig(
  module: MessagingModule,
  campaigns: Set[DeliveryCampaign],
  reSend: ReSendInterval,
  buckets: BucketConfigHistory,
  moduleBucketSalt: String,
  optionByPolicy: Map[AssignmentPolicy, BanditPolicy],
  assignment: Assignment.BucketAssignmentHistory
)

object MessagingModuleConfig {
  // scalastyle:off magic.number
  val Week: NonNegativeInt = NonNegativeInt.unsafeFromInt(7)
  val Month: NonNegativeInt = NonNegativeInt.unsafeFromInt(30)
  // Ladron only holds a 30 day history right now,
  // but using a large number to account for any future changes
  val Never: NonNegativeInt = NonNegativeInt.unsafeFromInt(9999)

  val ReactivationOptionsByPolicy: Map[AssignmentPolicy, BanditPolicy] = Map(
    CommonAssignmentPolicies.Explore -> RandomPolicy(),
    CommonAssignmentPolicies.Holdout -> RandomPolicy(),
    CommonAssignmentPolicies.UnifiedExplore -> RandomPolicy(),
    CommonAssignmentPolicies.UnifiedHoldout -> RandomPolicy(),
    ReactivationAssignmentPolicies.EGreedy20Policy -> EpsilonGreedyPolicy(epsilon = 0.2),
    ReactivationAssignmentPolicies.EGreedy20CtxPolicy -> EpsilonGreedyPolicy(epsilon = 0.2),
    ReactivationAssignmentPolicies.EGreedy10Policy -> EpsilonGreedyPolicy(epsilon = 0.1),
    ReactivationAssignmentPolicies.EGreedy10CtxPolicy -> EpsilonGreedyPolicy(epsilon = 0.1)
  )

  val ReactivationModuleConfig: MessagingModuleConfig = MessagingModuleConfig(
    module = MessagingModule.Reactivation,
    campaigns = Campaigns.ReactivationMessageDeliveryCampaigns,
    reSend = _ => Month,
    buckets = CommonBucketConfigs.BucketConfigs,
    moduleBucketSalt = CommonBucketPolicy.CommonBucketSalt,
    optionByPolicy = ReactivationOptionsByPolicy,
    assignment = ReactivationAssignment.Assignments
  )

  val EngagementOptionsByPolicy: Map[AssignmentPolicy, BanditPolicy] = Map(
    CommonAssignmentPolicies.Explore -> RandomPolicy(),
    CommonAssignmentPolicies.Holdout -> RandomPolicy(),
    CommonAssignmentPolicies.UnifiedExplore -> RandomPolicy(),
    CommonAssignmentPolicies.UnifiedHoldout -> RandomPolicy(),
    EngagementAssignmentPolicies.EGreedy20Policy -> EpsilonGreedyPolicy(epsilon = 0.2),
    EngagementAssignmentPolicies.EGreedy20CtxPolicy -> EpsilonGreedyPolicy(epsilon = 0.2),
    EngagementAssignmentPolicies.ModelFullExploit -> EpsilonGreedyPolicy(epsilon = 0),
    EngagementHistoricalAssignmentPolicies.UnnormalizedModel -> EpsilonGreedyPolicy(epsilon = 0)
  )

  val EngagementModuleConfig: MessagingModuleConfig = MessagingModuleConfig(
    module = MessagingModule.Engagement,
    campaigns = Campaigns.EngagementMessageDeliveryCampaigns,
    reSend = _ => Week,
    buckets = EngagementBucketConfigs.BucketConfigs,
    moduleBucketSalt = CommonBucketPolicy.CommonBucketSalt,
    optionByPolicy = EngagementOptionsByPolicy,
    assignment = EngagementAssignment.Assignments
  )

  val ActivationOptionsByPolicy: Map[AssignmentPolicy, BanditPolicy] = Map(
    CommonAssignmentPolicies.Explore -> RandomPolicy(),
    CommonAssignmentPolicies.Holdout -> RandomPolicy(),
    CommonAssignmentPolicies.UnifiedExplore -> RandomPolicy(),
    CommonAssignmentPolicies.UnifiedHoldout -> RandomPolicy(),
    ActivationAssignmentPolicies.ActivationEGreedy20CtxPolicy -> EpsilonGreedyPolicy(epsilon = 0.2),
    ActivationAssignmentPolicies.ActivationBaselinePolicy -> RandomPolicy()
  )

  val ActivationModuleConfig: MessagingModuleConfig = MessagingModuleConfig(
    module = MessagingModule.Activation,
    campaigns = Campaigns.ActivationMessageDeliveryCampaigns,
    reSend = _ => Never,
    buckets = CommonBucketConfigs.BucketConfigs,
    moduleBucketSalt = CommonBucketPolicy.CommonBucketSalt,
    optionByPolicy = ActivationOptionsByPolicy,
    assignment = ActivationAssignment.Assignments
  )

  val PerModuleConfig: Map[MessagingModule, MessagingModuleConfig] = Map(
    MessagingModule.Reactivation -> ReactivationModuleConfig,
    MessagingModule.Engagement -> EngagementModuleConfig,
    MessagingModule.Activation -> ActivationModuleConfig
  )
}
