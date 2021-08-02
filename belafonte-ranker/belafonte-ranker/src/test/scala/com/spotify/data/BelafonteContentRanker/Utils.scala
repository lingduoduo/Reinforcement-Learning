package com.spotify.data.BelafonteContentRanker

import com.spotify.data.BelafonteContentRanker.BelafonteContentRankerJob.Ranked
import com.spotify.scio.values.SCollection

object Utils {

  def checkOutput(actual: SCollection[Ranked], expected: Ranked): SCollection[Boolean] = {
    actual.map { a =>
      (a.rank == expected.rank) & (a.contentUri == expected.contentUri) &
        (a.score == expected.score) & (a.campaignName == expected.campaignName)
    }
  }
}
