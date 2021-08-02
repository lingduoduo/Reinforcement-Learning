package com.spotify.data.BelafonteContentRanker

import com.spotify.scio.testing._
import com.spotify.data.BelafonteContentRanker.BelafonteContentRankerJob._
import com.spotify.data.BelafonteContentRanker.GoogleTrendTables._
import com.spotify.data.BelafonteContentRanker.KnowledgeGraphTables._
import com.spotify.data.BelafonteContentRanker.PerformanceTables._
import com.spotify.data.BelafonteContentRanker.Utils.checkOutput
import com.spotify.scio.bigquery._

class NoPerformanceDataTest extends PipelineSpec {
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

  val inputPerformance = List()

  //A with 1.99, then B compared is 1.08 (0.1 for the diversity score), then C with 1.17 (0.2 for
  // the diversity score
  val expected1 = Ranked(contentA.get, Brazil.get, 1, 1.99, "Editorial", "4")
  val expected2 = Ranked(contentB.get, Brazil.get, 3, 1.08, "Editorial", "4")
  val expected3 = Ranked(contentC.get, Brazil.get, 2, 1.17, "Editorial", "4")
  val dateStr = "20190902"
  //run the test job
  "BelafonteContentRankerJob" should "work" in {
    JobTest[BelafonteContentRankerJob.type]
      .args(
        "--outputbq=bq.dummy",
        s"--knowledge-graph-table=$knowledge_Graph$dateStr",
        "--performance-table=artist:link.table",
        s"--google-trends-table=$googleTrendsTable",
        "--date=2019-09-02"
      )
      .input(BigQueryIO(knowledge_Graph + dateStr), inputKnowledgeGraph)
      .input(BigQueryIO(PerformanceEntity.query.format(dateStr, dateStr)), inputPerformance)
      .input(BigQueryIO(googleTrendsTable), defaultGoogleTrends)
      //assert right elements in output
      .output(BigQueryIO[Ranked]("bq.dummy"))(actual =>
        checkOutput(actual.filter(_.contentUri == "contentA"), expected1)
      )
      .output(BigQueryIO[Ranked]("bq.dummy"))(actual =>
        checkOutput(actual.filter(_.contentUri == "contentB"), expected2)
      )
      .output(BigQueryIO[Ranked]("bq.dummy"))(actual =>
        checkOutput(actual.filter(_.contentUri == "contentC"), expected3)
      )
      //assert that the output contains the correct number of elements
      .run()
  }
}
