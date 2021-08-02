import pandas as pd


def preprocess_training_data(raw_data):
    # collect campaigns
    campaigns_counts = pd.DataFrame(raw_data.groupby(["campaign_name"]).size()).reset_index()
    filtered_campaigns = campaigns_counts[campaigns_counts.iloc[:, -1] > 500]
    user_retention_trans = raw_data.copy()
    for campaign in filtered_campaigns["campaign_name"]:
        user_retention_trans["campaigns_" + campaign] = (user_retention_trans["campaign_name"] == campaign)

    # collect contents
    contents_counts = pd.DataFrame(raw_data.groupby(["content_uri"]).size()).reset_index()
    filtered_contents = contents_counts[contents_counts.iloc[:, -1] > 30]
    for content in filtered_contents["content_uri"]:
        user_retention_trans["contents_" + content] = (user_retention_trans["content_uri"] == content)
    return user_retention_trans, filtered_campaigns["campaign_name"], filtered_contents["content_uri"]
