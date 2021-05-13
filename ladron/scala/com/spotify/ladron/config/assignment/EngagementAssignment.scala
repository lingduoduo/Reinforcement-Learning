package com.spotify.ladron.config.assignment

import java.time.LocalDate

import scala.collection.immutable.ListMap
import com.spotify.ladron.NonNegativeInt
import com.spotify.ladron.assignment.Assignment.{
  BucketAssignment,
  BucketAssignmentHistory,
  InterBucketAssignment
}
import com.spotify.ladron.config.CommonBucketPolicy
import com.spotify.ladron.config.assignment.policy.{
  CommonAssignmentPolicies,
  EngagementAssignmentPolicies
}
import com.spotify.ladron.config.assignment.EngagementHistoricalAssignments.{
  canaryRandomAssignment,
  ctxMabRolloutAssignment,
  individualBucketSaltAssignment,
  initialBucketAssignment,
  mabRolloutAssignment,
  unifiedLifeCycleAndDoubleModel,
  unifiedLifeCycleAssignment,
  unifiedLifeCycleCtxAssignment
}

/**
 * Engagement specific user assignment configuration.
 */
object EngagementAssignment {
  // scalastyle:off magic.number
  private[config] val holdoutBucket: InterBucketAssignment =
    ListMap(CommonAssignmentPolicies.Holdout -> NonNegativeInt.unsafeFromInt(1000))

  private[config] val exploreBucket: InterBucketAssignment =
    ListMap(CommonAssignmentPolicies.Explore -> NonNegativeInt.unsafeFromInt(1000))

  private[assignment] val unifiedCtxBucketV2: InterBucketAssignment = ListMap(
    EngagementAssignmentPolicies.EGreedy20CtxPolicy ->
      CommonAssignment.UnifiedExploitContextualSize,
    EngagementAssignmentPolicies.EGreedy20Policy -> CommonAssignment.UnifiedExploitContextFreeSize,
    CommonAssignmentPolicies.UnifiedExplore -> CommonAssignment.UnifiedExploreSize,
    CommonAssignmentPolicies.UnifiedHoldout -> CommonAssignment.UnifiedControlSize
  )

  private[config] val unifiedLifeCycleCtxAssignmentV2: List[BucketAssignment] = List(
    CommonBucketPolicy.Bucket11 -> unifiedCtxBucketV2,
    CommonBucketPolicy.Bucket12 -> unifiedCtxBucketV2,
    CommonBucketPolicy.Bucket13 -> unifiedCtxBucketV2,
    CommonBucketPolicy.Bucket14 -> unifiedCtxBucketV2,
    // NB when experimenting in this bucket do not use EGreedy20CtxPolicy or EGreedy20Policy
    // since it is used in the unified test.
    CommonBucketPolicy.Bucket15 -> exploreBucket,
    CommonBucketPolicy.BucketHoldOut -> holdoutBucket
  )

  val Assignments: BucketAssignmentHistory = List(
    LocalDate.of(2020, 8, 7) -> unifiedLifeCycleCtxAssignmentV2,
    LocalDate.of(2020, 7, 3) -> unifiedLifeCycleAndDoubleModel,
    LocalDate.of(2020, 4, 28) -> unifiedLifeCycleCtxAssignmentV2,
    LocalDate.of(2020, 4, 17) -> unifiedLifeCycleCtxAssignment,
    LocalDate.of(2020, 4, 1) -> unifiedLifeCycleAssignment,
    LocalDate.of(2020, 3, 31) -> canaryRandomAssignment,
    LocalDate.of(2020, 3, 21) -> ctxMabRolloutAssignment,
    LocalDate.of(2020, 1, 22) -> mabRolloutAssignment,
    LocalDate.of(2019, 12, 4) -> individualBucketSaltAssignment,
    LocalDate.of(2019, 8, 19) -> initialBucketAssignment
  )
}
