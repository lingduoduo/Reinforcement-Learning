package com.spotify.ladron.util

import java.time.LocalDate
import java.time.temporal.ChronoUnit

import org.joda.time.{LocalDate => JodaLocalDate}

import scala.util.Try

/**
 * Utility functions to deal with dates
 */
sealed trait DateUtil {
  def jLocalDateFromJoda(jodaLocalDate: JodaLocalDate): LocalDate =
    LocalDate.of(jodaLocalDate.getYear, jodaLocalDate.getMonthOfYear, jodaLocalDate.getDayOfMonth)

  def jodaFromLocalDate(localDate: LocalDate): JodaLocalDate =
    new JodaLocalDate(localDate.getYear, localDate.getMonthValue, localDate.getDayOfMonth)

  def calculateDaysFromDateRange(start: LocalDate, end: LocalDate): Try[Long] = {
    Try {
      require(!end.isBefore(start), "Start date must be before end date")
      ChronoUnit.DAYS.between(start, end)
    }
  }
}

object DateUtil extends DateUtil
