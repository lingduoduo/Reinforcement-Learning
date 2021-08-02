package com.spotify.data.BelafonteContentRanker

import com.spotify.scio.testing._
import com.spotify.data.BelafonteContentRanker.BelafonteContentRankerJob._
import com.spotify.scio.bigquery._
import com.spotify.data.BelafonteContentRanker.GoogleTrendTables._
import com.spotify.data.BelafonteContentRanker.KnowledgeGraphTables._
import com.spotify.data.BelafonteContentRanker.PerformanceTables._
import com.spotify.scio.bigquery.client.BigQuery
import com.spotify.data.BelafonteContentRanker.Utils.checkOutput
import org.joda.time.LocalDate

package object constants {
  val Brazil = Some("BR")
  val France = Some("FR")
  val USA = Some("US")
  val julTenth = Option(new LocalDate(2019, 7, 10))
  val julFifth = Option(new LocalDate(2019, 7, 5))
  val contentA = Some("contentA")
  val contentB = Some("contentB")
  val contentC = Some("contentC")
  val contentD = Some("contentD")
  val contentE = Some("contentE")
  val contentF = Some("contentF")
  val contentG = Some("contentG")
  val contentH = Some("contentH")
  val contentI = Some("contentI")
  val contentJ = Some("contentJ")
  val someTrue = Some(true)
  val someFalse = Some(false)
  val defaultGoogleTrends: List[GoogleTrend] = List()
  val latestTable: String = BigQuery
    .defaultInstance()
    .tables
    .tableReferences("acmacquisition", "google_trends_popularity")
    .map(_.getTableId)
    .max
  val googleTrendsTable = s"acmacquisition:google_trends_popularity.$latestTable"
  val knowledge_Graph = s"acmacquisition:content_knowledge_graph.content_knowledge_graph_"
}

//tests if the scorer correctly keeps all those of not minimum age
class MinimumAgeAdTest extends PipelineSpec {
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
      Some(275L),
      Some(5L),
      Option(new LocalDate(2019, 8, 30)),
      Some(50L),
      someTrue
    ),
    PerformanceEntity(
      contentB,
      Brazil,
      Some(125L),
      Some(275L),
      Some(5L),
      Option(new LocalDate(2019, 8, 25)), //"20190825"),
      Some(60L),
      someTrue
    ),
    PerformanceEntity(
      contentC,
      Brazil,
      Some(50L),
      Some(275L),
      Some(10L),
      Option(new LocalDate(2019, 8, 30)),
      Some(50L),
      someTrue
    )
  )

  val dateStr = "20190902"

  //keep C and A, so C,A,B or A,C,B -- B gets score of 0 since it's been used already
  val expectedOutput = Ranked("contentB", "BR", 3.0, 0.0, "Editorial", "5")
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
      .output(BigQueryIO[Ranked]("bq.dummy"))(actual => checkOutput(actual, expectedOutput))
      //assert that the output contains the correct number of elements
      .run()
  }
}

//tests if one already used is scored 0, and one not already used is not scored 0
class UsedNotUsedTest extends PipelineSpec {
  import constants._

  val inputKnowledgeGraph = List(
    NodeWithLinksEntity(
      campaign = "BR",
      popularity = 1.0,
      uri = "contentA",
      links = List()
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
    ),
    NodeWithLinksEntity(
      campaign = "BR",
      popularity = 4.0,
      uri = "contentC",
      links = List()
    )
  )

  val inputPerformance = List(
    PerformanceEntity(
      contentA,
      Brazil,
      Some(100L),
      Some(275L),
      Some(5L),
      julFifth,
      Some(90L),
      someTrue
    ),
    PerformanceEntity(
      contentB,
      Brazil,
      Some(125L),
      Some(275L),
      Some(5L),
      julFifth,
      Some(60L),
      someTrue
    ),
    PerformanceEntity(
      contentC,
      Brazil,
      Some(50L),
      Some(275L),
      Some(30L),
      julFifth,
      Some(50L),
      someTrue
    )
  )

  //keep B and A, lose C, score D (1 + .96 + .55)

  val expectedOutput1 =
    Ranked("contentC", "BR", 4.0, 0.0, "Editorial", "4")
  val expectedOutput2 =
    Ranked("contentD", "BR", 3.0, 2.52, "Editorial", "5")
  val dateStr = "20190917"

