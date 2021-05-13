package com.spotify.ladron.config.assignment.policy

import com.spotify.bcd.schemas.scala.NonEmptyString
import com.spotify.ladron.common.{AssignmentPolicy, CampaignState, PolicyType}

/**
 * Assignment policies that are specific to engagement messaging module.
 */
object EngagementAssignmentPolicies {
  // This is really unified exploit, do not re-use during the unified experiment
  val EGreedy20Policy: AssignmentPolicy = AssignmentPolicy(
    name = NonEmptyString.fromAvro("egreedy_20"),
    `type` = PolicyType.EGreedy,
    includeState = Some(CampaignState.Prod)
  )

  // This is really unified exploit, do not re-use during the unified experiment
  val EGreedy20CtxPolicy: AssignmentPolicy = AssignmentPolicy(
    name = NonEmptyString.fromAvro("egreedy_ctx_20"),
    `type` = PolicyType.EGreedy,
    Some(NonEmptyString.fromAvro("model")),
    includeState = Some(CampaignState.Prod)
  )

  val ModelFullExploit: AssignmentPolicy = AssignmentPolicy(
    name = NonEmptyString.fromAvro("egreedy_ctx_0"),
    `type` = PolicyType.EGreedy,
    Some(NonEmptyString.fromAvro("model")),
    includeState = Some(CampaignState.Prod)
  )
}
