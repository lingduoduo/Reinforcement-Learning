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
  EngagementAssignmentPolicies,
  ReactivationAssignmentPolicies
}

/**
 * Reactivation specific user assignment configuration.
 */
object ReactivationAssignment {
  // scalastyle:off magic.number

  private[assignment] val holdoutBucket: InterBucketAssignment =
    ListMap(CommonAssignmentPolicies.Holdout -> NonNegativeInt.unsafeFromInt(1000))

  private[assignment] val eGreedy20Bucket: InterBucketAssignment = ListMap(
    ReactivationAssignmentPolicies.EGreedy20Policy -> NonNegativeInt.unsafeFromInt(1000)
  )

  private[assignment] val ctxUpliftTest: InterBucketAssignment = ListMap(
    ReactivationAssignmentPolicies.EGreedy10Policy -> NonNegativeInt.unsafeFromInt(400),
    ReactivationAssignmentPolicies.EGreedy10CtxPolicy -> NonNegativeInt.unsafeFromInt(400),
    CommonAssignmentPolicies.Holdout -> NonNegativeInt.unsafeFromInt(200)
  )

  private[assignment] val eGreedy20CtxBucket: InterBucketAssignment = ListMap(
    ReactivationAssignmentPolicies.EGreedy20CtxPolicy -> NonNegativeInt.unsafeFromInt(1000)
  )

  private[assignment] val exploreBucket: InterBucketAssignment = ListMap(
    CommonAssignmentPolicies.Explore -> NonNegativeInt.unsafeFromInt(1000)
  )

  private[assignment] val unifiedBucket: InterBucketAssignment = ListMap(
    ReactivationAssignmentPolicies.EGreedy20CtxPolicy -> CommonAssignment.UnifiedExploitSize,
    CommonAssignmentPolicies.UnifiedExplore -> CommonAssignment.UnifiedExploreSize,
    CommonAssignmentPolicies.UnifiedHoldout -> CommonAssignment.UnifiedControlSize
  )

  private[config] val UnifiedLifeCycleAssignment: List[BucketAssignment] = List(
    CommonBucketPolicy.Bucket11 -> unifiedBucket,
    CommonBucketPolicy.Bucket12 -> unifiedBucket,
    CommonBucketPolicy.Bucket13 -> unifiedBucket,
    CommonBucketPolicy.Bucket14 -> unifiedBucket,
    // NB when experimenting in this bucket do not use EGreedy20CtxPolicy
    // since it is used in the unified test.
    CommonBucketPolicy.Bucket15 -> exploreBucket,
    CommonBucketPolicy.BucketHoldOut -> holdoutBucket
  )

  private[assignment] val CtxUpliftTestAssignment: List[BucketAssignment] = List(
    CommonBucketPolicy.Bucket11 -> ctxUpliftTest,
    CommonBucketPolicy.Bucket12 -> ctxUpliftTest,
    CommonBucketPolicy.Bucket13 -> ctxUpliftTest,
    CommonBucketPolicy.Bucket14 -> ctxUpliftTest,
    CommonBucketPolicy.Bucket15 -> ctxUpliftTest,
    CommonBucketPolicy.BucketHoldOut -> holdoutBucket
  )

  private[assignment] val HistoricNoExplore: List[BucketAssignment] = List(
    CommonBucketPolicy.Bucket11 -> eGreedy20Bucket,
    CommonBucketPolicy.Bucket12 -> eGreedy20Bucket,
    CommonBucketPolicy.Bucket13 -> eGreedy20CtxBucket,
    CommonBucketPolicy.Bucket14 -> eGreedy20Bucket,
    CommonBucketPolicy.Bucket15 -> eGreedy20Bucket,
    CommonBucketPolicy.BucketHoldOut -> holdoutBucket
  )

  private[assignment] val HistoricRolloutFullBandit: List[BucketAssignment] = List(
    CommonBucketPolicy.Bucket11 -> exploreBucket,
    CommonBucketPolicy.Bucket12 -> eGreedy20Bucket,
    CommonBucketPolicy.Bucket13 -> eGreedy20CtxBucket,
    CommonBucketPolicy.Bucket14 -> eGreedy20Bucket,
    CommonBucketPolicy.Bucket15 -> eGreedy20Bucket,
    CommonBucketPolicy.BucketHoldOut -> holdoutBucket
  )

  private[assignment] val HistoricRolloutCTXBucketAssignment: List[BucketAssignment] = List(
    CommonBucketPolicy.Bucket11 -> exploreBucket,
    CommonBucketPolicy.Bucket12 -> eGreedy20Bucket,
    CommonBucketPolicy.Bucket13 -> eGreedy20CtxBucket,
    CommonBucketPolicy.Bucket14 -> exploreBucket,
    CommonBucketPolicy.Bucket15 -> exploreBucket,
    CommonBucketPolicy.BucketHoldOut -> holdoutBucket
  )

  private[assignment] val HistoricRolloutMABBucketAssignment: List[BucketAssignment] = List(
    CommonBucketPolicy.Bucket11 -> exploreBucket,
    CommonBucketPolicy.Bucket12 -> eGreedy20Bucket,
    CommonBucketPolicy.Bucket13 -> exploreBucket,
    CommonBucketPolicy.Bucket14 -> exploreBucket,
    CommonBucketPolicy.Bucket15 -> exploreBucket,
    CommonBucketPolicy.BucketHoldOut -> holdoutBucket
  )

  /**
   * Historical bucket configs (partitions, date was +1 until 2019-08-21,
   * legacy 2019-08-21 was never sent).
   *
   * failed 24, 25, 26, 27, 28, 29, 30, 01, 10, 11, 12, 13
   * 2019-12-09--2020-01-15  B11 Explore
   *                         B12 EGreedy20Policy (100%)
   *                         B13 EGreedy20CtxPolicy (100%)
   *                         B14 EGreedy20Policy (100%)
   *                         B15 EGreedy20Policy (100%)
   *
   * failed 22, 23, 24
   * 2019-11-22--2019-12-08  B11 Explore  (failed 22, 23, 24)
   *                         B12 EGreedy20Policy (100%)
   *                         B13 EGreedy20CtxPolicy (100%)
   *                         B14 Explore
   *                         B15 Explore
   *
   * 2019-08-22--2019-11-21  B11 Explore
   *                         B12 EGreedy20Policy (100%)
   *                         B13 Explore
   *                         B14 Explore
   *                         B15 Explore
   */
  private[assignment] val CurrentAssignments: BucketAssignmentHistory = List(
    LocalDate.of(2020, 4, 1) -> UnifiedLifeCycleAssignment,
    LocalDate.of(2020, 3, 19) -> CtxUpliftTestAssignment,
    LocalDate.of(2020, 1, 16) -> HistoricNoExplore,
    LocalDate.of(2019, 12, 9) -> HistoricRolloutFullBandit,
    LocalDate.of(2019, 11, 22) -> HistoricRolloutCTXBucketAssignment,
    LocalDate.of(2019, 8, 19) -> HistoricRolloutMABBucketAssignment
  )

  val Assignments: BucketAssignmentHistory = CurrentAssignments
}
