package com.spotify.data.BelafonteContentRanker

import com.spotify.scio.bigquery._

object GoogleTrendTables {
  //Google trends readin query
  @BigQueryType.fromStorage(
    "acmacquisition:google_trends_popularity.google_trends_popularity_%s",
    args = List("$LATEST"),
    selectedFields = List("campaignName", "popularity", "artist_uri")
  )
  class GoogleTrendEntity

  //google trends data
  case class GoogleTrend(
    contentUri: String,
    campaign: String,
    popularity: Double
  )

  //google trends data stored in a GoogleTrend object
  def getGoogleTrend(row: GoogleTrendEntity): GoogleTrend = {
    row match {
      case GoogleTrendEntity(
          Some(campaignName),
          Some(popularity),
          Some(artist_uri)
          ) =>
        GoogleTrend(contentUri = artist_uri, campaign = campaignName, popularity = popularity)
    }
  }
}
