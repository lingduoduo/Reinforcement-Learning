package com.spotify.ladron.util

import com.yammer.metrics.core.MetricsRegistry
import org.apache.beam.sdk.metrics.{Counter, Distribution, Gauge, Metric}

import com.spotify.bcd.schemas.scala.NonEmptyString
import com.spotify.scio.{ScioContext, ScioMetrics}

/**
 * This was designed to be a member in a Job object (either directly, or through a base class).
 * Jobs can register metrics under a specific namespace and provided metrics from other
 * registers.
 *
 * In composing Jobs, where each has their own registry, the main Job would register its own
 * metrics by calling one of the metric creation methods, and the other Jobs' metrics by
 * calling the register method.
 */
class MetricRegistry(namespace: String) extends Serializable {

  /**
   * Metrics registered in this registry.
   * (write protected by this.)
   */
  private var beamCounters: List[Counter] = List.empty
  private var beamDistributions: List[Distribution] = List.empty
  private var beamGauges: List[Gauge] = List.empty

  /**
   * MetricRegisters that should be aggregated into this registry.
   * NB this is lazy so that metrics registered after the registery was registered is picked up.
   * (write protected by this.)
   */
  private var registers: List[MetricRegistry] = List.empty

  protected def register(m: Distribution): Unit = this.synchronized {
    beamDistributions = m :: beamDistributions
  }
  protected def register(m: Counter): Unit = this.synchronized {
    beamCounters = m :: beamCounters
  }
  protected def register(m: Gauge): Unit = this.synchronized {
    beamGauges = m :: beamGauges
  }

  def register(m: MetricRegistry): Unit = this.synchronized {
    registers = m :: registers
  }

  def counter(prefix: String, name: String): Counter = {
    val counter = ScioMetrics.counter(namespace, CounterNames.name(namespace, prefix, name))
    register(counter)
    counter
  }

  def counter(prefix: String, name: String, postfix: String): Counter = {
    val counter = ScioMetrics.counter(
      namespace,
      CounterNames.name(namespace, prefix, name, postfix)
    )
    register(counter)
    counter
  }

  def distribution(prefix: String, name: String): Distribution = {
    val dist = ScioMetrics.distribution(namespace, CounterNames.name(namespace, prefix, name))
    register(dist)
    dist
  }

  def distribution(prefix: String, name: String, postfix: String): Distribution = {
    val dist = ScioMetrics.distribution(
      namespace,
      CounterNames.name(namespace, prefix, name, postfix)
    )
    register(dist)
    dist
  }

  def gauge(prefix: String, name: String): Gauge = {
    val gauge = ScioMetrics.gauge(
      namespace,
      CounterNames.name(namespace, prefix, name)
    )
    register(gauge)
    gauge
  }

  def gauge(prefix: String, name: String, postfix: String): Gauge = {
    val gauge = ScioMetrics.gauge(
      namespace,
      CounterNames.name(namespace, prefix, name, postfix)
    )
    register(gauge)
    gauge
  }

  private def allRegisters: List[MetricRegistry] = this :: registers

  private def allCounters: List[Counter] = allRegisters.flatMap(_.beamCounters)

  private def allDistributions: List[Distribution] = allRegisters.flatMap(_.beamDistributions)

  private def allGauges: List[Gauge] = allRegisters.flatMap(_.beamGauges)

  def initMetrics(sc: ScioContext): Unit = {
    val counters = allCounters
    val distributions = allDistributions
    val gauges = allGauges

    if (counters.nonEmpty || distributions.nonEmpty || gauges.nonEmpty) {
      sc.withName("InitMetrics")
        .parallelize(Seq(0))
        .map { _ =>
          counters.foreach(_.inc(0))
          // should be update(0, 0, 0, 0) but that can fail when sending to data-counters
          // (since 0/0 = NaN which fails when converting to BigDecimal through toString).
          distributions.foreach(_.update(0))
          gauges.foreach(_.set(0))
        }
    }
  }
}

object MetricRegistry {
  def createMetricRegistry(namespace: String): MetricRegistry =
    new MetricRegistry(NonEmptyString.fromAvro(namespace).value)
}
