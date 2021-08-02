def preprocess_scoring_data(raw_data, valid_campaigns, valid_contents):
    whatif_contents = raw_data[["campaign_name", "content_uri"]].drop_duplicates()
    campaigns = raw_data.drop(["content_uri"], 1)
    whatif_contents["fake"] = 1
    campaigns["fake"] = 1
    scoring_data = campaigns.merge(whatif_contents).drop("fake", 1)
    scoring_data_transformed = scoring_data.copy()

    for campaign in valid_campaigns:
        scoring_data_transformed["campaigns_" + campaign] = (scoring_data_transformed["campaign_name"] == campaign)

    for content in valid_contents:
        scoring_data_transformed["contents_" + content] = (scoring_data_transformed["content_uri"] == content)
    return scoring_data, scoring_data_transformed
