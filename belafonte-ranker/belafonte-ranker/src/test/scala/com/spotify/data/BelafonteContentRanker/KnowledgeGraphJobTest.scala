package com.spotify.data.BelafonteContentRanker

import com.spotify.data.BelafonteContentRanker.BelafonteContentRankerJob._
import com.spotify.data.BelafonteContentRanker.GoogleTrendTables._
import com.spotify.data.BelafonteContentRanker.KnowledgeGraphTables._
import com.spotify.data.BelafonteContentRanker.PerformanceTables._
import com.spotify.data.BelafonteContentRanker.Utils.checkOutput
import com.spotify.scio.bigquery._
import com.spotify.scio.testing._

//tests if the scorer keeps separate regions separate and test empty knowledgegraph
class EmptyKnowledgeGraphMultipleRegionTest extends PipelineSpec {
  import constants._

  val inputKnowledgeGraph = List(
    NodeWithLinksEntity(
      campaign = "BR",
      popularity = 1.0,
      uri = "contentA",
      links = List(Links$1("contentB", 1.0), Links$1("contentC", 2.0))
    ),
    NodeWithLinksEntity(
      campaign = "BR",
      popularity = 2.0,
      uri = "contentB",
      links = List()
    ),
    NodeWithLinksEntity(
      campaign = "BR",
      popularity = 3.0,
      uri = "contentC",
      links = List()
    )
  )

  val inputPerformance = List(
    PerformanceEntity(
      contentA,
      Brazil,
      Some(100L),
      Some(120L),
      Some(5L),
      julTenth,
      Some(50L),
      someFalse
    ),
    PerformanceEntity(
      contentB,
      France,
      Some(125L),
      Some(130L),
      Some(5L),
      julTenth,
      Some(60L),
      someFalse
    ),
    PerformanceEntity(
      contentC,
      USA,
      Some(50L),
      Some(70L),
      Some(10L),
      julFifth,
      Some(50L),
      someFalse
    )
  )

  val expectedOutput1 = Ranked("contentA", "BR", 1.0, 1.99, "Editorial", "4") // Diversity=1,
  // ECF=0, Popularity=.99
  val expectedOutput2 = Ranked("contentB", "FR", 1.0, 1.98, "Editorial", "4") // Diversity=1,
  // ECF=0, Popularity=.98
  val expectedOutput3 = Ranked("contentC", "US", 1.0, 1.97, "Editorial", "4") // Diversity=1,
  // ECF=0, Popularity=.97
  val dateStr = "20190717"

  //run the test job
  "BelafonteContentRankerJob" should "work" in {
    JobTest[BelafonteContentRankerJob.type]
      .args(
        "--outputbq=bq.dummy",
        s"--knowledge-graph-table=$knowledge_Graph$dateStr",
        "--performance-table=artist:link.table",
        s"--google-trends-table=$googleTrendsTable",
        "--date=2019-07-17"
      )
      .input(BigQueryIO(knowledge_Graph + dateStr), inputKnowledgeGraph)
      .input(BigQueryIO(PerformanceEntity.query.format(dateStr, dateStr)), inputPerformance)
      .input(BigQueryIO(googleTrendsTable), defaultGoogleTrends)
      //assert right elements in output
      .output(BigQueryIO[Ranked]("bq.dummy"))(actual =>
        checkOutput(actual.filter(_.contentUri == "contentA"), expectedOutput1)
      )
      .output(BigQueryIO[Ranked]("bq.dummy"))(actual =>
        checkOutput(actual.filter(_.contentUri == "contentB"), expectedOutput2)
      )
      .output(BigQueryIO[Ranked]("bq.dummy"))(actual =>
        checkOutput(actual.filter(_.contentUri == "contentC"), expectedOutput3)
      )
      //assert that the output contains the correct number of elements
      .run()
  }
}
