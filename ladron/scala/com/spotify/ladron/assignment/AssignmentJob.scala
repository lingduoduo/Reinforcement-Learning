package com.spotify.ladron.assignment

import java.time.LocalDate

import org.apache.beam.sdk.metrics.Counter
import com.spotify.ladron.Coders._
import com.spotify.ladron.assignment.Assignment.{BucketAssignmentHistory, BucketConfigHistory}
import com.spotify.ladron.assignment.assignable.{Assignable, AssignableCandidates}
import com.spotify.ladron.common.{AssignmentPolicy, PolicyType}
import com.spotify.ladron.model.{AssignedCandidateMessages, CandidateMessages}
import com.spotify.ladron.util.{MetricRegistry, ScioJobBase}
import com.spotify.scio.coders.Coder
import com.spotify.scio.values.SCollection

/**
 * Job that assigns users to a target group.
 * See [[com.spotify.ladron.common.PolicyType]] for possible group types.
 */
object AssignmentJob extends ScioJobBase {
  override val metricRegistry: MetricRegistry =
    MetricRegistry.createMetricRegistry("assignment_metrics")

  object Counters {
    val CandidateGroupCount: Map[PolicyType, Counter] = PolicyType.values.map { group =>
      (group, groupCounter("candidates", group))
    }.toMap
  }

  /**
   * Assign using the Assigner and group sizes from Assignment.PolicyTypes.
   * Returns input together with group so that downstream components
   * can use the target group to decide if the message should be sent or not.
   * The logger parameter can be used to save the user assignment for later analysis.
   */
  private[assignment] def assign[ASSIGNABLE, RESULT: Coder](
    assignment: AssignmentBucket[AssignmentPolicy],
    assignable: Assignable[ASSIGNABLE, RESULT]
  )(users: SCollection[ASSIGNABLE]): SCollection[RESULT] = {
    users
      .map { user: ASSIGNABLE =>
        val userId = assignable.userId(user)
        val group = Assigner.assignCandidate(userId, assignment)
        assignable.groupCount(group.`type`).inc()
        assignable.result(user, group)
      }
  }

  /**
   * Assigns using [[Assignment.assignmentOn]].
   */
  private[assignment] def assign[ASSIGNABLE, RESULT: Coder](
    date: LocalDate,
    assignmentHistory: BucketAssignmentHistory,
    bucketConfigs: BucketConfigHistory,
    moduleBucketSalt: String,
    assignable: Assignable[ASSIGNABLE, RESULT]
  )(users: SCollection[ASSIGNABLE]): SCollection[RESULT] =
    assign(
      Assignment.assignmentOn(date, assignmentHistory, bucketConfigs, moduleBucketSalt),
      assignable
    )(users)

  /**
   * Assign UserCandidateMessages into UserCandidateMessagesAssignment.
   */
  def assignCandidates(
    date: LocalDate,
    assignmentHistory: BucketAssignmentHistory,
    bucketConfigs: BucketConfigHistory,
    moduleBucketSalt: String
  )(candidates: SCollection[CandidateMessages]): SCollection[AssignedCandidateMessages] =
    assign(date, assignmentHistory, bucketConfigs, moduleBucketSalt, AssignableCandidates)(
      candidates
    )

  private def groupCounter(assignmentType: String, group: PolicyType): Counter =
    metricRegistry.counter("total_assigned_" + assignmentType, group.name)
}
