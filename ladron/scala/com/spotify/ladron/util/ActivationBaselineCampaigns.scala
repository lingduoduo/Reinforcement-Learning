package com.spotify.ladron.util

import com.spotify.ladron.model.CandidateMessageWithContext

/**
 * !HACK!
 *
 * Replicates the "original" behaviour when Nancy ran separately from ladron
 * using handcrafted messaging series.
 *
 * Since it's impossible to replicate the exact behaviour of the old series we assume
 * a sequence with similar messages is enough.
 *
 * !IMPORTANT!
 *  Assignment of messages to the days of week (eg Release Radar on Monday) is done
 * inside campaign-runner and is global for all buckets
 */
case object ActivationBaselineCampaigns {
  private val ReleaseRadar = Seq(
    "ca8fa9f3-881f-406c-9662-a81e7c83a823", // RR1
    "24815676-d22e-424d-86ce-03a0ffa04a02", // RR2
    "3fc9dc55-eb6d-4e39-baeb-152f52dbf91b", // RR3
    "28afc0fb-e9a0-4109-bd4b-02df526efbf3" // RR4
  )

  private val DiscoveryWeekly = Seq(
    "f3c8a0dc-49a8-4ac7-ac39-aff4f4032fac", // DW1
    "a5292342-8e7f-427d-8ba2-542de5639ce7", // DW2
    "4276bc9b-495d-4ee3-9075-cfe5bf9adbeb", // DW3
    "8782cee2-e2fd-45ac-bdd1-610c02494e14" // DW4
  )

  private val HomeUpdated = Seq(
    "11de99bb-b762-49d8-89b3-99a9b871933b", // HU7
    "be89ec39-75ab-47b7-9508-47483adabe28" // HU14
  )

  private val TasteOnboarding = Seq(
    "581b88e0-d019-4919-8e92-0efcc1008a20", // Completed
    "2d207468-bbcc-4752-ae07-7565cc53327d" // Not completed
  )

  private val PlaylistCreated = Seq(
    "1bfad9f6-eaa3-4ce3-ae28-e97c8e236f5d", // Created
    "9ccbab0f-aba1-4a29-b9d7-9709b81eeacd" // Not created
  )

  private val BaselineCampaigns =
    ReleaseRadar ++ DiscoveryWeekly ++ HomeUpdated ++ TasteOnboarding ++ PlaylistCreated

  def filterMessages(m: CandidateMessageWithContext): Boolean =
    BaselineCampaigns.contains(m.message.campaignId.toString())
}
