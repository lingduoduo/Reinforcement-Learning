package com.spotify.ladron.selection

import org.tensorflow.example.Example

import com.spotify.bcd.schemas.scala.UserId
import com.spotify.ladron.selection.CandidateContext.CandidateId

case class SelectionContext(
  userId: UserId,
  registrationCountry: String,
  contextByCandidate: Map[CandidateId, Example]
)
