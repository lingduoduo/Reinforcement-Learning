package com.spotify.data.BelafonteContentRanker

import org.joda.time.{LocalDate => JodaDateTime}
import org.joda.time.format.ISODateTimeFormat
import com.spotify.data.BelafonteContentRanker.GoogleTrendTables._
import com.spotify.data.BelafonteContentRanker.KnowledgeGraphTables._
import com.spotify.data.BelafonteContentRanker.PerformanceTables._
import com.spotify.data.counters._
import com.spotify.scio.bigquery._
import com.spotify.scio.bigquery.client.BigQuery
import com.spotify.scio.values.SCollection
import com.spotify.scio.{ScioMetrics, _}
import org.slf4j.LoggerFactory

import scala.annotation.tailrec
import scala.util.Random
import org.joda.time.Days

/*
   run with (line breaks need to be removed):
  $ sbt
  > project belafonte-ranker
  > runMain com.spotify.data.BelafonteContentRanker.BelafonteContentRankerJob
  --project=acmacquisition
  --runner=DataflowRunner
  --region=europe-west1
  --outputbq=acmacquisition:belafonte.test1
  --knowledge-graph-table=acmacquisition:content_knowledge_graph.content_knowledge_graph_20200531
  --date=2020-05-31
  --google-trends-table=google_trends_popularity.google_trends_popularity_20200531
  --tempLocation=gs://belafonte/dataflow/tmp
  --dataEndpoint=BelafonteContentRankerJob
  --dataPartition=2020-05-31
 */

//   scalastyle:off method.length
object BelafonteContentRankerJob {

  type NodeUri = String
  //set up data counters to track performance
  val performanceCounter = ScioMetrics.counter("performance-counter")
  val knowledgeGraphNodeCounter = ScioMetrics.counter("node-counter")
  val totalSpendCounter = ScioMetrics.counter("total-spend-counter")
  val obsSpendFracMetrics = ScioMetrics.distribution("obsSpendFrac-dist")
  val estSpendFracMetrics = ScioMetrics.distribution("estSpendFrac-dist")
  val campaignZeroSpendCounter = ScioMetrics.counter("zeroSpend-counter")
  val MAX_SCORE: Double = 3.0
  val MIN_SCORE: Double = 0.0
  private val logger = LoggerFactory.getLogger(this.getClass)

  val theme_variation: Map[String, List[String]] = Map("Editorial" -> List("3b", "4", "5"))
  val theme_variation_seq: Seq[(String, String)] = flattenTemplateMap(theme_variation)

  def main(cmdlineArgs: Array[String]): Unit = {
    val (sc, args) = ContextAndArgs(cmdlineArgs)


    val dateFormatter = ISODateTimeFormat.date()
    val outputBq = args("outputbq")
    val knowledgeGraphTable = args("knowledge-graph-table")
    val today = JodaDateTime.parse(args("date"), dateFormatter)

    val todayString = args("date").replace("-", "")
    val random = new Random()

    //read in knowledge graph, will key by campaign and content id
    val knowledgeGraph = sc
      .typedBigQueryStorage[NodeWithLinksEntity](
        Table.Spec(knowledgeGraphTable)
      )
      .map { f =>
        //increment node counter
        knowledgeGraphNodeCounter.inc()
        getNodeWithLinks(f)
      }

    //read in performance data, will key by campaign and content id
    val performance = sc
      .typedBigQuery[PerformanceEntity](
        Query(PerformanceEntity.query.format(todayString, todayString))
      )
      .filter { f =>
        if (f.total_spend_while_live.get == 0.0) {
          campaignZeroSpendCounter.inc()
        }
        f.total_spend_while_live.get > 0.0
      }
      .map { f =>
        //increment performance row counter
        performanceCounter.inc()
        //update obsSpendFrac with spend/totSpend
        obsSpendFracMetrics.update(
          (math.ceil(100 * f.spend.get)).toLong /
            (math.ceil(f.total_spend_while_live.get)).toLong
        )
        //update totalSpend with spend
        totalSpendCounter.inc(math.ceil(f.spend.get).toLong)
        getPerformance(f)

      }

    //read in google trends data,  will key by campaign and content id
    val latestTable: String = BigQuery
      .defaultInstance()
      .tables
      .tableReferences("acmacquisition", "google_trends_popularity")
      .map(_.getTableId)
      .max
    val googleTrendsTable = s"acmacquisition:google_trends_popularity.$latestTable"
    val googleTrendsData = sc
      .typedBigQueryStorage[GoogleTrendEntity](Table.Spec(googleTrendsTable))
      .map(getGoogleTrend)

    joinedPipeline(knowledgeGraph, googleTrendsData, performance)
      .groupBy(_.performance.campaign)
      .flatMap {
        case (campaignName, contentInfos) =>
          // get estimated cpr per piece of content by applying observed performance to unobserved
          // via the knowledge graph
          val performance: Map[NodeUri, Double] = computeEstimatedPerformance(contentInfos)
          val popularities: Map[NodeUri, Double] = computePopularities(contentInfos.map(_.relation))

          val activeAds = contentInfos.filter(_.performance.isActive)
          val worstAds = findWorst(activeAds.map(_.performance), today).toSet

          val referencePoolTot = activeAds
            .filter(c => !worstAds(c.uri))

          val referenceFriends = referencePoolTot
            .flatMap(c => c.relation.links)

          val referencePool = referencePoolTot
            .map(ci => ItemScore(ci.uri, MAX_SCORE))
            .toList

          val candidates = contentInfos
            .filter(c => !referencePool.map(_.uri).contains(c.uri))
            .filter(c => !worstAds(c.uri))

          val selectedPool =
            fillOutPool(candidates, referencePool, performance, popularities, referenceFriends) ++
              worstAds.map(uri => ItemScore(uri, MIN_SCORE))

          selectedPool
            .sortBy(-_.score)
            .zipWithIndex
            .map {
              case (score, rank) =>
                // adding random template
                val theme_variation_tuple =
                  theme_variation_seq(random.nextInt(theme_variation_seq.length))
                Ranked(
                  score.uri,
                  campaignName,
                  rank + 1,
                  score.score,
                  theme_variation_tuple._1,
                  theme_variation_tuple._2
                )
            }
      }
      .saveAsTypedBigQueryTable(
        Table.Spec(outputBq),
        writeDisposition = WRITE_EMPTY,
        createDisposition = CREATE_IF_NEEDED
      )

    sc.runAndPublishMetrics()
  }

