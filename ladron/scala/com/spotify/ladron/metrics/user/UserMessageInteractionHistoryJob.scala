package com.spotify.ladron.metrics.user

import java.time.LocalDate

import com.spotify.ladron.Coders._
import com.spotify.ladron.avro.metrics
import com.spotify.ladron.avro.metrics.{UserMessageInteraction => JUserMessageInteraction}
import com.spotify.ladron.metrics.{UserMessageInteraction, UserMessageInteractionHistory}
import com.spotify.ladron.util.{MetricRegistry, ScioJobBase}
import com.spotify.scio
import com.spotify.scio.ContextAndArgs
import com.spotify.scio.avro._
import com.spotify.multiple.syntax._
import com.spotify.scio.values.SCollection

/*
 * The jobs aggregate 14 partitions of 3 day aggregations of UserMessageInteraction
 * */
object UserMessageInteractionHistoryJob extends ScioJobBase {

  override val metricRegistry: MetricRegistry =
    MetricRegistry.createMetricRegistry("user_message_interaction_history")
  metricRegistry.register(UserMessageInteractionHistoryJob.metricRegistry)

  final case class Args(
    userMessageInteraction: List[String],
    date: LocalDate,
    output: String
  )

  object Args {
    def parse(scioArgs: scio.Args): Args = Args(
      userMessageInteraction = scioArgs.list("userMessageInteraction"),
      // scio will not pass dataPartition as an argument since it is being read as an option
      date = LocalDate.parse(scioArgs("date")),
      output = scioArgs("output")
    )
  }

  def main(cmdArgs: Array[String]): Unit = {
    val (sc, scioArgs) = ContextAndArgs(cmdArgs)
    val args = Args.parse(scioArgs)

    val userMessageInteractions: SCollection[UserMessageInteraction] = sc
      .avroFiles[JUserMessageInteraction](args.userMessageInteraction)
      .withName("fromAvro")
      .map(UserMessageInteraction.fromAvro)

    val userMessageInteractionHistory: SCollection[metrics.UserMessageInteractionHistory] =
      pipeline(userMessageInteractions).map(UserMessageInteractionHistory.toAvro)

    userMessageInteractionHistory.saveAsAvroFile(args.output)
    sc.run().waitUntilDone()
  }

  def pipeline(
    userMessageInteractions: SCollection[UserMessageInteraction]
  ): SCollection[UserMessageInteractionHistory] = {
    userMessageInteractions
      .keyBy(_.userId)
      .groupByKey
      .map {
        case (uid, interactions) =>
          UserMessageInteractionHistory(uid, interactions.toList.sortBy(_.partitionDate.toEpochDay))
      }
  }
}
