package com.spotify.ladron.model
import com.spotify.bcd.schemas.scala.UserId
import com.spotify.ladron.candidate.CandidateMessage
import com.spotify.ladron.common.AssignmentPolicy
import com.spotify.ladron.selection.SelectionId
import org.tensorflow.example.Example

/**
 * Represents a message that has been selected for a given user.
 */
final case class AssignedSelectedMessage(
  userId: UserId,
  selectionId: SelectionId,
  candidates: Seq[CandidateMessageWithContext], // already filtered for recently sent
  selectedMessage: CandidateMessage,
  group: AssignmentPolicy,
  score: Double,
  explore: Boolean
)

/**
 * Defines a candidate message, but with an optional context
 */
final case class CandidateMessageWithContext(message: CandidateMessage, context: Example)