  def flattenTemplateMap(templates: Map[String, List[String]]): Seq[(String, String)] = {
    templates
      .map {
        case (theme, variations) => {
          variations.map(v => (theme, v))
        }
      }
      .flatten
      .toSeq
  }

  def joinedPipeline(
    rawKnowledgeGraph: SCollection[KnowledgeGraphNode],
    rawGoogleTrends: SCollection[GoogleTrend],
    rawPerformance: SCollection[PerformanceData]
  ): SCollection[ContentInfo] = {
    rawKnowledgeGraph
      .keyBy(node => (node.campaign, node.nodeUri))
      .leftOuterJoin(
        rawGoogleTrends.keyBy(trend => (trend.campaign, trend.contentUri))
      )
      .map {
        //if observed google trends for this node, replace graphNode.popularity with trends
        // popularity
        case ((campaign, nodeUri), (graphNode, Some(google))) =>
          ((campaign, nodeUri), graphNode.copy(popularity = google.popularity))
        case ((campaign, nodeUri), (graphNode, None)) =>
          ((campaign, nodeUri), graphNode)
      }
      .leftOuterJoin(rawPerformance.keyBy(perf => (perf.campaign, perf.contentUri)))
      .map {
        //if observed performance for this node, create content info with that observed spendfrac
        case ((campaign, nodeUri), (graphNode, Some(perf))) =>
          ContentInfo(nodeUri, campaign, perf, graphNode)
        //else no observed performance, create content info object with performance initialized
        //as 0 to fill in using updates from knowledge graph
        case ((campaign, nodeUri), (graphNode, None)) =>
          val performance = PerformanceData(nodeUri, campaign, 0.0, 0.0, 0.0, false, 0.0, None, 0.0)
          ContentInfo(nodeUri, campaign, performance, graphNode)
      }
  }

  @tailrec
  def fillOutPool(
    candidates: Iterable[ContentInfo],
    referencePool: List[ItemScore],
    performance: Map[NodeUri, Double],
    popularities: Map[NodeUri, Double],
    referenceFriends: Iterable[Link]
  ): List[ItemScore] = {
    if (candidates.nonEmpty) {
      val bestCandidate = candidates
        .map(ci =>
          (
            ItemScore(
              ci.uri,
              computeScoreAgainstPool(
                ci,
                referencePool,
                performance,
                popularities,
                referenceFriends
              )
            ),
            ci.relation.links
          )
        )
        .maxBy(_._1.score)

      val newCandidates = candidates.filter(!_.uri.equals(bestCandidate._1.uri))
      val newPool = referencePool ++ List(bestCandidate._1)
      val newFriends = referenceFriends ++ bestCandidate._2

      fillOutPool(newCandidates, newPool, performance, popularities, newFriends)
    } else {
      referencePool
    }
  }

