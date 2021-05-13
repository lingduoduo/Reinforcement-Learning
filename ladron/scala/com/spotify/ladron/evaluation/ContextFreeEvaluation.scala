package com.spotify.ladron.evaluation

import com.spotify.ladron.Coders._
import com.spotify.ladron.agent.model.ContextFreeModel
import com.spotify.ladron.agent.{Bandit, BanditPolicy}
import com.spotify.ladron.common.MessageChannel
import com.spotify.ladron.evaluation.Evaluation.{EvaluationContext, EvaluationResults}
import com.spotify.ladron.train.{Context, ContextFreeModelByContext}
import com.spotify.scio.values.{SCollection, SideInput}
import com.twitter.algebird.AveragedValue

object ContextFreeEvaluation {
  def pipeline(
    evaluations: SCollection[EvaluationContext],
    banditPolicy: BanditPolicy,
    bandits: SCollection[ContextFreeModelByContext]
  ): SCollection[EvaluationResults] = {
    val contextToBanditMap = bandits.map(bc => (bc.context, bc.bandit)).asMapSideInput
    val results = (1 to Evaluation.NumExperiments).map { _ =>
      simulate(evaluations, banditPolicy, contextToBanditMap)
    }

    SCollection
      .unionAll(results)
      .sumByKey // add up runs
      .map {
        case (channel, avg) =>
          val policyName = banditPolicy.toString
          EvaluationResults(channel, policyName, Evaluation.ContextFree, avg.count, avg.value)
      }
  }

  def simulate(
    evaluations: SCollection[EvaluationContext],
    banditPolicy: BanditPolicy,
    contextToBanditMap: SideInput[Map[Context, ContextFreeModel]]
  ): SCollection[(MessageChannel, AveragedValue)] = {
    evaluations
      .withSideInputs(contextToBanditMap)
      .map {
        case (evalContext, sideContext) =>
          val channel = MessageChannel.withName(evalContext.channel.toString)
          val context = Context(channel, evalContext.country.value)

          val banditsByContext = sideContext(contextToBanditMap)
          val banditModel = if (banditsByContext.contains(context)) {
            banditsByContext(context)
          } else {
            banditsByContext(Context(channel, Context.Global))
          }
          val bandit = Bandit(banditModel, banditPolicy)

          (evalContext, bandit.selectArm(evalContext.candidates))
      }
      .toSCollection
      .filter {
        case (evalContext, selectedArm) =>
          evalContext.sentCampaign.value == selectedArm.id
      }
      .keys
      .keyBy(_.channel)
      .mapValues(c => AveragedValue(Evaluation.rewardToCtr(c.reward)))
      .sumByKey
  }
}
