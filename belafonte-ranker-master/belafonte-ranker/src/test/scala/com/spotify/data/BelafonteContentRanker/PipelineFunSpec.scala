package com.spotify.data.BelafonteContentRanker

import com.spotify.scio.testing.JobTest.BeamOptions
import com.spotify.scio.testing.{PipelineTestUtils, SCollectionMatchers}
import org.scalatest.funspec.PathAnyFunSpec
import org.scalatest.matchers.must.Matchers

trait PipelineFunSpec
    extends PathAnyFunSpec
    with Matchers
    with SCollectionMatchers
    with PipelineTestUtils {

  private val beamOpts: BeamOptions = BeamOptions(opts = List())

  implicit def beamOptions: BeamOptions = beamOpts
}