  def computeScoreAgainstPool(
    item: ContentInfo,
    pool: List[ItemScore],
    performance: Map[NodeUri, Double],
    popularity: Map[NodeUri, Double],
    referenceFriends: Iterable[Link]
  ): Double = {
    val poolUris = pool.map(_.uri).toSet

    // We use the (max strength / 10) from all the friends of the items in the pool or 1
    // if there's none
    val diversityScore: Double = referenceFriends
      .filter(n => n.toUri == item.uri)
      .map(_.strength / 10)
      .reduceOption(_ min _)
      .getOrElse(1)

    math.round(100 * (diversityScore + performance(item.uri) + popularity(item.uri))) / 100.0
  }

  def computePopularities(items: Iterable[KnowledgeGraphNode]): Map[NodeUri, Double] = {
    items
      .map(c => (c.nodeUri, (100.0 - c.popularity) / 100.0))
      .toMap
  }

  def computeEstimatedPerformance(contentInfos: Iterable[ContentInfo]): Map[NodeUri, Double] = {
    val avgCampaignSpend = computeAverageCampaignSpend(contentInfos.map(_.performance))

    val neighbourEcfs: Map[NodeUri, Double] = contentInfos
      .filter(_.performance.spend > 0)
      .flatMap(c =>
        getNeighboursEcf(c.performance.estSpendFrac, avgCampaignSpend, c.relation.links)
      )
      .groupBy(_.uri)
      .mapValues(ecfs => ecfs.map(_.ecf).sum / ecfs.size)

    val unnormalizedEcfs = contentInfos.map {
      case contentInfo if contentInfo.performance.spend > 0 =>
        (contentInfo.uri, contentInfo.performance.estSpendFrac)

      case contentInfo if neighbourEcfs.contains(contentInfo.uri) =>
        (contentInfo.uri, neighbourEcfs(contentInfo.uri))

      case contentInfo =>
        (contentInfo.uri, avgCampaignSpend)
    }.toMap

    val (ecfMax, ecfMin) = (unnormalizedEcfs.values.max, unnormalizedEcfs.values.min)

    unnormalizedEcfs
      .mapValues(ecf => (ecf - ecfMin) / (ecfMax - ecfMin))
      .mapValues(ecf => if (ecf.isNaN) 0 else ecf) // cold start case, when ☝️ it's 0/0
  }

  def computeAverageCampaignSpend(performances: Iterable[PerformanceData]): Double = {
    val totalCampaignSpend = performances.map(_.totalSpend).sum
    if (totalCampaignSpend == 0) 0 else performances.map(_.spend).sum / totalCampaignSpend
  }

  case class NeighbourEcf(uri: NodeUri, ecf: Double)

  //given a node and it's est spendfrac and its' links, construct list of updates to its links
  def getNeighboursEcf(
    estSpendFrac: Double,
    avgSpendFrac: Double,
    links: Array[Link]
  ): Array[NeighbourEcf] = {
    links.map {
      case Link(toUri, strength) =>
        //adjust away from avgCPR towards the observed node, with multiplyer of
        // (10 - toNode.strength) / 20.0
        //so for first friend, 9/20, for 10th friend, no adjust
        val newEcf = avgSpendFrac + (estSpendFrac - avgSpendFrac) * (10 - strength) / 20.0
        NeighbourEcf(toUri, newEcf)
    }
  }

  def findWorst(activeAds: Iterable[PerformanceData], today: JodaDateTime): Iterable[NodeUri] = {
    //get number of running ads in that campaign
    val numRunningAds = activeAds.size
    //get number to remove (round down from 20% of candidates -- so up to 9 , remove 1 --
    // 10-14 -- remove 2, 15-20, remove 3, etc.
    val numRemoval = math.max(1, math.floor(numRunningAds * 0.2).toInt)

    //filter for just ones more than 5 days old
    activeAds
      .filter { candidateContent =>
        val dayGot = candidateContent.contentLive.get
        val dayDiff = Days.daysBetween(dayGot, today).getDays()
        dayDiff >= 5
      }
      //take lowest numRemoval lastSpend candidates
      .toList
      .sortBy(_.lastSpend)
      .take(numRemoval)
      .map(_.contentUri)
  }

  //includes performance data (observed or estimated) and knowledge graph data for a piece of
  // content
  case class ContentInfo(
    uri: String,
    campaign: String,
    performance: PerformanceData,
    relation: KnowledgeGraphNode
  )

  case class ItemScore(uri: String, score: Double)

  //class for data storage at end
  @BigQueryType.toTable
  @description("{ policy: { accessTier: BROAD }, description: Ranked  Content }")
  case class Ranked(
    contentUri: String,
    campaignName: String,
    rank: Double,
    score: Double,
    theme: String,
    variation: String
  )
}
