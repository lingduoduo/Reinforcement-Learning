package com.spotify.ladron.selection

import com.spotify.bcd.schemas.scala.{NonEmptyString, UserId}

final case class ScoredSelectionContext(
  userId: UserId,
  scoreByCampaign: Map[NonEmptyString, Float]
)
