package com.spotify.ladron.config.assignment

import java.time.LocalDate

import com.spotify.ladron.NonNegativeInt
import com.spotify.ladron.assignment.Assignment.{
  BucketAssignment,
  BucketAssignmentHistory,
  InterBucketAssignment
}
import com.spotify.ladron.config.CommonBucketPolicy
import com.spotify.ladron.config.assignment.policy.{
  ActivationAssignmentPolicies,
  CommonAssignmentPolicies
}

import scala.collection.immutable.ListMap

/**
 * Activation specific user assignment configuration.
 */
object ActivationAssignment {
  // scalastyle:off magic.number

  private[config] val holdoutBucket: InterBucketAssignment =
    ListMap(CommonAssignmentPolicies.Holdout -> NonNegativeInt.unsafeFromInt(1000))

  private[config] val exploreBucket: InterBucketAssignment =
    ListMap(CommonAssignmentPolicies.Explore -> NonNegativeInt.unsafeFromInt(1000))

  private[assignment] val eGreedy20CtxBucket: InterBucketAssignment = ListMap(
    ActivationAssignmentPolicies.ActivationEGreedy20CtxPolicy -> NonNegativeInt.unsafeFromInt(55),
    CommonAssignmentPolicies.Explore -> NonNegativeInt.unsafeFromInt(945)
  )

  private[assignment] val unifiedBucket: InterBucketAssignment = ListMap(
    ActivationAssignmentPolicies.ActivationEGreedy20CtxPolicy ->
      CommonAssignment.UnifiedExploitSize,
    CommonAssignmentPolicies.UnifiedExplore -> CommonAssignment.UnifiedExploreSize,
    CommonAssignmentPolicies.UnifiedHoldout -> CommonAssignment.UnifiedControlSize
  )

  private[assignment] val contextualVsBaselineBucket: InterBucketAssignment = ListMap(
    ActivationAssignmentPolicies.ActivationEGreedy20CtxPolicy -> NonNegativeInt.unsafeFromInt(300),
    ActivationAssignmentPolicies.ActivationBaselinePolicy -> NonNegativeInt.unsafeFromInt(300),
    CommonAssignmentPolicies.Explore -> NonNegativeInt.unsafeFromInt(300),
    CommonAssignmentPolicies.Holdout -> NonNegativeInt.unsafeFromInt(100)
  )

  private[assignment] val unifiedLifeCycleBucketAssignment: List[BucketAssignment] = List(
    CommonBucketPolicy.Bucket11 -> unifiedBucket,
    CommonBucketPolicy.Bucket12 -> unifiedBucket,
    CommonBucketPolicy.Bucket13 -> unifiedBucket,
    CommonBucketPolicy.Bucket14 -> unifiedBucket,
    // NB when experimenting in this bucket do not use ActivationEGreedy20CtxPolicy
    // since it is used in the unified test.
    CommonBucketPolicy.Bucket15 -> exploreBucket,
    CommonBucketPolicy.BucketHoldOut -> holdoutBucket
  )

  private[config] val contextualVsOldBucketAssignment: List[BucketAssignment] = List(
    CommonBucketPolicy.Bucket11 -> contextualVsBaselineBucket,
    CommonBucketPolicy.Bucket12 -> contextualVsBaselineBucket,
    CommonBucketPolicy.Bucket13 -> contextualVsBaselineBucket,
    CommonBucketPolicy.Bucket14 -> contextualVsBaselineBucket,
    CommonBucketPolicy.Bucket15 -> contextualVsBaselineBucket,
    CommonBucketPolicy.BucketHoldOut -> contextualVsBaselineBucket
  )

  private[config] val contextualBanditBucketAssignment: List[BucketAssignment] = List(
    CommonBucketPolicy.Bucket11 -> exploreBucket,
    CommonBucketPolicy.Bucket12 -> exploreBucket,
    CommonBucketPolicy.Bucket13 -> exploreBucket,
    CommonBucketPolicy.Bucket14 -> exploreBucket,
    CommonBucketPolicy.Bucket15 -> eGreedy20CtxBucket,
    CommonBucketPolicy.BucketHoldOut -> holdoutBucket
  )

  private[config] val initialBucketAssignment: List[BucketAssignment] = List(
    CommonBucketPolicy.Bucket11 -> exploreBucket,
    CommonBucketPolicy.Bucket12 -> exploreBucket,
    CommonBucketPolicy.Bucket13 -> exploreBucket,
    CommonBucketPolicy.Bucket14 -> exploreBucket,
    CommonBucketPolicy.Bucket15 -> exploreBucket,
    CommonBucketPolicy.BucketHoldOut -> holdoutBucket
  )

  private[config] val Assignments: BucketAssignmentHistory = List(
    LocalDate.of(2020, 4, 1) -> unifiedLifeCycleBucketAssignment,
    LocalDate.of(2020, 3, 11) -> contextualVsOldBucketAssignment,
    LocalDate.of(2020, 2, 23) -> contextualBanditBucketAssignment,
    LocalDate.of(2019, 10, 1) -> initialBucketAssignment
  )
}
