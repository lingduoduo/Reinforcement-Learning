package com.spotify.ladron.assignment

import java.time.LocalDate

import scala.collection.immutable
import scala.collection.immutable.ListMap

import com.spotify.ladron.NonNegativeInt
import com.spotify.ladron.common.AssignmentPolicy
import com.spotify.ladron.config.CommonBucketPolicy

/*
 * Assignment salt & weights on variants.
 * The variant `T` shouldn't be another Assignment
 * because Assigner has to be aware of its internal state, in order to assign multi-level.
 * */
sealed case class Assignment[T](salt: String, cellWeights: ListMap[T, NonNegativeInt]) {
  val weightedSum: Int = cellWeights.values
    .map(_.value)
    .sum

  require(weightedSum > 0, "at least one cell must have a positive weight")
  require(weightedSum == 1000, s"weight sum must be 1000. Actual sum: $weightedSum")
}

class AssignmentBucket[T](salt: String, cellWeights: ListMap[Assignment[T], NonNegativeInt])
    extends Assignment(salt, cellWeights)

object Assignment {
  type InterBucketAssignment = ListMap[AssignmentPolicy, NonNegativeInt]
  type BucketAssignment = (CommonBucketPolicy, InterBucketAssignment)
  type BucketAssignmentHistory = List[(LocalDate, List[BucketAssignment])]
  type BucketConfigHistory = List[(LocalDate, Map[CommonBucketPolicy, NonNegativeInt])]

  def bucketWeightOnDate(
    date: LocalDate,
    bucketPolicy: CommonBucketPolicy,
    bucketConfigsHistory: BucketConfigHistory
  ): NonNegativeInt =
    bucketConfigsHistory
      .find { case (d, _) => date.isAfter(d) || date.isEqual(d) }
      .flatMap {
        case (_, buckets: Map[CommonBucketPolicy, NonNegativeInt]) => buckets.get(bucketPolicy)
      }
      .getOrElse(
        throw new NoSuchElementException(s"No bucket defined for given date ${date.toString}")
      )

  def assignmentOn(
    date: LocalDate,
    assignmentHistory: BucketAssignmentHistory,
    bucketConfigsHistory: BucketConfigHistory,
    moduleBucketSalt: String
  ): AssignmentBucket[AssignmentPolicy] = {
    val weights: immutable.Seq[(Assignment[AssignmentPolicy], NonNegativeInt)] =
      assignmentHistory
        .find { case (d, _) => date.isAfter(d) || date.isEqual(d) }
        .map { case (_, cells) => cells }
        .getOrElse(throw new IllegalStateException("Date not supported for assignment."))
        .map {
          case (bucket, assignments) =>
            Assignment(bucket.salt, assignments) ->
              bucketWeightOnDate(date, bucket, bucketConfigsHistory)
        }

    new AssignmentBucket(salt = moduleBucketSalt, cellWeights = ListMap(weights: _*))
  }
}
//scalastyle:on