  //run the test job
  "BelafonteContentRankerJob" should "work" in {
    JobTest[BelafonteContentRankerJob.type]
      .args(
        "--outputbq=bq.dummy",
        s"--knowledge-graph-table=$knowledge_Graph$dateStr",
        "--performance-table=artist:link.table",
        s"--google-trends-table=$googleTrendsTable",
        "--date=2019-09-17"
      )
      .input(BigQueryIO(knowledge_Graph + dateStr), inputKnowledgeGraph)
      .input(BigQueryIO(PerformanceEntity.query.format(dateStr, dateStr)), inputPerformance)
      .input(BigQueryIO(googleTrendsTable), defaultGoogleTrends)
      //assert right elements in output
      .output(BigQueryIO[Ranked]("bq.dummy"))(actual =>
        checkOutput(actual.filter(_.contentUri == "contentC"), expectedOutput1)
      )
      .output(BigQueryIO[Ranked]("bq.dummy"))(actual =>
        checkOutput(actual.filter(_.contentUri == "contentD"), expectedOutput2)
      )
      //assert that the output contains the correct number of elements
      .run()
  }
}

//tests if friend is calculated correctly
class FriendDiscountTest extends PipelineSpec {
  import constants._

  val inputKnowledgeGraph = List(
    NodeWithLinksEntity(
      campaign = "BR",
      popularity = 1.0,
      uri = "contentA",
      links = List(Links$1("contentB", 1.0))
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
      Some(150L),
      Some(5L),
      julFifth,
      Some(90L),
      someTrue
    ),
    PerformanceEntity(
      contentC,
      Brazil,
      Some(50L),
      Some(150L),
      Some(30L),
      julFifth,
      Some(50L),
      someTrue
    )
  )

  //B is scored according to friendship with A -- so cpr is affected and friendscore is affected
  //avgSpendFrac is 35/150, adjust is .82 + .98 + .1 =

  val expectedOutput =
    Ranked("contentB", "BR", 2.0, 1.81, "Editorial", "4")

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
      .output(BigQueryIO[Ranked]("bq.dummy"))(actual => checkOutput(actual, expectedOutput))
      //assert that the output contains the correct number of elements
      .run()
  }
}

//tests if the estcprs are calculated correctly across regions
class estCprRegionTest extends PipelineSpec {
  import constants._

  val inputKnowledgeGraph = List(
    NodeWithLinksEntity(
      campaign = "BR",
      popularity = 1.0,
      uri = "contentA",
      links = List(Links$1("contentB", 1.0))
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
    ),
    NodeWithLinksEntity(
      campaign = "US",
      popularity = 1.0,
      uri = "contentA",
      links = List(Links$1("contentB", 1.0))
    ),
    NodeWithLinksEntity(
      campaign = "US",
      popularity = 2.0,
      uri = "contentB",
      links = List()
    ),
    NodeWithLinksEntity(
      campaign = "US",
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
      Some(150L),
      Some(1L),
      julFifth,
      Some(90L),
      someTrue
    ),
    PerformanceEntity(
      contentC,
      Brazil,
      Some(50L),
      Some(150L),
      Some(30L),
      julFifth,
      Some(50L),
      someTrue
    ),
    PerformanceEntity(
      contentA,
      USA,
      Some(70L),
      Some(150L),
      Some(10L),
      julFifth,
      Some(90L),
      someTrue
    ),
    PerformanceEntity(
      contentC,
      USA,
      Some(80L),
      Some(150L),
      Some(40L),
      julFifth,
      Some(50L),
      someTrue
    )
  )

  val expectedOutputBR =
    Ranked("contentB", "BR", 2.0, 1.81, "Editorial", "4")

  val expectedOutputUS =
    Ranked("contentB", "US", 2.0, 1.35, "Editorial", "5")

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
      .output(BigQueryIO[Ranked]("bq.dummy"))(actual => checkOutput(actual, expectedOutputUS))
      //assert that the output contains the correct number of elements
      .run()
  }
}

//tests if the pipeline can correctly remove 2 ads for a region with lots of ads
class variableWorstNumByRegionTest extends PipelineSpec {
  import constants._

