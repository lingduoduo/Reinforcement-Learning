package com.spotify.data.BelafonteContentRanker

import org.joda.time.LocalDate
import com.spotify.data.BelafonteContentRanker.BelafonteContentRankerJob._
import com.spotify.data.BelafonteContentRanker.PerformanceTables._
import com.spotify.data.BelafonteContentRanker.KnowledgeGraphTables._
import com.spotify.data.BelafonteContentRanker.GoogleTrendTables._

class BelafonteUnitTest extends PipelineFunSpec {

  // scalastyle:off parameter.number
  // scalastyle:off line.size.limit
  describe("Belafonte Ranker Job Test") {
    def performanceData(
      uri: String,
      campaign: String = "US",
      regs: Double = 0,
      spend: Double = 10,
      totalSpend: Double = 30,
      isActive: Boolean = false,
      estSpendFrac: Double = 0.3,
      contentLive: Option[LocalDate] = Some(new LocalDate(2019, 10, 1)),
      lastSpend: Double = 10
    ): PerformanceData =
      PerformanceData(
        uri,
        campaign,
        regs,
        spend,
        totalSpend,
        isActive,
        estSpendFrac,
        contentLive,
        lastSpend
      )

    val octoberFirst = Some(new LocalDate(2019, 10, 1))
    val inputPerformance = Iterable(
      performanceData(
        uri = "uri1",
        spend = 10.0,
        totalSpend = 100.0,
        isActive = true,
        contentLive = octoberFirst,
        lastSpend = 27.5
      ),
      performanceData(
        uri = "uri2",
        spend = 20.0,
        totalSpend = 200.0,
        isActive = true,
        contentLive = octoberFirst,
        lastSpend = 20.4
      ),
      performanceData(
        uri = "uri3",
        spend = 30.0,
        totalSpend = 300.0,
        isActive = true,
        contentLive = octoberFirst,
        lastSpend = 39.9
      ),
      performanceData(
        uri = "uri4",
        spend = 40.0,
        totalSpend = 400.0,
        isActive = true,
        contentLive = octoberFirst,
        lastSpend = 81.8
      ),
      performanceData(
        uri = "uri5",
        spend = 50.0,
        totalSpend = 500.0,
        isActive = true,
        contentLive = Some(new LocalDate(2019, 10, 21)),
        lastSpend = 20.3
      ),
      performanceData(
        uri = "uri6",
        spend = 60.0,
        totalSpend = 600.0,
        isActive = true,
        contentLive = Some(new LocalDate(2019, 10, 23)),
        lastSpend = 24.9
      ),
      performanceData(
        uri = "uri7",
        spend = 70.0,
        totalSpend = 700.0,
        isActive = true,
        contentLive = Some(new LocalDate(2019, 10, 25)),
        lastSpend = 0.23
      ),
      performanceData(
        uri = "uri8",
        spend = 80.0,
        totalSpend = 801.0,
        isActive = true,
        contentLive = Some(new LocalDate(2019, 10, 28)),
        lastSpend = 64.7
      )
    )
    val neighbors = Array(Link("uri2", 1.0), Link("uri3", 2.0))
    val inputKnowledgeGraph = Iterable(
      KnowledgeGraphNode("uri1", "US", 1.0, neighbors)
    )
    val inputGoogleTrends = Iterable(
      GoogleTrend("uri2", "US", 2L)
    )

    it("joinedPipeline should correctly joined knowledgeGraph, performance, and googleTrends") {
      val (_, result) = runWithLocalOutput { sc =>
        BelafonteContentRankerJob.joinedPipeline(
          sc.parallelize(inputKnowledgeGraph),
          sc.parallelize(inputGoogleTrends),
          sc.parallelize(inputPerformance)
        )
      }
      result.size mustBe 1
    }

    it("computePopularities should correctly compute popularity scores based on KnowledgeGraph") {
      val result = computePopularities(inputKnowledgeGraph)
      val expected = Map("uri1" -> 0.99)
      result mustBe expected
    }

    it("computeEstimatedPerformance should correctly compute an output") {
      val (_, contentInfos) = runWithLocalOutput { sc =>
        BelafonteContentRankerJob.joinedPipeline(
          sc.parallelize(inputKnowledgeGraph),
          sc.parallelize(inputGoogleTrends),
          sc.parallelize(inputPerformance)
        )
      }
      val result = computeEstimatedPerformance(contentInfos)
      val expected = Map("uri1" -> 0.0)
      result mustBe expected
    }

    it("computeAverageCampaignSpend should correctly compute an average") {
      val result = computeAverageCampaignSpend(inputPerformance)
      result mustBe (360.0 / 3601.0)
    }

    it("getNeighboursEcf should correctly compute expected spend fraction based on neighbours") {
      val result = getNeighboursEcf(estSpendFrac = 0.5, avgSpendFrac = 0.3, links = neighbors)
      val expected = Array(NeighbourEcf("uri2", 0.39), NeighbourEcf("uri3", 0.38))
      result mustBe expected
    }

    it("findWorst should replace the worst ad in the real data") {
      val result = findWorst(inputPerformance, new LocalDate(2019, 10, 29))
      result.size mustBe 1
      result.head mustBe "uri5"
    }
  }
}
