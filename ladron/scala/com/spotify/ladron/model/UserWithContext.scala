package com.spotify.ladron.model
import com.spotify.bcd.schemas.scala.UserId

case class UserWithContext(userId: UserId, registrationCountry: String)
