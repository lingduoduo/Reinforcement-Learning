package com.spotify.ladron.candidate.record

import java.time.LocalDate

import com.spotify.bcd.schemas.scala.{Country, ReportingProduct, UserId, UserSnapshot4}
import com.spotify.ladron.util.DateUtil.jLocalDateFromJoda

final case class UserInfo(
  userId: UserId,
  registrationCountry: Country,
  reportingProduct: ReportingProduct,
  registrationDate: LocalDate
)

object UserInfo {
  def fromUserSnapshot(userSnapshot4: UserSnapshot4.Avro): UserInfo = {
    UserInfo(
      userId = UserId.fromAvro(userSnapshot4.getUserId),
      registrationCountry = Country.fromAvro(userSnapshot4.getRegistrationCountry),
      reportingProduct = ReportingProduct.fromAvro(userSnapshot4.getReportingProduct),
      registrationDate = jLocalDateFromJoda(userSnapshot4.getRegistrationDate)
    )
  }
}
