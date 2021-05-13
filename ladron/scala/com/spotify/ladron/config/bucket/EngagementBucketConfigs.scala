package com.spotify.ladron.config.bucket

import java.time.LocalDate

import com.spotify.ladron.config.CommonBucketPolicy
import com.spotify.ladron.config.EngagementBucketPolicy._
import com.spotify.ladron.NonNegativeInt
import com.spotify.ladron.assignment.Assignment.BucketConfigHistory

import scala.collection.immutable.ListMap

// scalastyle:off
object EngagementBucketConfigs {
  private val OnePercentHoldoutBuckets: Map[CommonBucketPolicy, NonNegativeInt] = ListMap(
    CommonBucketPolicy.Bucket11 -> NonNegativeInt.unsafeFromInt(200),
    CommonBucketPolicy.Bucket12 -> NonNegativeInt.unsafeFromInt(200),
    CommonBucketPolicy.Bucket13 -> NonNegativeInt.unsafeFromInt(200),
    CommonBucketPolicy.Bucket14 -> NonNegativeInt.unsafeFromInt(200),
    CommonBucketPolicy.Bucket15 -> NonNegativeInt.unsafeFromInt(190),
    CommonBucketPolicy.BucketHoldOut -> NonNegativeInt.unsafeFromInt(10)
  )

  private val FivePercentHoldoutBuckets: Map[CommonBucketPolicy, NonNegativeInt] = ListMap(
    CommonBucketPolicy.Bucket11 -> NonNegativeInt.unsafeFromInt(190),
    CommonBucketPolicy.Bucket12 -> NonNegativeInt.unsafeFromInt(190),
    CommonBucketPolicy.Bucket13 -> NonNegativeInt.unsafeFromInt(190),
    CommonBucketPolicy.Bucket14 -> NonNegativeInt.unsafeFromInt(190),
    CommonBucketPolicy.Bucket15 -> NonNegativeInt.unsafeFromInt(190),
    CommonBucketPolicy.BucketHoldOut -> NonNegativeInt.unsafeFromInt(50)
  )

  private val IndependentBuckets: Map[CommonBucketPolicy, NonNegativeInt] = ListMap(
    EngagementBucket11 -> NonNegativeInt.unsafeFromInt(190),
    EngagementBucket12 -> NonNegativeInt.unsafeFromInt(190),
    EngagementBucket13 -> NonNegativeInt.unsafeFromInt(190),
    EngagementBucket14 -> NonNegativeInt.unsafeFromInt(190),
    EngagementBucket15 -> NonNegativeInt.unsafeFromInt(190),
    EngagementBucketHoldOut -> NonNegativeInt.unsafeFromInt(50)
  )

  val BucketConfigs: BucketConfigHistory = List(
    LocalDate.of(2020, 4, 1) -> CommonBucketConfigs.CurrentBuckets,
    LocalDate.of(2019, 12, 4) -> IndependentBuckets,
    LocalDate.of(2019, 11, 13) -> FivePercentHoldoutBuckets,
    LocalDate.of(2019, 8, 19) -> OnePercentHoldoutBuckets
  )
}
