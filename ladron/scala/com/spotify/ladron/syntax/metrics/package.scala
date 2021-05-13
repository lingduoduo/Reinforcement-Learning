package com.spotify.ladron.syntax

import org.apache.beam.sdk.metrics.{Counter, Distribution}

import com.spotify.scio.coders.Coder
import com.spotify.scio.values.SCollection

package object metrics {
  implicit class MetricsSyntax[A: Coder](self: SCollection[A]) {

    /**
     * Accumulate the count of the given collection in the
     * [[org.apache.beam.sdk.metrics.Counter r]]s.
     *
     * val all = ScioMetrics.counter[Long]("all")
     * sc.parallelize(1 to 100).accumulateCount(all)
     *
     * should return 100
     *
     */
    def accumulateCount(counters: Counter*): SCollection[A] =
      self.map { x =>
        counters.foreach(_.inc()); x
      }

    /**
     * Accumulate the count of entities in the given collection.
     *
     * val even = ScioMetrics.counter[Long]("even")
     * sc.parallelize(1 to 100).accumulateCount { x => ScioMetrics.counter[Long](s"n_$x") }
     *
     */
    def accumulateCount(counterFn: A => Counter): SCollection[A] =
      self.map { x =>
        counterFn(x).inc(); x
      }

    /**
     * Accumulate the count of entities in the given collection.
     *
     * val even = ScioMetrics.counter[Long]("even")
     * sc.parallelize(1 to 100).accumulateIf(_ % 2 == 0)(even)
     *
     * should return 50
     */
    def accumulateIf(predicate: A => Boolean)(counters: Counter*): SCollection[A] =
      self.map { x =>
        if (predicate(x)) counters.foreach(_.inc())
        x
      }

    /**
     * Decrease the count of the given collection in the
     * [[org.apache.beam.sdk.metrics.Counter]]s.
     *
     * val all = ScioMetrics.counter[Long]("all")
     * sc.parallelize(1 to 100).decreaseCount(all)
     *
     * should return -100
     *
     */
    def decreaseCount(counters: Counter*): SCollection[A] =
      self.map { x =>
        counters.foreach(_.dec()); x
      }

    /**
     * Treat distribution as a ratio, updating it with one for each value matching predicate a.
     *
     * val even = ScioMetrics.distribution[Long]("even")
     * sc.parallelize(1 to 100).accumulateRatio(_ % 2 == 0)
     *
     * should return even.mean 0.5
     */
    def accumulateBinaryRatio(distribution: Distribution)(a: A => Boolean): SCollection[A] =
      self.map { x =>
        distribution.update(ratioUpdate(a(x)))
        x
      }

    /**
     * Treat distribution as ratio, updating it with one for each value matching value in map
     *
     * val ones = ScioMetrics.distribution[Long]("ones")
     * val twos = ScioMetrics.distribution[Long]("twos")
     * val threes = ScioMetrics.distribution[Long]("threes")
     *
     * sc.parallelize(1 to 3).accumulateRatios(Map(1 -> ones, 2 -> twos, 3 -> threes))
     *
     * should return ones.mean 0.33, twos.mean 0.33, threes.mean 0.33
     */
    def accumulateBinaryRatios(distributions: Map[A, Distribution]): SCollection[A] =
      self.map { x =>
        distributions.foreach {
          case (key, distribution) => distribution.update(ratioUpdate(key == x))
        }
        x
      }
  }

  /**
   * Use distribution.mean as ratio of (match / (match + non match))
   * by updating with 1 when match and update with 0 other ways
   * (sum will be match and count will be (match + non match) and ratio = mean).
   */
  private def ratioUpdate[A](p: Boolean): Long = if (p) 1 else 0
}
