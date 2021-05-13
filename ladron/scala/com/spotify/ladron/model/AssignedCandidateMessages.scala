package com.spotify.ladron.model

import com.spotify.bcd.schemas.scala.UserId
import com.spotify.ladron.candidate.CandidateMessage
import com.spotify.ladron.common.AssignmentPolicy

/**
 * Eligible templates for a user plus user target group assignment.
 */
final case class AssignedCandidateMessages(
  userId: UserId,
  messages: Seq[CandidateMessage],
  group: AssignmentPolicy
)
