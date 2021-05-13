package com.spotify.ladron.model

import com.spotify.bcd.schemas.scala.UserId
import com.spotify.ladron.candidate.CandidateMessage

final case class CandidateMessages(userId: UserId, messages: Seq[CandidateMessage]) {}
