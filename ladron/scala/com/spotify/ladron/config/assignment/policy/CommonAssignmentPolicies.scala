package com.spotify.ladron.config.assignment.policy

import com.spotify.bcd.schemas.scala.NonEmptyString
import com.spotify.ladron.common.{AssignmentPolicy, CampaignState, PolicyType}

/**
 * Assignment policies that are common to all messaging modules.
 */
object CommonAssignmentPolicies {
  val Explore = AssignmentPolicy(NonEmptyString.fromAvro("explore"), PolicyType.Random)

  /**
   * This group will never get any messages, it should always be the last % of users in assignment.
   */
  val Holdout = AssignmentPolicy(NonEmptyString.fromAvro("holdout"), PolicyType.Holdout)

  val UnifiedExplore =
    AssignmentPolicy(
      NonEmptyString.fromAvro("unified-explore"),
      PolicyType.Random,
      includeState = Some(CampaignState.Prod)
    )
  val UnifiedHoldout =
    AssignmentPolicy(NonEmptyString.fromAvro("unified-holdout"), PolicyType.Holdout)
}
