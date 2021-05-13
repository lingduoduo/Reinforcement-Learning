package com.spotify.ladron.config

import com.spotify.bcd.schemas.scala.NonEmptyString
import com.spotify.ladron.common.{MessageChannel, MessagingModule}
import com.spotify.message.data.{Channel, MessageHistory}

/**
 * Historic view of all campaigns,
 * used to extract ladron metrics from unified message history.
 *
 * When the unit test detects missing campaigns,
 * add all new campaigns here (and leave old ones to allow metrics to be collected).
 */
// scalastyle:off line.size.limit
object Campaigns {
  type LadronCampaign = (MessageChannel, NonEmptyString)
  type DeliveryCampaign = (Channel, NonEmptyString)

  val ReactivationEmailTestCampaignId: NonEmptyString =
    NonEmptyString.fromAvro("reactivation-test")

  val ReactivationPushTestCampaignId: NonEmptyString =
    NonEmptyString.fromAvro("393dba3a-68f7-4f10-9967-8952d80ff57e")

  val TestCampaigns: Set[LadronCampaign] = Set(
    (MessageChannel.Email, ReactivationEmailTestCampaignId),
    (MessageChannel.Push, ReactivationPushTestCampaignId)
  )

  val ReactivationCampaigns: Set[LadronCampaign] = TestCampaigns ++ Set(
    (MessageChannel.Email, "reactivation-email-listen-to-daily-mix"), // reactivation_listentodailymix_*.yaml
    (MessageChannel.Email, "reactivation-email-playlist-for-any-mood"), // reactivation_playlistforanymood_*.yaml
    (MessageChannel.Email, "b3528314-02eb-4261-bbb0-6627d9d63583"), // reactivation_stations_simplicity_1.yaml
    (MessageChannel.Email, "reactivation-email-today-top-hits"), // reactivation_todaytophits_*.yaml
    (MessageChannel.Push, "de4fd6dd-4fe8-4783-886f-495ad86f86b4"), // reactivation_alsolistenedtopodcast_1.yaml
    (MessageChannel.Push, "4dbe87ff-3632-4b75-b85d-55e869dcbcae"), // reactivation_artist_bio_1.yaml
    (MessageChannel.Push, "51b0151b-2322-4d8b-a830-595ab38b39ab"), // reactivation_artist_discography_1.yaml
    (MessageChannel.Push, "a122d37b-5d79-49a6-9a1b-93e91ca397a1"), // reactivation_artist_radio_1.yaml
    (MessageChannel.Push, "ebaa753c-be42-45ef-8a32-819c8e2d1d1e"), // reactivation_artist_recommendation_1.yaml
    (MessageChannel.Push, "6ff3304e-fd7b-4a70-b9db-90dad376f954"), // reactivation_charts_1.yaml
    (MessageChannel.Push, "9dcbf4ab-151a-443b-b582-a45e3e8862b4"), // reactivation_chill_1.yaml
    (MessageChannel.Push, "08326a9d-7626-4251-b71d-f720c1cc1e4f"), // reactivation_daily_horoscope_1.yaml
    (MessageChannel.Push, "9347828f-78f3-432d-938a-6d5a46ffae70"), // reactivation_dailymix_artist_1.yaml
    (MessageChannel.Push, "af27c2e4-be00-4d34-b8e1-dda15ad2bdcc"), // reactivation_daily_mix_for_you_1.yaml
    (MessageChannel.Push, "34e2ab36-082b-4b7e-85ac-13e072ac57a8"), // reactivation_dailymix_personalized_1.yaml
    (MessageChannel.Push, "aefe9aa3-39d5-4755-b01f-225dc36d4201"), // reactivation_decadeshub_1.yaml
    (MessageChannel.Push, "1c644785-95ed-4386-b579-d304c619b9de"), // reactivation_discover_1.yaml
    (MessageChannel.Push, "ab2ee0f1-9266-4857-ac43-e4f98c4c1fe9"), // reactivation_discover_genre_1.yaml
    (MessageChannel.Push, "8029a550-e96d-45b0-a369-78c2a98a5493"), // reactivation_focus_1.yaml
    (MessageChannel.Push, "db1cf936-c9a3-4373-9cbc-4a8f0da4d62c"), // reactivation_follow_artist_1.yaml
    (MessageChannel.Push, "266aeabd-ce81-4b34-8558-4924ecbff139"), // reactivation_genre_playlist_1.yaml
    (MessageChannel.Push, "facb2817-0028-46a1-8b34-ea98c07f8390"), // reactivation_heavy_rotation_1.yaml
    (MessageChannel.Push, "98d6b091-c3e1-42b9-9ef9-e136a6c7698b"), // reactivation_home_1.yaml
    (MessageChannel.Push, "fdb7fd08-51af-4fbb-890f-f2d37f6b5c6a"), // reactivation_hotplaylistincountry_1.yaml
    (MessageChannel.Push, "78ee1c70-660e-4db1-839e-13519a6ed923"), // reactivation_inspiration_1.yaml
    (MessageChannel.Push, "3d1c77f6-58a8-46a8-9e02-39b652825677"), // reactivation_latestreleases_1.yaml
    (MessageChannel.Push, "2b8e55b3-6f84-4987-8fc7-75e506cfb6f5"), // reactivation_liked_songs_1.yaml
    (MessageChannel.Push, "ff58350c-1a16-48ae-8387-87a77c8524e4"), // reactivation_listen_again_playlist_1.yaml
    (MessageChannel.Push, "0c871221-24b4-44ad-bfd3-e27d94dd74c0"), // reactivation_listening_to_genre_1.yaml
    (MessageChannel.Push, "89be9bdb-3d1e-4966-ad1a-70deebd1ee3a"), // reactivation_listentodailymix_1.yaml
    (MessageChannel.Push, "abd01c54-dbc5-43f7-9e9a-645c23ff20f8"), // reactivation_lowdata_1.yaml
    (MessageChannel.Push, "9fb6e491-8762-4db2-9903-1e08705462ca"), // reactivation_monday_motivation_1.yaml
    (MessageChannel.Push, "b9521a61-ff06-4975-ab40-03104f66fdff"), // reactivation_moods_1.yaml
    (MessageChannel.Push, "6237d260-747d-4d05-b8c5-6f1f1115f0a4"), // reactivation_new_music_friday_1.yaml
    (MessageChannel.Push, "8ed538d0-6f5b-4b72-b6d8-6325d18ceb29"), // reactivation_new_release_1.yaml
    (MessageChannel.Push, "6c3e2e97-3030-4837-88a3-0e488e0a36ef"), // reactivation_numartists_1.yaml
    (MessageChannel.Push, "d97c3d29-4b39-44d2-a2b6-bb8d820d93f7"), // reactivation_party_1.yaml
    (MessageChannel.Push, "ea0ecbe4-4e92-43ac-8109-7da0379f1a79"), // reactivation_playlistforanymood_1.yaml
    (MessageChannel.Push, "ea0ecbe4-4e92-43ac-8109-7da0379f1a79"), // reactivation_playlistforanymood_2.yaml
    (MessageChannel.Push, "03e53cb0-ada2-4aad-8796-5602133a58a1"), // reactivation_plays_count_discover_1.yaml
    (MessageChannel.Push, "92066f72-22b4-446c-b5a4-a1a40a1ce816"), // reactivation_podcasts_1.yaml
    (MessageChannel.Push, "20c745a3-1841-40c8-88f3-44ae071d1646"), // reactivation_popular_local_artist.yaml
    (MessageChannel.Push, "1eb624c3-1410-48e6-9a00-722681cdd33f"), // reactivation_search_1.yaml
    (MessageChannel.Push, "b8d4cd5b-5843-470b-8c3d-013ad752d9f2"), // reactivation_song_radio_1.yaml
    (MessageChannel.Push, "15b436bd-05c9-49c2-8780-214a32c309ab"), // reactivation_this_is_1.yaml
    (MessageChannel.Push, "c5d14fea-88f7-474e-8a23-fdc2da5b9621"), // reactivation_todaytophits_1.yaml
    (MessageChannel.Push, "c5d14fea-88f7-474e-8a23-fdc2da5b9621"), // reactivation_todaytophits_2.yaml
    (MessageChannel.Push, "c5d14fea-88f7-474e-8a23-fdc2da5b9621"), // reactivation_todaytophits_3.yaml
    (MessageChannel.Push, "2e3c4be9-dc27-47c0-b70d-b2cf1c09d04e"), // reactivation_topplaylistincountry_1.yaml
    (MessageChannel.Push, "281022cc-0119-4648-a742-0868ec833ef7"), // reactivation_topviralpercountry_1.yaml
    (MessageChannel.Push, "c6bdee49-373b-4f93-94ef-c93d2b0f096f"), // reactivation_trendingnearyou_1.yaml
    (MessageChannel.Push, "cd756162-045a-4918-971d-211c67370309"), // reactivation_trendingnearyou_tap_1.yaml
    (MessageChannel.Push, "308480b5-af28-4a98-aaca-5c70ec97fe91") // reactivation_workout_1.yaml
  ).map(toNonEmptyString)

