package com.spotify.ladron.util
import com.spotify.data.counters.{DataCountersOptions, DataCountersValidationContext}
import com.spotify.scio.{ScioContext, ScioResult}

trait ScioJobBase {
  val metricRegistry: MetricRegistry

  /* Validate and publish metrics */
  private def publishCounters(sc: ScioContext, sr: ScioResult): Unit = {
    import com.spotify.data.counters._
    sc.publishMetrics(sr)
  }

  private val DefaultPreOps: Seq[ScioContext => Unit] = Seq(
    metricRegistry.initMetrics
  )

  private val DefaultPostOps: Seq[(ScioContext, ScioResult) => Unit] = Seq(
    publishCounters,
    validateCounters
  )

  private def validateCounters(sc: ScioContext, sr: ScioResult): Unit = {
    val validationCtx = new DataCountersValidationContext(sc.optionsAs[DataCountersOptions], sr)
    validateCounters(validationCtx)
  }

  def validateCounters(ctx: DataCountersValidationContext): Unit = ()

  def context: ScioJobContext = ScioJobContext(
    preOps = DefaultPreOps,
    postOps = DefaultPostOps
  )

  def close(sc: ScioContext): Unit =
    context.close(sc)
}