  //two for brazil (1 replace), 10 for France (2 replace)
  val inputKnowledgeGraph = List(
    NodeWithLinksEntity(
      campaign = "BR",
      popularity = 1.0,
      uri = "contentA",
      links = List()
    ),
    NodeWithLinksEntity(
      campaign = "BR",
      popularity = 2.0,
      uri = "contentB",
      links = List()
    ),
    NodeWithLinksEntity(
      campaign = "FR",
      popularity = 1.0,
      uri = "contentA",
      links = List()
    ),
    NodeWithLinksEntity(
      campaign = "FR",
      popularity = 2.0,
      uri = "contentB",
      links = List()
    ),
    NodeWithLinksEntity(
      campaign = "FR",
      popularity = 3.0,
      uri = "contentC",
      links = List()
    ),
    NodeWithLinksEntity(
      campaign = "FR",
      popularity = 4.0,
      uri = "contentD",
      links = List()
    ),
    NodeWithLinksEntity(
      campaign = "FR",
      popularity = 5.0,
      uri = "contentE",
      links = List()
    ),
    NodeWithLinksEntity(
      campaign = "FR",
      popularity = 6.0,
      uri = "contentF",
      links = List()
    ),
    NodeWithLinksEntity(
      campaign = "FR",
      popularity = 7.0,
      uri = "contentG",
      links = List()
    ),
    NodeWithLinksEntity(
      campaign = "FR",
      popularity = 8.0,
      uri = "contentH",
      links = List()
    ),
    NodeWithLinksEntity(
      campaign = "FR",
      popularity = 9.0,
      uri = "contentI",
      links = List()
    ),
    NodeWithLinksEntity(
      campaign = "FR",
      popularity = 10.0,
      uri = "contentJ",
      links = List()
    )
  )

  val inputPerformance = List(
    //two for Brazil, the worst one is A
    PerformanceEntity(
      contentA,
      Brazil,
      Some(100L),
      Some(120L),
      Some(5L),
      julTenth,
      Some(50L),
      someTrue
    ),
    PerformanceEntity(
      contentB,
      Brazil,
      Some(125L),
      Some(130L),
      Some(5L),
      julFifth,
      Some(30L),
      someTrue
    ),
    //10 for France, worst ones are I and J
    PerformanceEntity(
      contentA,
      France,
      Some(100L),
      Some(120L),
      Some(5L),
      julTenth,
      Some(50L),
      someTrue
    ),
    PerformanceEntity(
      contentB,
      France,
      Some(125L),
      Some(130L),
      Some(5L),
      julFifth,
      Some(60L),
      someTrue
    ),
    PerformanceEntity(
      contentC,
      France,
      Some(125L),
      Some(130L),
      Some(5L),
      julFifth,
      Some(60L),
      someTrue
    ),
    PerformanceEntity(
      contentD,
      France,
      Some(125L),
      Some(130L),
      Some(5L),
      julFifth,
      Some(60L),
      someTrue
    ),
    PerformanceEntity(
      contentE,
      France,
      Some(100L),
      Some(120L),
      Some(5L),
      julTenth,
      Some(50L),
      someTrue
    ),
    PerformanceEntity(
      contentF,
      France,
      Some(125L),
      Some(130L),
      Some(5L),
      julFifth,
      Some(60L),
      someTrue
    ),
    PerformanceEntity(
      contentG,
      France,
      Some(125L),
      Some(130L),
      Some(5L),
      julFifth,
      Some(60L),
      someTrue
    ),
    PerformanceEntity(
      contentH,
      France,
      Some(125L),
      Some(130L),
      Some(5L),
      julFifth,
      Some(60L),
      someTrue
    ),
    PerformanceEntity(
      contentI,
      France,
      Some(125L),
      Some(130L),
      Some(5L),
      julFifth,
      Some(20L),
      someTrue
    ),
    PerformanceEntity(
      contentJ,
      France,
      Some(125L),
      Some(130L),
      Some(5L),
      julFifth,
      Some(30L),
      someTrue
    )
  )
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
      .output(BigQueryIO[Ranked]("bq.dummy")) { output =>
        //France size 2 with score 0
        output.filter(_.campaignName == "FR").filter(_.score == 0.0) should haveSize(2)
        //Brazil size 1 with score 0
        output.filter(_.campaignName == "BR").filter(_.score == 0.0) should haveSize(1)
      }
      .run()
  }
}