  val ActivationCampaigns: Set[LadronCampaign] = Set(
    (MessageChannel.Email, "activation-email-plcharts-01"), // legacy
    (MessageChannel.Email, "activation-email-plcreate-01"), // legacy
    (MessageChannel.Email, "activation-email-plmood-01"), // legacy
    (MessageChannel.Email, "activation-email-apptour-01"), // activation_apptour_g*.yaml
    (MessageChannel.Email, "activation-email-plsuggest-01"), // activation_playlistcharts_g*.yaml
    (MessageChannel.Email, "activation-email-wknd-01"), // activation_playlistmood_g*.yaml
    (MessageChannel.Email, "872a476b-637e-40df-b6e5-ea26b92369fa"), // activation_dining_hub_intro.yaml
    (MessageChannel.Email, "fc8eb87b-3506-4245-af98-f580e0096edc"), // activation_general_podcasts_intro.yaml
    (MessageChannel.Email, "2009d5c4-e841-41d2-b0e9-e04f2424ff53"), // activation_made_for_you.yaml
    (MessageChannel.Email, "4f4727ca-7191-496a-8f43-e2a75cce8485"), // activation_like_playlist_feature_education.yaml .yaml
    (MessageChannel.Email, "66f8f3dd-826e-426e-a1cd-fe866382e1e4"), // activation_mood_and_genre_intro.yaml
    (MessageChannel.Email, "66327733-dee6-423b-a361-44c9d2fc77f0"), // activation_this_is_top_two_artists.yaml
    (MessageChannel.Email, "ec00dd28-4256-4646-8d6a-130ea3031d4a"), // activation_artist_discovered_first_month.yaml
    (MessageChannel.Email, "1e45c1d3-64d2-4fdc-8d63-6a38cc1d1266"), // activation_connect_devices_edu.yaml
    (MessageChannel.Email, "0b3b64cd-718e-455b-b53a-a22af1a38efe"), // activation_sleep_hub_intro.yaml
    (MessageChannel.Email, "9d84661c-5334-4c64-bf3d-dc5cbd7f3ee4"), // activation_wellness_hub_intro.yaml
    (MessageChannel.Email, "087292db-c86f-4fde-a01a-98ad0e4884ec"), // activation_wellness_hub_intro_without_daily_wellness.yaml
    (MessageChannel.Email, "b9e9e0ec-bd74-43ae-8eaf-4ca4487a6973"), // activation_data_saver_and_offline_functionality.yaml
    (MessageChannel.Email, "95977edd-e626-444a-b18d-b1f54816fce2"), // activation_listening_in_car_ydd.yaml
    (MessageChannel.Email, "dacca3d2-6a49-4c3d-b884-0d8c731aeba1"), // activation_listening_in_car.yaml
    (MessageChannel.Email, "85f706cc-a13d-40d1-b4e0-342c70304588"), // activation_podcast_starter_kit.yaml
    (MessageChannel.Email, "02d54378-24bf-4ee4-b0cb-d763bf775dfa"), // activation_stay_in_the_know_podcast_playlist.yaml
    (MessageChannel.Email, "cbac9640-4fc8-4d12-ac18-24cf9498f43f"), // activation_gaming_podcasts_recs.yaml
    (MessageChannel.Email, "0dfa2400-6f65-4345-8948-91fb5092d014"), // activation_personalized_playlists.yaml
    (MessageChannel.Email, "0fdd6e36-d70b-4ae2-9643-87b63922f425"), // activation_music_genre_deep_dive.yaml
    (MessageChannel.Push, "581b88e0-d019-4919-8e92-0efcc1008a20"), // nancy TOC 1
    (MessageChannel.Push, "2d207468-bbcc-4752-ae07-7565cc53327d"), // nancy TONC 1
    (MessageChannel.Push, "1bfad9f6-eaa3-4ce3-ae28-e97c8e236f5d"), // nancy PLC 1
    (MessageChannel.Push, "9ccbab0f-aba1-4a29-b9d7-9709b81eeacd"), // nancy PLNC 1
    (MessageChannel.Push, "a19bd964-075f-477b-9b58-bdc9e855cd4c"), // activation_artist_bio_1.yaml
    (MessageChannel.Push, "a359b5ab-dce6-48ab-99bd-4816a35de7f2"), // activation_artist_discography_1.yaml
    (MessageChannel.Push, "e9b406aa-2f83-45dd-9911-edb0713ffd5a"), // activation_artist_radio_1.yaml
    (MessageChannel.Push, "145ac9e6-5118-44ab-897a-06da18545052"), // activation_artist_recommendation_1.yaml
    (MessageChannel.Push, "37e979b7-736f-48ab-8fb6-3b0b5a1a3f6c"), // activation_charts_1.yaml
    (MessageChannel.Push, "301cebe3-b8d7-4efc-8393-db05088b16e3"), // activation_chill_1.yaml
    (MessageChannel.Push, "c339691a-a857-4685-ba98-f51203ee12c3"), // activation_daily_horoscope_1.yaml
    (MessageChannel.Push, "7ccdfa24-5306-47e3-a685-3cb7563780f6"), // activation_dailymix_artist_1.yaml
    (MessageChannel.Push, "5e8816db-73af-40a9-95a3-2a643b6d9791"), // activation_daily_mix_for_you_1.yaml
    (MessageChannel.Push, "24f0c99e-d7fc-4a92-a5d9-95efff85f0d2"), // activation_decades_1.yaml
    (MessageChannel.Push, "2fbe7cc3-cbc6-4b85-b162-3cbb2d9ab889"), // activation_discover_1.yaml
    (MessageChannel.Push, "1d4b8a20-f4d8-44ea-956f-85f4a2b524fb"), // activation_discover_genre_1.yaml
    (MessageChannel.Push, "f3c8a0dc-49a8-4ac7-ac39-aff4f4032fac"), // activation_discoverweekly_1.yaml
    (MessageChannel.Push, "a5292342-8e7f-427d-8ba2-542de5639ce7"), // activation_discoverweekly_2.yaml
    (MessageChannel.Push, "4276bc9b-495d-4ee3-9075-cfe5bf9adbeb"), // activation_discoverweekly_3.yaml
    (MessageChannel.Push, "8782cee2-e2fd-45ac-bdd1-610c02494e14"), // activation_discoverweekly_4.yaml
    (MessageChannel.Push, "a3b97637-c225-4b3a-93e2-f630eb9c73be"), // activation_focus_1.yaml
    (MessageChannel.Push, "f32bcc06-4eeb-4964-b770-f87265e210d4"), // activation_follow_artist_1.yaml
    (MessageChannel.Push, "1adb5559-2525-4505-ab26-3c62de0721fc"), // activation_genre_playlist_1.yaml
    (MessageChannel.Push, "dc2ca7d5-c5f8-44a3-9bf6-02a1bf2db958"), // activation_heavy_rotation_1.yaml
    (MessageChannel.Push, "be89ec39-75ab-47b7-9508-47483adabe28"), // activation_home_updated14_1.yaml
    (MessageChannel.Push, "11de99bb-b762-49d8-89b3-99a9b871933b"), // activation_home_updated7_1.yaml
    (MessageChannel.Push, "530f35f7-36c9-4df6-837b-0b84168a645a"), // activation_latestreleases_1.yaml
    (MessageChannel.Push, "f8f9cd59-5d20-4322-94f9-f951f5350003"), // activation_liked_songs_1.yaml
    (MessageChannel.Push, "b488e573-6b55-4941-b11f-3dd951249f10"), // activation_listen_again_playlist_1.yaml
    (MessageChannel.Push, "121598e8-ddc9-42a0-b4de-5a735b3a826c"), // activation_listening_to_genre_1.yaml
    (MessageChannel.Push, "28816e7c-0d14-4eda-8f78-adfb2978630a"), // activation_lowdata_1.yaml
    (MessageChannel.Push, "fd8c52f5-d58f-4113-a180-4b1dc14032be"), // activation_monday_motivation_1.yaml
    (MessageChannel.Push, "2cd6d7c9-4ae3-48ea-8023-bce9fb59e570"), // activation_moods_1.yaml
    (MessageChannel.Push, "2abe5b36-f0af-4da5-839a-8daa72fba37a"), // activation_new_music_friday_1.yaml
    (MessageChannel.Push, "b87d993f-ae04-4936-b81f-0b1cc7a3a20b"), // activation_new_release_1.yaml
    (MessageChannel.Push, "ee87e9cf-5471-42f0-9c4e-65e16c123589"), // activation_numartists_1.yaml
    (MessageChannel.Push, "f8678038-3e8d-4c9e-9fe7-3d7f15964c96"), // activation_party_1.yaml
    (MessageChannel.Push, "4cbafa4e-bde0-4ba9-9980-0abfb6381b68"), // activation_plays_count_discover_1.yaml
    (MessageChannel.Push, "9ec54733-69a7-4e26-8c52-d056b427e178"), // activation_podcasts_1.yaml
    (MessageChannel.Push, "28c665ab-1655-4130-a48e-f2a8183b67bb"), // activation_popular_local_artist.yaml
    (MessageChannel.Push, "ca8fa9f3-881f-406c-9662-a81e7c83a823"), // activation_releaseradar_1.yaml
    (MessageChannel.Push, "24815676-d22e-424d-86ce-03a0ffa04a02"), // activation_releaseradar_2.yaml
    (MessageChannel.Push, "3fc9dc55-eb6d-4e39-baeb-152f52dbf91b"), // activation_releaseradar_3.yaml
    (MessageChannel.Push, "28afc0fb-e9a0-4109-bd4b-02df526efbf3"), // activation_releaseradar_4.yaml
    (MessageChannel.Push, "ed3e4393-92df-467d-83fd-5debdbb912ae"), // activation_search_1.yaml
    (MessageChannel.Push, "c35997b7-94c6-4a8c-9103-9fa9d8b1d16e"), // activation_song_radio_1.yaml
    (MessageChannel.Push, "25992c4d-3143-4530-8baf-67ec7cc39290"), // activation_this_is_1.yaml
    (MessageChannel.Push, "9e8e21ec-27b6-48fa-bf0a-1bed8d5d2caf"), // activation_trendingnearyou_tap_2.yaml
    (MessageChannel.Push, "3a8d73ce-98f3-44cd-af66-d5cd89efaf42") // activation_workout_1.yaml
  ).map(toNonEmptyString)

