package com.spotify.data.BelafonteContentRanker

import org.joda.time.LocalDate
import com.spotify.scio.bigquery._
import org.apache.beam.vendor.bytebuddy.v1_10_8.net.bytebuddy.asm.Advice.Local

object PerformanceTables {
  //Performance data reading query
  @BigQueryType.fromQuery(
    """
      |#standardSQL
      |
      |select
      |content_uri,
      |campaign_name,
      |sum(spend) as spend,
      |sum(distinct total_spend_that_day) as total_spend_while_live,
      |sum(regs) as regs,
      |min(live_date) as content_live_date,
      |last_spend as last_spend,
      |case when max(table_suffix) = '%s' then True else False end as is_active
      |from (
      |select
      |  content_uri,
      |  campaign_name,
      |  spend,
      |  regs,
      |  live_date,
      |  _TABLE_SUFFIX AS table_suffix,
      |LAST_VALUE(spend)
      |over (PARTITION BY content_uri, campaign_name ORDER BY _TABLE_SUFFIX asc
      |ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING
      |) as last_spend,
      |sum(spend) over (partition by _TABLE_SUFFIX, campaign_name
      |ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) as total_spend_that_day
      |from
      |`acmacquisition.zissou_belafonte_metrics.zissou_belafonte_metrics_*`
      |where _TABLE_SUFFIX <= '%s'
      |)
      |group by
      |content_uri,
      |campaign_name,
      |last_spend
    """.stripMargin,
    "20190719",
    "20190719"
  )
  class PerformanceEntity

  //performance data collected from facebook and display campaigns.
  case class PerformanceData(
    contentUri: String,
    campaign: String,
    regs: Double,
    spend: Double,
    totalSpend: Double,
    isActive: Boolean,
    estSpendFrac: Double,
    contentLive: Option[LocalDate],
    lastSpend: Double
  )

  //performance data stored in a performance data object
  def getPerformance(row: PerformanceEntity): PerformanceData = {
    val contentUri = row.content_uri.getOrElse("")
    val campaign_name = row.campaign_name.getOrElse("")
    val regs = row.regs.get
    val spend = row.spend.get
    val totalSpend = row.total_spend_while_live.get
    val isActive = row.is_active.getOrElse(false)
    val contentLiveDate: Option[LocalDate] = row.content_live_date
    val lastSpend = row.last_spend.get

    PerformanceData(
      contentUri,
      campaign_name,
      regs,
      spend,
      totalSpend,
      isActive,
      spend / totalSpend,
      contentLiveDate,
      lastSpend
    )
  }
}
