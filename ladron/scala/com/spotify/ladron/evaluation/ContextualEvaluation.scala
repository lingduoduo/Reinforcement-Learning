package com.spotify.ladron.evaluation

import com.spotify.ladron.Coders._
import com.spotify.ladron.agent._
import com.spotify.ladron.agent.model.ExampleModel
import com.spotify.ladron.common.MessageChannel
import com.spotify.ladron.evaluation.Evaluation.{EvaluationContext, EvaluationResults}
import com.spotify.scio.values.SCollection
import com.spotify.ladron.syntax.tensorflow._
import com.twitter.algebird.AveragedValue

object ContextualEvaluation {
  def pipeline(
    evaluations: SCollection[EvaluationContext],
    banditPolicy: BanditPolicy,
    modelUri: String
  ): SCollection[EvaluationResults] = {
    val results = (1 to Evaluation.NumExperiments).map { _ =>
      simulate(evaluations, banditPolicy, modelUri)
    }

    SCollection
      .unionAll(results)
      .sumByKey // add up runs
      .map {
        case (channel, avg) =>
          val policyName = banditPolicy.toString
          EvaluationResults(channel, policyName, Evaluation.Contextual, avg.count, avg.value)
      }
  }

  def simulate(
    evaluations: SCollection[EvaluationContext],
    banditPolicy: BanditPolicy,
    modelUri: String
  ): SCollection[(MessageChannel, AveragedValue)] = {
    evaluations
      .predictWithExample(modelUri) { (evalContext, runner) =>
        val bandit = Bandit(ExampleModel(runner), banditPolicy)
        (evalContext, bandit.selectArm(evalContext.candidates))
      }
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
