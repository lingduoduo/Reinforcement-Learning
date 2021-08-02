package com.spotify.data.BelafonteContentRanker

import com.spotify.scio.bigquery._

object KnowledgeGraphTables {
  //Knowledge Graph readin query, each node with array of ~10 links (and weights)
  @BigQueryType.fromStorage(
    "acmacquisition:content_knowledge_graph.content_knowledge_graph_%s",
    args = List("$LATEST"),
    selectedFields = List("campaign", "popularity", "uri", "links")
  )
  class NodeWithLinksEntity

  //all we know about how this content relates to other content, and how popular it is
  case class KnowledgeGraphNode(
    nodeUri: String,
    campaign: String,
    popularity: Double,
    links: Array[Link]
  )

  //each link has a strength (lower - more strong)
  case class Link(
    toUri: String,
    strength: Double
  )

  //store knowledge graph data in KnowledgeGraphNode object
  def getNodeWithLinks(row: NodeWithLinksEntity): KnowledgeGraphNode = {
    val campaign = row.campaign
    val popularity = row.popularity
    val nodeUri = row.uri
    val links = row.links.map(f => Link(f.toUri, f.score)).toArray

    KnowledgeGraphNode(
      campaign = campaign,
      popularity = popularity,
      nodeUri = nodeUri,
      links = links
    )
  }
}
