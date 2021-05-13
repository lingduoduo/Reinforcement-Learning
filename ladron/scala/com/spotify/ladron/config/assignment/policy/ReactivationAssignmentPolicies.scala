package com.spotify.ladron.config.assignment.policy

import com.spotify.bcd.schemas.scala.NonEmptyString
import com.spotify.ladron.common.{AssignmentPolicy, CampaignState, PolicyType}
import com.spotify.ladron.model.AssignedCandidateMessages

/**
 * Assignment policies that are specific to reactivation messaging module.
 */
object ReactivationAssignmentPolicies {
  val Control_0: AssignmentPolicy =
    AssignmentPolicy(NonEmptyString.fromAvro("control_0"), PolicyType.Holdout)
  val Control_1: AssignmentPolicy =
    AssignmentPolicy(NonEmptyString.fromAvro("control_1"), PolicyType.Holdout)

  /**
   * Epsilon greedy policies
   */
  val EGreedy20Policy: AssignmentPolicy =
    AssignmentPolicy(NonEmptyString.fromAvro("egreedy_20"), PolicyType.EGreedy)

  val EGreedy10Policy: AssignmentPolicy =
    AssignmentPolicy(NonEmptyString.fromAvro("egreedy_10"), PolicyType.EGreedy)

  /**
   * Epsilon greedy policy with contextual exploitation.
   * This is really unified exploit, do not re-use during the unified experiment
   */
  val EGreedy20CtxPolicy: AssignmentPolicy =
    AssignmentPolicy(
      NonEmptyString.fromAvro("egreedy_ctx_20"),
      PolicyType.EGreedy,
      Some(NonEmptyString.fromAvro("model")),
      includeState = Some(CampaignState.Prod)
    )

  val EGreedy10CtxPolicy: AssignmentPolicy =
    AssignmentPolicy(
      NonEmptyString.fromAvro("egreedy_ctx_10"),
      PolicyType.EGreedy,
      Some(NonEmptyString.fromAvro("model"))
    )
}
