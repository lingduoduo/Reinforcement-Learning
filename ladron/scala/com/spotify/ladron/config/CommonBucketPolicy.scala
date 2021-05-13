package com.spotify.ladron.config

final case class CommonBucketPolicy(salt: String) extends AnyVal

object CommonBucketPolicy {
  val CommonBucketSalt = "ladron-enabled"

  /*
   * On decision of splitting 5 buckets
   * referring to ```doc/2019-06-26-inactive-14-day-and-randomization-experiment.md```
   *
   * June 18th 2019: Splitting 5-equal experiment-buckets (99% total) and 1 hold-out-bucket(1%).
   * Each bucket is some 10x more users than minimum required.
   *
   * Nov 25th 2019: in order to evaluate both experiments (reactivation and engagement)
   * independent of each other we need a way of selecting users for experiment independent of
   * each other. So we are making use of different salts. And global holdout users will be
   * determined in campaign runner.
   * */
  val Bucket11 = CommonBucketPolicy("first")
  val Bucket12 = CommonBucketPolicy("second")
  val Bucket13 = CommonBucketPolicy("third")
  val Bucket14 = CommonBucketPolicy("fourth")
  val Bucket15 = CommonBucketPolicy("fifth")

  val BucketHoldOut = CommonBucketPolicy("holdout")
}

object EngagementBucketPolicy {
  val EngagementBucketSalt = "engagement-ladron-enabled"
  val EngagementBucket11 = CommonBucketPolicy("engagement-first")
  val EngagementBucket12 = CommonBucketPolicy("engagement-second")
  val EngagementBucket13 = CommonBucketPolicy("engagement-third")
  val EngagementBucket14 = CommonBucketPolicy("engagement-fourth")
  val EngagementBucket15 = CommonBucketPolicy("engagement-fifth")
  val EngagementBucketHoldOut = CommonBucketPolicy("engagement-holdout")
}