  val EngagementCampaigns: Set[LadronCampaign] = Set(
    (MessageChannel.Email, "0f251d85-cd9a-4ae4-b88d-f1bc40a836f1"), // followed_one_year_ago.yaml
    (MessageChannel.Push, "76816134-c063-4a3f-a92b-5db17ae6f7e0"), // alsolistenedtopodcast_1.yaml
    (MessageChannel.Push, "49da29fc-1478-4447-a758-64c66647e557"), // artist_bio_1.yaml
    (MessageChannel.Push, "8d5d6a5c-e032-4b5f-9b0a-54fa1a16f1e5"), // artist_discography_1.yaml
    (MessageChannel.Push, "6a2ca900-6402-40e2-b908-8a7e0f8b4542"), // artist_discography.yaml
    (MessageChannel.Push, "d1a4d4cc-dede-4b3c-9d70-0f7869bd1d42"), // artist_radio.yaml
    (MessageChannel.Push, "538ce4ac-3318-475f-b0cf-f06e1d841c8a"), // artist_recommendation.yaml
    (MessageChannel.Push, "4fde64d6-a301-49a8-814e-ed063587b878"), // charts_1.yaml
    (MessageChannel.Push, "1e966152-bbe3-412c-9e00-007ed58c693e"), // dailymix_artist_1.yaml
    (MessageChannel.Push, "538433aa-d2eb-426b-b3ba-a5bda7a6a8ef"), // daily_mix_personalized.yaml
    (MessageChannel.Push, "45953a86-511c-4364-959d-8913e8da0882"), // decadeshub_1.yaml
    (MessageChannel.Push, "c7c7fb2c-4d8c-4242-93bb-94a9844ca247"), // decades.yaml
    (MessageChannel.Push, "08f88e67-9fd7-4f8c-b29f-70ac516bdd11"), // discover_weekly_US_generic.yaml
    (MessageChannel.Push, "2d851b3d-88e5-4b06-8758-be421bd98f22"), // engagement_daily_horoscope_1.yaml
    (MessageChannel.Push, "bc3b7cb5-59fe-4f57-849c-199cae22b9ed"), // engagement_monday_motivation_1.yaml
    (MessageChannel.Push, "408e7dce-5c63-4000-936a-9c7f75ef019f"), // engagement_plays_count_discover_1.yaml
    (MessageChannel.Push, "46a9aa1b-dd27-4d90-a4dc-e3cf2c29f80e"), // engagement_song_radio_1.yaml
    (MessageChannel.Push, "cfd37a77-d7fc-45a9-b993-d657908803d3"), // focus_1.yaml
    (MessageChannel.Push, "9a48ec1f-f852-4381-8c9d-a4e88a612a25"), // follow_artist_1.yaml
    (MessageChannel.Push, "7576b285-cd97-4afc-bcf6-f443372c3d03"), // heavy_rotation_1.yaml
    (MessageChannel.Push, "45736430-3839-443c-be43-95849f8b638d"), // home_1.yaml
    (MessageChannel.Push, "4cb48ab5-998d-4738-b215-753fe083c5e1"), // home_updated14_1.yaml
    (MessageChannel.Push, "ecddac19-31f6-469e-8780-cff78d4aef4f"), // hot_country_generic.yaml
    (MessageChannel.Push, "a00b83b6-f974-42c2-b1d1-c7427ed6f99d"), // hot_country_personalized.yaml
    (MessageChannel.Push, "9e9627f7-65c5-43a2-896c-6f78fb049430"), // inspiration_1.yaml
    (MessageChannel.Push, "ffca1490-9253-4387-94b9-ddba4051a276"), // latestreleases_1.yaml
    (MessageChannel.Push, "f6c8e18c-32dc-40d4-9039-a1c5fed2085c"), // listening_to_genre_1.yaml
    (MessageChannel.Push, "cbd70fcb-af1c-4e40-9eb9-4bd1bccf0e14"), // lowdata_1.yaml
    (MessageChannel.Push, "ba353803-dee6-4852-b601-ef6175a01eb7"), // mint_generic.yaml
    (MessageChannel.Push, "5b9e6613-89e7-4f79-b7bd-457a9fd29625"), // mint_personalized.yaml
    (MessageChannel.Push, "ec62d513-845b-48bb-9eb4-6aebf8ebde0a"), // moods_1.yaml
    (MessageChannel.Push, "525a68e5-bf31-448c-bf3e-b4ab1698e36a"), // new_podcast_episodes.yaml
    (MessageChannel.Push, "156fd1cc-562d-4afd-9180-19945ef58efa"), // new_podcast_nonfollows_episodes.yaml
    (MessageChannel.Push, "9da30c8a-6c86-4247-a669-231468fa4745"), // new_release_1.yaml
    (MessageChannel.Push, "4170125d-ae5f-4a4b-9d32-8792fd0f6d9e"), // new_release.yaml
    (MessageChannel.Push, "ea9c73aa-c125-48ba-96d8-af9cc7023ea8"), // on_repeat.yaml
    (MessageChannel.Push, "a54024c8-9e65-4b5f-836d-97bf431a4d44"), // podcasts_1.yaml
    (MessageChannel.Push, "6ca30a3f-5970-490b-b4e0-2ae02b07a1d7"), // rap_caviar_generic.yaml
    (MessageChannel.Push, "9755366d-56ea-4b05-a2db-5bc013fe3fc7"), // rap_caviar_personalized.yaml
    (MessageChannel.Push, "6a6b5338-1b3b-4a92-ac01-9a9578b0496c"), // releaseradar_1.yaml
    (MessageChannel.Push, "bc6edcee-1d6e-418a-8325-c63e295e5284"), // releaseradar_US_generic.yaml
    (MessageChannel.Push, "f2be5e48-8000-4968-945e-ef4558db37b6"), // releaseradar_US_personalized_1.yaml
    (MessageChannel.Push, "24a1f212-8393-47bc-b505-64120ad5671c"), // repeat_rewind.yaml
    (MessageChannel.Push, "719e7d01-c25d-4076-8fa2-e1029c4fb5c6"), // rock_this_generic.yaml
    (MessageChannel.Push, "462c61e4-bcde-4c3d-97ff-9f3b8ee4b048"), // rock_this_personalized.yaml
    (MessageChannel.Push, "928c01b3-3d12-4388-8cef-7b9f56f7e282"), // this_is_1.yaml
    (MessageChannel.Push, "9260d0aa-19b7-41ae-a38b-641affeadc04"), // this_is_playlist_personalized_1.yaml
    (MessageChannel.Push, "4b66b547-f0cb-4699-bfb7-cc8c0440b52f"), // top_genre.yaml
    (MessageChannel.Push, "04da6bf5-ef41-450f-aff3-35c7af9307c9"), // top_hits_US_generic.yaml
    (MessageChannel.Push, "edb4fe90-447e-4019-a6ad-926cfcfd62f8"), // top_hits_US_personalized_1.yaml
    (MessageChannel.Push, "72a2d861-9fe8-46dd-a385-22b47d24e94d"), // trending_near_you_non_personalized.yaml
    (MessageChannel.Push, "d914fc74-1fb3-4047-993d-5d4ed5c74aaf"), // trendingnearyou_tap_2.yaml
    (MessageChannel.Push, "8a372e73-26fa-4e68-8f91-24a07e2bb8fb"), // viva_latino_generic.yaml
    (MessageChannel.Push, "29a11a77-d149-4410-9d4d-e871a31af702") // viva_latino_personalized.yaml
  ).map(toNonEmptyString)

