package com.spotify.ladron.eligibility

import com.spotify.ladron.Coders._
import com.spotify.ladron.common.{AssignmentPolicy, CampaignState}
import com.spotify.ladron.config.Campaigns.LadronCampaign
import com.spotify.ladron.model.AssignedCandidateMessages
import com.spotify.scio.values.SCollection

object FilterAssignedCampaignJob {

  def pipeline(whitelist: Set[LadronCampaign])(
    c: SCollection[AssignedCandidateMessages]
  ): SCollection[AssignedCandidateMessages] =
    c.flatMap(filter(whitelist))

  def onlyProd(group: AssignmentPolicy): Boolean =
    group.includeState.contains(CampaignState.Prod)

  def filter(whitelist: Set[LadronCampaign])(
    cs: AssignedCandidateMessages
  ): Option[AssignedCandidateMessages] = {
    if (onlyProd(cs.group)) {
      val filtered = cs.messages.filter(c => whitelist.contains(c.channel, c.campaignId))
      if (filtered.isEmpty) {
        None
      } else {
        Some(cs.copy(messages = filtered))
      }
    } else {
      Some(cs)
    }
  }
}
