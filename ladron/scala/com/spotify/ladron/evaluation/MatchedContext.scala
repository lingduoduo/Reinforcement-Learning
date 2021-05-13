package com.spotify.ladron.evaluation

import com.spotify.bcd.schemas.scala.NonEmptyString
import com.spotify.ladron.util.MetricRegistry
import com.spotify.ladron.tf.Encoding._
import magnolify.tensorflow.ExampleType
import org.apache.beam.sdk.metrics.Counter
import org.tensorflow.example.Example

case class MatchedContext(
  selection_id: NonEmptyString,
  campaign_id: NonEmptyString,
  country: NonEmptyString,
  context: Example
)

object MatchedContext {
  case class RequiredFeatures(
    selection_id: Option[NonEmptyString],
    campaign_id: Option[NonEmptyString],
    country: Option[NonEmptyString]
  )

  val requiredFeaturesExampleType: ExampleType[RequiredFeatures] =
    ExampleType[RequiredFeatures]

  val metricRegistry: MetricRegistry =
    MetricRegistry.createMetricRegistry("matchedContextMetrics")
  object Counters {
    val NoCampaignId: Counter = metricRegistry.counter("missing", "campaignId")
    val NoCountry: Counter = metricRegistry.counter("missing", "country")
    val NoSelectionId: Counter = metricRegistry.counter("missing", "selectionId")
  }

  def fromExample(example: Example): Option[MatchedContext] = {
    Option(requiredFeaturesExampleType.from(example))
      .filter(removeAndCountNones)
      .map(x => MatchedContext(x.selection_id.get, x.campaign_id.get, x.country.get, example))
  }

  private def removeAndCountNones(features: RequiredFeatures): Boolean = {
    var validRecord = true
    if (features.country.isEmpty) {
      validRecord = false
      Counters.NoCountry.inc()
    }
    if (features.campaign_id.isEmpty) {
      validRecord = false
      Counters.NoCampaignId.inc()
    }
    if (features.selection_id.isEmpty) {
      validRecord = false
      Counters.NoSelectionId.inc()
    }
    validRecord
  }
}