  // All campaigns in every stage of the lifecycle
  val Campaigns: Set[LadronCampaign] =
    ReactivationCampaigns ++ ActivationCampaigns ++ EngagementCampaigns

  // Whitelisted production campaigns (as included in unified lifecycle test).
  val ProductionCampaigns: Set[LadronCampaign] = Set(
    (MessageChannel.Email, "reactivation-email-listen-to-daily-mix"), // reactivation_listentodailymix_1.yaml
    (MessageChannel.Email, "reactivation-email-playlist-for-any-mood"), // reactivation_playlistforanymood_1.yaml
    (MessageChannel.Email, "b3528314-02eb-4261-bbb0-6627d9d63583"), // reactivation_stations_simplicity_1.yaml
    (MessageChannel.Email, "reactivation-email-today-top-hits"), // reactivation_todaytophits_1.yaml
    (MessageChannel.Push, "de4fd6dd-4fe8-4783-886f-495ad86f86b4"), // reactivation_alsolistenedtopodcast_1.yaml
    (MessageChannel.Push, "4dbe87ff-3632-4b75-b85d-55e869dcbcae"), // reactivation_artist_bio_1.yaml
    (MessageChannel.Push, "51b0151b-2322-4d8b-a830-595ab38b39ab"), // reactivation_artist_discography_1.yaml
    (MessageChannel.Push, "a122d37b-5d79-49a6-9a1b-93e91ca397a1"), // reactivation_artist_radio_1.yaml
    (MessageChannel.Push, "ebaa753c-be42-45ef-8a32-819c8e2d1d1e"), // reactivation_artist_recommendation_1.yaml
    (MessageChannel.Push, "6ff3304e-fd7b-4a70-b9db-90dad376f954"), // reactivation_charts_1.yaml
    (MessageChannel.Push, "9dcbf4ab-151a-443b-b582-a45e3e8862b4"), // reactivation_chill_1.yaml
    (MessageChannel.Push, "9347828f-78f3-432d-938a-6d5a46ffae70"), // reactivation_dailymix_artist_1.yaml
    (MessageChannel.Push, "34e2ab36-082b-4b7e-85ac-13e072ac57a8"), // reactivation_dailymix_personalized_1.yaml
    (MessageChannel.Push, "aefe9aa3-39d5-4755-b01f-225dc36d4201"), // reactivation_decadeshub_1.yaml
    (MessageChannel.Push, "1c644785-95ed-4386-b579-d304c619b9de"), // reactivation_discover_1.yaml
    (MessageChannel.Push, "8029a550-e96d-45b0-a369-78c2a98a5493"), // reactivation_focus_1.yaml
    (MessageChannel.Push, "facb2817-0028-46a1-8b34-ea98c07f8390"), // reactivation_heavy_rotation_1.yaml
    (MessageChannel.Push, "98d6b091-c3e1-42b9-9ef9-e136a6c7698b"), // reactivation_home_1.yaml
    (MessageChannel.Push, "fdb7fd08-51af-4fbb-890f-f2d37f6b5c6a"), // reactivation_hotplaylistincountry_1.yaml
    (MessageChannel.Push, "78ee1c70-660e-4db1-839e-13519a6ed923"), // reactivation_inspiration_1.yaml
    (MessageChannel.Push, "3d1c77f6-58a8-46a8-9e02-39b652825677"), // reactivation_latestreleases_1.yaml
    (MessageChannel.Push, "0c871221-24b4-44ad-bfd3-e27d94dd74c0"), // reactivation_listening_to_genre_1.yaml
    (MessageChannel.Push, "89be9bdb-3d1e-4966-ad1a-70deebd1ee3a"), // reactivation_listentodailymix_1.yaml
    (MessageChannel.Push, "abd01c54-dbc5-43f7-9e9a-645c23ff20f8"), // reactivation_lowdata_1.yaml
    (MessageChannel.Push, "b9521a61-ff06-4975-ab40-03104f66fdff"), // reactivation_moods_1.yaml
    (MessageChannel.Push, "8ed538d0-6f5b-4b72-b6d8-6325d18ceb29"), // reactivation_new_release_1.yaml
    (MessageChannel.Push, "6c3e2e97-3030-4837-88a3-0e488e0a36ef"), // reactivation_numartists_1.yaml
    (MessageChannel.Push, "d97c3d29-4b39-44d2-a2b6-bb8d820d93f7"), // reactivation_party_1.yaml
    (MessageChannel.Push, "ea0ecbe4-4e92-43ac-8109-7da0379f1a79"), // reactivation_playlistforanymood_1.yaml
    (MessageChannel.Push, "92066f72-22b4-446c-b5a4-a1a40a1ce816"), // reactivation_podcasts_1.yaml
    (MessageChannel.Push, "20c745a3-1841-40c8-88f3-44ae071d1646"), // reactivation_popular_local_artist.yaml
    (MessageChannel.Push, "15b436bd-05c9-49c2-8780-214a32c309ab"), // reactivation_this_is_1.yaml
    (MessageChannel.Push, "c5d14fea-88f7-474e-8a23-fdc2da5b9621"), // reactivation_todaytophits_1.yaml
    (MessageChannel.Push, "2e3c4be9-dc27-47c0-b70d-b2cf1c09d04e"), // reactivation_topplaylistincountry_1.yaml
    (MessageChannel.Push, "281022cc-0119-4648-a742-0868ec833ef7"), // reactivation_topviralpercountry_1.yaml
    (MessageChannel.Push, "c6bdee49-373b-4f93-94ef-c93d2b0f096f"), // reactivation_trendingnearyou_1.yaml
    (MessageChannel.Push, "cd756162-045a-4918-971d-211c67370309"), // reactivation_trendingnearyou_tap_1.yaml
    (MessageChannel.Email, "activation-email-plcharts-01"), // legacy
    (MessageChannel.Email, "activation-email-plcreate-01"), // legacy
    (MessageChannel.Email, "activation-email-plmood-01"), // legacy
    (MessageChannel.Email, "activation-email-plsuggest-01"), // activation_playlistcharts_g1.yaml
    (MessageChannel.Email, "activation-email-wknd-01"), // activation_playlistmood_g1.yaml
    (MessageChannel.Push, "581b88e0-d019-4919-8e92-0efcc1008a20"), // nancy TOC 1
    (MessageChannel.Push, "2d207468-bbcc-4752-ae07-7565cc53327d"), // nancy TONC 1
    (MessageChannel.Push, "1bfad9f6-eaa3-4ce3-ae28-e97c8e236f5d"), // nancy PLC 1
    (MessageChannel.Push, "9ccbab0f-aba1-4a29-b9d7-9709b81eeacd"), // nancy PLNC 1
    (MessageChannel.Push, "a19bd964-075f-477b-9b58-bdc9e855cd4c"), // activation_artist_bio_1.yaml
    (MessageChannel.Push, "a359b5ab-dce6-48ab-99bd-4816a35de7f2"), // activation_artist_discography_1.yaml
    (MessageChannel.Push, "e9b406aa-2f83-45dd-9911-edb0713ffd5a"), // activation_artist_radio_1.yaml
    (MessageChannel.Push, "145ac9e6-5118-44ab-897a-06da18545052"), // activation_artist_recommendation_1.yaml
    (MessageChannel.Push, "37e979b7-736f-48ab-8fb6-3b0b5a1a3f6c"), // activation_charts_1.yaml
    (MessageChannel.Push, "301cebe3-b8d7-4efc-8393-db05088b16e3"), // activation_chill_1.yaml
    (MessageChannel.Push, "7ccdfa24-5306-47e3-a685-3cb7563780f6"), // activation_dailymix_artist_1.yaml
    (MessageChannel.Push, "24f0c99e-d7fc-4a92-a5d9-95efff85f0d2"), // activation_decades_1.yaml
    (MessageChannel.Push, "2fbe7cc3-cbc6-4b85-b162-3cbb2d9ab889"), // activation_discover_1.yaml
    (MessageChannel.Push, "f3c8a0dc-49a8-4ac7-ac39-aff4f4032fac"), // activation_discoverweekly_1.yaml
    (MessageChannel.Push, "a5292342-8e7f-427d-8ba2-542de5639ce7"), // activation_discoverweekly_2.yaml
    (MessageChannel.Push, "4276bc9b-495d-4ee3-9075-cfe5bf9adbeb"), // activation_discoverweekly_3.yaml
    (MessageChannel.Push, "8782cee2-e2fd-45ac-bdd1-610c02494e14"), // activation_discoverweekly_4.yaml
    (MessageChannel.Push, "a3b97637-c225-4b3a-93e2-f630eb9c73be"), // activation_focus_1.yaml
    (MessageChannel.Push, "dc2ca7d5-c5f8-44a3-9bf6-02a1bf2db958"), // activation_heavy_rotation_1.yaml
    (MessageChannel.Push, "be89ec39-75ab-47b7-9508-47483adabe28"), // activation_home_updated14_1.yaml
    (MessageChannel.Push, "11de99bb-b762-49d8-89b3-99a9b871933b"), // activation_home_updated7_1.yaml
    (MessageChannel.Push, "530f35f7-36c9-4df6-837b-0b84168a645a"), // activation_latestreleases_1.yaml
    (MessageChannel.Push, "121598e8-ddc9-42a0-b4de-5a735b3a826c"), // activation_listening_to_genre_1.yaml
    (MessageChannel.Push, "28816e7c-0d14-4eda-8f78-adfb2978630a"), // activation_lowdata_1.yaml
    (MessageChannel.Push, "2cd6d7c9-4ae3-48ea-8023-bce9fb59e570"), // activation_moods_1.yaml
    (MessageChannel.Push, "b87d993f-ae04-4936-b81f-0b1cc7a3a20b"), // activation_new_release_1.yaml
    (MessageChannel.Push, "ee87e9cf-5471-42f0-9c4e-65e16c123589"), // activation_numartists_1.yaml
    (MessageChannel.Push, "f8678038-3e8d-4c9e-9fe7-3d7f15964c96"), // activation_party_1.yaml
    (MessageChannel.Push, "9ec54733-69a7-4e26-8c52-d056b427e178"), // activation_podcasts_1.yaml
    (MessageChannel.Push, "28c665ab-1655-4130-a48e-f2a8183b67bb"), // activation_popular_local_artist.yaml
    (MessageChannel.Push, "ca8fa9f3-881f-406c-9662-a81e7c83a823"), // activation_releaseradar_1.yaml
    (MessageChannel.Push, "24815676-d22e-424d-86ce-03a0ffa04a02"), // activation_releaseradar_2.yaml
    (MessageChannel.Push, "3fc9dc55-eb6d-4e39-baeb-152f52dbf91b"), // activation_releaseradar_3.yaml
    (MessageChannel.Push, "28afc0fb-e9a0-4109-bd4b-02df526efbf3"), // activation_releaseradar_4.yaml
    (MessageChannel.Push, "25992c4d-3143-4530-8baf-67ec7cc39290"), // activation_this_is_1.yaml
    (MessageChannel.Push, "9e8e21ec-27b6-48fa-bf0a-1bed8d5d2caf"), // activation_trendingnearyou_tap_2.yaml
    (MessageChannel.Push, "76816134-c063-4a3f-a92b-5db17ae6f7e0"), // alsolistenedtopodcast_1.yaml
    (MessageChannel.Push, "49da29fc-1478-4447-a758-64c66647e557"), // artist_bio_1.yaml
    (MessageChannel.Push, "8d5d6a5c-e032-4b5f-9b0a-54fa1a16f1e5"), // artist_discography_1.yaml
    (MessageChannel.Push, "6a2ca900-6402-40e2-b908-8a7e0f8b4542"), // artist_discography.yaml
    (MessageChannel.Push, "d1a4d4cc-dede-4b3c-9d70-0f7869bd1d42"), // artist_radio.yaml
    (MessageChannel.Push, "538ce4ac-3318-475f-b0cf-f06e1d841c8a"), // artist_recommendation.yaml
    (MessageChannel.Push, "4fde64d6-a301-49a8-814e-ed063587b878"), // charts_1.yaml
    (MessageChannel.Push, "1e966152-bbe3-412c-9e00-007ed58c693e"), // dailymix_artist_1.yaml
    (MessageChannel.Push, "538433aa-d2eb-426b-b3ba-a5bda7a6a8ef"), // daily_mix_personalized.yaml
    (MessageChannel.Push, "45953a86-511c-4364-959d-8913e8da0882"), // decadeshub_1.yaml
    (MessageChannel.Push, "c7c7fb2c-4d8c-4242-93bb-94a9844ca247"), // decades.yaml
    (MessageChannel.Push, "08f88e67-9fd7-4f8c-b29f-70ac516bdd11"), // discover_weekly_US_generic.yaml
    (MessageChannel.Push, "cfd37a77-d7fc-45a9-b993-d657908803d3"), // focus_1.yaml
    (MessageChannel.Push, "45736430-3839-443c-be43-95849f8b638d"), // home_1.yaml
    (MessageChannel.Push, "4cb48ab5-998d-4738-b215-753fe083c5e1"), // home_updated14_1.yaml
    (MessageChannel.Push, "ecddac19-31f6-469e-8780-cff78d4aef4f"), // hot_country_generic.yaml
    (MessageChannel.Push, "a00b83b6-f974-42c2-b1d1-c7427ed6f99d"), // hot_country_personalized.yaml
    (MessageChannel.Push, "9e9627f7-65c5-43a2-896c-6f78fb049430"), // inspiration_1.yaml
    (MessageChannel.Push, "ffca1490-9253-4387-94b9-ddba4051a276"), // latestreleases_1.yaml
    (MessageChannel.Push, "f6c8e18c-32dc-40d4-9039-a1c5fed2085c"), // listening_to_genre_1.yaml
    (MessageChannel.Push, "cbd70fcb-af1c-4e40-9eb9-4bd1bccf0e14"), // lowdata_1.yaml
    (MessageChannel.Push, "ba353803-dee6-4852-b601-ef6175a01eb7"), // mint_generic.yaml
    (MessageChannel.Push, "5b9e6613-89e7-4f79-b7bd-457a9fd29625"), // mint_personalized.yaml
    (MessageChannel.Push, "ec62d513-845b-48bb-9eb4-6aebf8ebde0a"), // moods_1.yaml
    (MessageChannel.Push, "525a68e5-bf31-448c-bf3e-b4ab1698e36a"), // new_podcast_episodes.yaml
    (MessageChannel.Push, "156fd1cc-562d-4afd-9180-19945ef58efa"), // new_podcast_nonfollows_episodes.yaml
    (MessageChannel.Push, "9da30c8a-6c86-4247-a669-231468fa4745"), // new_release_1.yaml
    (MessageChannel.Push, "4170125d-ae5f-4a4b-9d32-8792fd0f6d9e"), // new_release.yaml
    (MessageChannel.Push, "ea9c73aa-c125-48ba-96d8-af9cc7023ea8"), // on_repeat.yaml
    (MessageChannel.Push, "a54024c8-9e65-4b5f-836d-97bf431a4d44"), // podcasts_1.yaml
    (MessageChannel.Push, "6ca30a3f-5970-490b-b4e0-2ae02b07a1d7"), // rap_caviar_generic.yaml
    (MessageChannel.Push, "9755366d-56ea-4b05-a2db-5bc013fe3fc7"), // rap_caviar_personalized.yaml
    (MessageChannel.Push, "6a6b5338-1b3b-4a92-ac01-9a9578b0496c"), // releaseradar_1.yaml
    (MessageChannel.Push, "bc6edcee-1d6e-418a-8325-c63e295e5284"), // releaseradar_US_generic.yaml
    (MessageChannel.Push, "f2be5e48-8000-4968-945e-ef4558db37b6"), // releaseradar_US_personalized_1.yaml
    (MessageChannel.Push, "24a1f212-8393-47bc-b505-64120ad5671c"), // repeat_rewind.yaml
    (MessageChannel.Push, "719e7d01-c25d-4076-8fa2-e1029c4fb5c6"), // rock_this_generic.yaml
    (MessageChannel.Push, "462c61e4-bcde-4c3d-97ff-9f3b8ee4b048"), // rock_this_personalized.yaml
    (MessageChannel.Push, "928c01b3-3d12-4388-8cef-7b9f56f7e282"), // this_is_1.yaml
    (MessageChannel.Push, "9260d0aa-19b7-41ae-a38b-641affeadc04"), // this_is_playlist_personalized_1.yaml
    (MessageChannel.Push, "4b66b547-f0cb-4699-bfb7-cc8c0440b52f"), // top_genre.yaml
    (MessageChannel.Push, "04da6bf5-ef41-450f-aff3-35c7af9307c9"), // top_hits_US_generic.yaml
    (MessageChannel.Push, "edb4fe90-447e-4019-a6ad-926cfcfd62f8"), // top_hits_US_personalized_1.yaml
    (MessageChannel.Push, "72a2d861-9fe8-46dd-a385-22b47d24e94d"), // trending_near_you_non_personalized.yaml
    (MessageChannel.Push, "d914fc74-1fb3-4047-993d-5d4ed5c74aaf"), // trendingnearyou_tap_2.yaml
    (MessageChannel.Push, "8a372e73-26fa-4e68-8f91-24a07e2bb8fb"), // viva_latino_generic.yaml
    (MessageChannel.Push, "29a11a77-d149-4410-9d4d-e871a31af702") // viva_latino_personalized.yaml
  ).map(toNonEmptyString)

