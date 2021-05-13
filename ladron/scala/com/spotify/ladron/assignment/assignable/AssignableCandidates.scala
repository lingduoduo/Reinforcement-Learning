package com.spotify.ladron.assignment.assignable

import org.apache.beam.sdk.metrics.Counter

import com.spotify.bcd.schemas.scala.UserId
import com.spotify.ladron.assignment.AssignmentJob.Counters
import com.spotify.ladron.common.{AssignmentPolicy, PolicyType}
import com.spotify.ladron.model.{AssignedCandidateMessages, CandidateMessages}

private[assignment] case object AssignableCandidates
    extends Assignable[CandidateMessages, AssignedCandidateMessages] {
  override def userId(assignable: CandidateMessages): UserId = assignable.userId

  override def result(
    assigned: CandidateMessages,
    policy: AssignmentPolicy
  ): AssignedCandidateMessages =
    AssignedCandidateMessages(
      userId = assigned.userId,
      messages = assigned.messages,
      group = policy
    )

  override def groupCount: Map[PolicyType, Counter] = Counters.CandidateGroupCount
}
