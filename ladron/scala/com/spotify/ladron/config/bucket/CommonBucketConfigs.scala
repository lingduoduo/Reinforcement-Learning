package com.spotify.ladron.config.bucket

import java.time.LocalDate

import com.spotify.ladron.assignment.Assignment.BucketConfigHistory
import com.spotify.ladron.config.CommonBucketPolicy
import com.spotify.ladron.NonNegativeInt

import scala.collection.immutable.ListMap

// scalastyle:off
object CommonBucketConfigs {
  val CurrentBuckets: Map[CommonBucketPolicy, NonNegativeInt] = ListMap(
    CommonBucketPolicy.Bucket11 -> NonNegativeInt.unsafeFromInt(200),
    CommonBucketPolicy.Bucket12 -> NonNegativeInt.unsafeFromInt(200),
    CommonBucketPolicy.Bucket13 -> NonNegativeInt.unsafeFromInt(200),
    CommonBucketPolicy.Bucket14 -> NonNegativeInt.unsafeFromInt(200),
    CommonBucketPolicy.Bucket15 -> NonNegativeInt.unsafeFromInt(190),
    CommonBucketPolicy.BucketHoldOut -> NonNegativeInt.unsafeFromInt(10)
  )

  val BucketConfigs: BucketConfigHistory = List(
    LocalDate.of(2019, 8, 19) -> CurrentBuckets
  )
}
