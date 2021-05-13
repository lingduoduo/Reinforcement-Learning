package com.spotify.ladron.config.assignment.policy

import com.spotify.bcd.schemas.scala.NonEmptyString
import com.spotify.ladron.common.{AssignmentPolicy, CampaignState, PolicyType}

/**
 * Historical assignment policies that are no longer used
 */
object EngagementHistoricalAssignmentPolicies {
  val UnnormalizedModel: AssignmentPolicy = AssignmentPolicy(
    name = NonEmptyString.fromAvro("egreedy_ctx_0"),
    `type` = PolicyType.EGreedy,
    Some(NonEmptyString.fromAvro("unnormalizedModel")),
    includeState = Some(CampaignState.Prod)
  )
}
