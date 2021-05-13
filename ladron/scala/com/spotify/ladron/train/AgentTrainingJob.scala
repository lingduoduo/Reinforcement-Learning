package com.spotify.ladron.train

import com.spotify.ladron.Coders._
import com.spotify.ladron.agent.model.{ContextFreeArm, ContextFreeModelMonoidAggregator}
import com.spotify.ladron.candidate.UserSelectionInfo
import com.spotify.ladron.reward
import com.spotify.ladron.reward.Reward
import com.spotify.ladron.syntax.agent.BanditSyntax
import com.spotify.ladron.util.{MetricRegistry, ScioJobBase}
import com.spotify.scio
import com.spotify.scio.ContextAndArgs
import com.spotify.scio.avro._
import com.spotify.multiple.syntax._
import com.spotify.scio.values.SCollection

/**
 * The job goes through the rewards and "trains" agents.
 * The job will create one agent per context, and will save each agent as json, each one in a
 * different path.
 */
object AgentTrainingJob extends ScioJobBase {
  override val metricRegistry: MetricRegistry =
    MetricRegistry.createMetricRegistry("bandit_training")

  final case class Args(
    rewards: List[String],
    output: String,
    userSelectionInfo: String,
    minimalPullsPerCountry: Integer
  )

  object Args {
    def parse(scioArgs: scio.Args): Args = Args(
      rewards = scioArgs.list("rewards"),
      output = scioArgs("output"),
      userSelectionInfo = scioArgs("userSelectionInfo"),
      minimalPullsPerCountry = scioArgs.int("minimalPullsPerCountry")
    )
  }

  def main(cmdArgs: Array[String]): Unit = {
    val (sc, scioArgs) = ContextAndArgs(cmdArgs)
    val args = Args.parse(scioArgs)

    val rewards = sc
      .avroFiles[reward.Reward.Avro](args.rewards)
      .withName("MapFromAvro")
      .map(Reward.fromAvro)

    val userSelectionInfo = sc
      .withName("ReadUserSelectionInfo")
      .avroFile[UserSelectionInfo.Avro](args.userSelectionInfo)
      .withName("MapFromAvro")
      .map(UserSelectionInfo.fromAvro)

    pipeline(rewards, userSelectionInfo, args.minimalPullsPerCountry)
      .saveBanditWithContext(args.output)

    close(sc)
  }

  def pipeline(
    rewards: SCollection[Reward],
    userSelectionInfo: SCollection[UserSelectionInfo],
    minimalPullsPerCountry: Integer
  ): SCollection[ContextFreeModelByContext] = {
    val banditWithContext = joinInteractionsWithContext(rewards, userSelectionInfo)
      .mapValues(reward => ContextFreeArm(reward.campaignId.value, reward.reward, 1.0))
      .aggregateByKey(new ContextFreeModelMonoidAggregator())
      .map(ContextFreeModelByContext.tupled)

    val globalBandits = banditWithContext
      .flatMap { bc =>
        bc.bandit.arms.values.map(arm => (Context(bc.context.channel, Context.Global), arm))
      }
      .aggregateByKey(new ContextFreeModelMonoidAggregator())
      .map(ContextFreeModelByContext.tupled)

    val filteredBandits = banditWithContext
      .filter { banditWithContext =>
        val pullsPerCountry =
          banditWithContext.bandit.arms.map { case (_, v) => v.pulls }.toList.sum
        pullsPerCountry >= minimalPullsPerCountry
      }
    SCollection.unionAll(Seq(globalBandits, filteredBandits))
  }

  def joinInteractionsWithContext(
    rewards: SCollection[Reward],
    userSelectionInfo: SCollection[UserSelectionInfo]
  ): SCollection[(Context, Reward)] = {
    val userToCountry = userSelectionInfo
      .map { info =>
        (info.userId, info.registrationCountry)
      }

    rewards
      .keyBy(x => x.userId)
      .leftOuterJoin(userToCountry)
      .values
      .flatMap {
        case (reward, countryOpt) => countryOpt.map(country => (reward, country))
      }
      .map {
        case (reward, country) => (Context(reward.channel, country), reward)
      }
  }
}
