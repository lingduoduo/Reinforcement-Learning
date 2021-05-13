package com.spotify.ladron.config.assignment.policy

import com.spotify.bcd.schemas.scala.NonEmptyString
import com.spotify.ladron.common.{AssignmentPolicy, CampaignState, PolicyType}

/**
 * Assignment policies that are specific to activation messaging module.
 */
object ActivationAssignmentPolicies {
  val Activation: AssignmentPolicy =
    AssignmentPolicy(NonEmptyString.fromAvro("activation"), PolicyType.Random)

  // This is really unified exploit, do not re-use during the unified experiment
  val ActivationEGreedy20CtxPolicy: AssignmentPolicy =
    AssignmentPolicy(
      NonEmptyString.fromAvro("activation_egreedy_ctx_20"),
      PolicyType.EGreedy,
      Some(NonEmptyString.fromAvro("ActivationModel")),
      includeState = Some(CampaignState.Prod)
    )

  val ActivationBaselinePolicy: AssignmentPolicy =
    AssignmentPolicy(
      NonEmptyString.fromAvro("activation_baseline_policy"),
      PolicyType.Random,
      None
    )
}
