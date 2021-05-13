package com.spotify.ladron.config.assignment

import com.spotify.ladron.NonNegativeInt
import com.spotify.ladron.assignment.Assignment.{BucketAssignment, InterBucketAssignment}
import com.spotify.ladron.config.CommonBucketPolicy
import com.spotify.ladron.config.EngagementBucketPolicy._
import com.spotify.ladron.config.assignment.EngagementAssignment.{
  exploreBucket,
  holdoutBucket,
  unifiedCtxBucketV2
}
import com.spotify.ladron.config.assignment.policy.{
  CommonAssignmentPolicies,
  EngagementAssignmentPolicies,
  EngagementHistoricalAssignmentPolicies
}

import scala.collection.immutable.ListMap

/**
 * Engagement user assignments that are no longer used
 */
object EngagementHistoricalAssignments {
  // scalastyle:off magic.number

  private[config] val doubleModelBucket: InterBucketAssignment = ListMap(
    CommonAssignmentPolicies.Explore -> NonNegativeInt.unsafeFromInt(400),
    EngagementAssignmentPolicies.ModelFullExploit -> NonNegativeInt.unsafeFromInt(300),
    EngagementHistoricalAssignmentPolicies.UnnormalizedModel -> NonNegativeInt.unsafeFromInt(300)
  )

  private[assignment] val eGreedy20Bucket: InterBucketAssignment = ListMap(
    EngagementAssignmentPolicies.EGreedy20Policy -> NonNegativeInt.unsafeFromInt(1000)
  )

  private[assignment] val eGreedy20CtxBucket: InterBucketAssignment = ListMap(
    EngagementAssignmentPolicies.EGreedy20CtxPolicy -> NonNegativeInt.unsafeFromInt(55),
    CommonAssignmentPolicies.Explore -> NonNegativeInt.unsafeFromInt(945)
  )

  private[assignment] val unifiedCtxBucket: InterBucketAssignment = ListMap(
    EngagementAssignmentPolicies.EGreedy20CtxPolicy -> CommonAssignment.UnifiedExploitSize,
    CommonAssignmentPolicies.UnifiedExplore -> CommonAssignment.UnifiedExploreSize,
    CommonAssignmentPolicies.UnifiedHoldout -> CommonAssignment.UnifiedControlSize
  )

  private[assignment] val unifiedBucket: InterBucketAssignment = ListMap(
    EngagementAssignmentPolicies.EGreedy20Policy -> CommonAssignment.UnifiedExploitSize,
    CommonAssignmentPolicies.UnifiedExplore -> CommonAssignment.UnifiedExploreSize,
    CommonAssignmentPolicies.UnifiedHoldout -> CommonAssignment.UnifiedControlSize
  )

  private[config] val initialBucketAssignment: List[BucketAssignment] = List(
    CommonBucketPolicy.Bucket11 -> exploreBucket,
    CommonBucketPolicy.Bucket12 -> exploreBucket,
    CommonBucketPolicy.Bucket13 -> exploreBucket,
    CommonBucketPolicy.Bucket14 -> exploreBucket,
    CommonBucketPolicy.Bucket15 -> exploreBucket,
    CommonBucketPolicy.BucketHoldOut -> holdoutBucket
  )

  private[config] val individualBucketSaltAssignment: List[BucketAssignment] = List(
    EngagementBucket11 -> exploreBucket,
    EngagementBucket12 -> exploreBucket,
    EngagementBucket13 -> exploreBucket,
    EngagementBucket14 -> exploreBucket,
    EngagementBucket15 -> exploreBucket,
    EngagementBucketHoldOut -> holdoutBucket
  )

  private[config] val mabRolloutAssignment: List[BucketAssignment] = List(
    EngagementBucket11 -> eGreedy20Bucket,
    EngagementBucket12 -> exploreBucket,
    EngagementBucket13 -> exploreBucket,
    EngagementBucket14 -> exploreBucket,
    EngagementBucket15 -> exploreBucket,
    EngagementBucketHoldOut -> holdoutBucket
  )

  private[config] val ctxMabRolloutAssignment: List[BucketAssignment] = List(
    EngagementBucket11 -> eGreedy20CtxBucket,
    EngagementBucket12 -> holdoutBucket,
    EngagementBucket13 -> holdoutBucket,
    EngagementBucket14 -> holdoutBucket,
    EngagementBucket15 -> holdoutBucket,
    EngagementBucketHoldOut -> holdoutBucket
  )

  private[config] val canaryRandomAssignment: List[BucketAssignment] = List(
    EngagementBucket11 -> exploreBucket,
    EngagementBucket12 -> holdoutBucket,
    EngagementBucket13 -> holdoutBucket,
    EngagementBucket14 -> holdoutBucket,
    EngagementBucket15 -> holdoutBucket,
    EngagementBucketHoldOut -> holdoutBucket
  )

  private[config] val unifiedLifeCycleAssignment: List[BucketAssignment] = List(
    CommonBucketPolicy.Bucket11 -> unifiedBucket,
    CommonBucketPolicy.Bucket12 -> unifiedBucket,
    CommonBucketPolicy.Bucket13 -> unifiedBucket,
    CommonBucketPolicy.Bucket14 -> unifiedBucket,
    CommonBucketPolicy.Bucket15 -> exploreBucket,
    CommonBucketPolicy.BucketHoldOut -> holdoutBucket
  )

  private[config] val unifiedLifeCycleCtxAssignment: List[BucketAssignment] = List(
    CommonBucketPolicy.Bucket11 -> unifiedCtxBucket,
    CommonBucketPolicy.Bucket12 -> unifiedCtxBucket,
    CommonBucketPolicy.Bucket13 -> unifiedCtxBucket,
    CommonBucketPolicy.Bucket14 -> unifiedCtxBucket,
    // NB when experimenting in this bucket do not use EGreedy20CtxPolicy or EGreedy20Policy
    // since it is used in the unified test.
    CommonBucketPolicy.Bucket15 -> exploreBucket,
    CommonBucketPolicy.BucketHoldOut -> holdoutBucket
  )

  private[config] val unifiedLifeCycleAndDoubleModel: List[BucketAssignment] = List(
    CommonBucketPolicy.Bucket11 -> unifiedCtxBucketV2,
    CommonBucketPolicy.Bucket12 -> unifiedCtxBucketV2,
    CommonBucketPolicy.Bucket13 -> unifiedCtxBucketV2,
    CommonBucketPolicy.Bucket14 -> unifiedCtxBucketV2,
    // NB when experimenting in this bucket do not use EGreedy20CtxPolicy or EGreedy20Policy
    // since it is used in the unified test.
    CommonBucketPolicy.Bucket15 -> doubleModelBucket,
    CommonBucketPolicy.BucketHoldOut -> holdoutBucket
  )
}
