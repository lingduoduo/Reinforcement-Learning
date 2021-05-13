package com.spotify.ladron.assignment.assignable

import org.apache.beam.sdk.metrics.Counter

import com.spotify.bcd.schemas.scala.UserId
import com.spotify.ladron.common.{AssignmentPolicy, PolicyType}

/**
 * Requirements for the AssignmentJob.
 * @tparam ASSIGNABLE The input type.
 * @tparam RESULT The result type.
 */
private[assignment] trait Assignable[ASSIGNABLE, RESULT] extends Serializable {

  /**
   * An Assignable is assigned to a group based on the user id.
   */
  def userId(assignable: ASSIGNABLE): UserId

  /**
   * Construct the Result type from the given Assignable and group.
   */
  def result(assigned: ASSIGNABLE, policy: AssignmentPolicy): RESULT

  /**
   * A reference to the counters to use for this Assignable.
   */
  def groupCount: Map[PolicyType, Counter]
}
