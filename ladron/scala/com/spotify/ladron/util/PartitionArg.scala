package com.spotify.ladron.util

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime, ZoneId}

import com.spotify.bcd.schemas.scala.NonEmptyString
import com.spotify.scio

/**
 * Generic wrapper for handling both daily and hourly partitions.
 *
 * Some jobs (e.g `LadronJob`) can be run both daily and hourly and to avoid adding
 * parsing logic for date/datehour within each job this object can be used instead.
 */
object PartitionArg {
  val partitionDateTimeFormat: DateTimeFormatter = DateTimeFormatter
    .ofPattern("uuuu-MM-dd'T'HH")
    .withZone(ZoneId.of("Z"))

  val partitionDateFormat: DateTimeFormatter = DateTimeFormatter
    .ofPattern("uuuu-MM-dd")
    .withZone(ZoneId.of("Z"))

  def apply(scioArgs: scio.Args): PartitionArg = {
    val (date, datehour) =
      (scioArgs.optional("date"), scioArgs.optional("datehour")) match {
        case (Some(str), None) =>
          (Some(LocalDate.parse(str, PartitionArg.partitionDateFormat)), None)
        case (None, Some(str)) =>
          (None, Some(LocalDateTime.parse(str, PartitionArg.partitionDateTimeFormat)))
        case (Some(_), Some(_)) =>
          throw new IllegalArgumentException("only one of date or datehour can be supplied")
        case _ => throw new IllegalArgumentException("one of date or datehour must be supplied")
      }
    new PartitionArg(date, datehour)
  }
}

class PartitionArg private (
  private val date: Option[LocalDate],
  private val datehour: Option[LocalDateTime]
) {

  /**
   * Extracts a `LocalDate` independent on if the current partition is daily or hourly.
   */
  def toDate: LocalDate =
    (date, datehour) match {
      case (Some(d), None) => d
      case (None, Some(d)) => d.toLocalDate
      case _ =>
        throw new IllegalArgumentException("one of date or datehour must be supplied")
    }

  /**
   * Generates a String that identifies the parition regardless whether it's date or datetime
   */
  def toPartitionString: NonEmptyString =
    (date, datehour) match {
      case (Some(d), None) => NonEmptyString.fromAvro(d.format(PartitionArg.partitionDateFormat))
      case (None, Some(d)) =>
        NonEmptyString.fromAvro(d.format(PartitionArg.partitionDateTimeFormat))
      case _ =>
        throw new IllegalArgumentException("one of date or datehour must be supplied")
    }
}
