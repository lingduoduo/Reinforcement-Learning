package com.spotify.ladron.util
import com.google.common.annotations.VisibleForTesting

import com.spotify.scio.{ScioContext, ScioResult}

final case class ScioJobContext private (
  preOps: Seq[ScioContext => Unit],
  postOps: Seq[(ScioContext, ScioResult) => Unit]
) {

  /**
   * Runs all pre ops with the [[ScioContext]] before running the Job with
   * [[ScioContext.close().waitUntilDone()]].
   * When the Job finishes, both the [[ScioContext]] and [[ScioResult]] are used to execute all
   * post ops.
   */
  def close(sc: ScioContext): Unit = {
    preOps.foreach(fn => fn(sc))
    val scioResult = sc.run().waitUntilDone()
    postOps.foreach(fn => fn(sc, scioResult))
  }

  /**
   * Run the pre-ops of this context.
   * Should only be used in testing to initialize counters.
   */
  @VisibleForTesting
  def pre(sc: ScioContext): Unit =
    preOps.foreach(fn => fn(sc))
}