  val ReactivationMessageDeliveryCampaigns: Set[DeliveryCampaign] = ReactivationCampaigns.map {
    case (channel, campaignId) => (Channel.withName(channel.name), campaignId)
  }

  val ActivationMessageDeliveryCampaigns: Set[DeliveryCampaign] = ActivationCampaigns.map {
    case (channel, campaignId) => (Channel.withName(channel.name), campaignId)
  }

  val EngagementMessageDeliveryCampaigns: Set[DeliveryCampaign] = EngagementCampaigns.map {
    case (channel, campaignId) => (Channel.withName(channel.name), campaignId)
  }

  // All campaigns in every stage of the lifecycle
  val MessageDeliveryCampaignToOriginModule: Map[DeliveryCampaign, MessagingModule] =
    (ReactivationMessageDeliveryCampaigns.map(_ -> MessagingModule.Reactivation) ++
      ActivationMessageDeliveryCampaigns.map(_ -> MessagingModule.Activation) ++
      EngagementMessageDeliveryCampaigns.map(_ -> MessagingModule.Engagement)).toMap

  val MessageDeliveryCampaigns: Set[DeliveryCampaign] =
    MessageDeliveryCampaignToOriginModule.keys.toSet

  def hasValidDeliveryCampaign(valid: Set[DeliveryCampaign])(event: MessageHistory): Boolean =
    valid.contains((event.channel, event.campaignId))

  private def toNonEmptyString(tuple: (MessageChannel, String)): LadronCampaign =
    tuple._1 -> NonEmptyString.fromAvro(tuple._2)
}
// scalastyle:on line.size.limit
