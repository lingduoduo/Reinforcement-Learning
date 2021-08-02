import logging
import luigi

from base import BigQueryDailySnapshot
from datetime import timedelta
from user_retention.fetch_data import fetch_training_data
from user_retention.preprocess_data import preprocess_training_data
from user_retention.trainer import training_models
from user_retention.preprocess_scoring_data import preprocess_scoring_data


class UserRetentionPredictionJob(luigi.Task):
    date = luigi.DateParameter()
    endpoint = luigi.Parameter(default="acmacquisition.belafonte_content_ranking.user_retention_prediction_YYYYMMDD")
    uri_prefix = luigi.Parameter(default="gs://belafonte/", significant=False)

    service_account = "belafonte-ranker-wk@acmacquisition.iam.gserviceaccount.com"

    package = "com.spotify.data.BelafonteContentRanker"
    project = "acmacquisition"
    region = "europe-west1"

    def _shift_date(self, add_sub, offset):
        if add_sub:
            new_date = self.date + timedelta(days=offset)
        else:
            new_date = self.date - timedelta(days=offset)
        return new_date

    def requires(self):
        return {
            "user_retention_adjust": BigQueryDailySnapshot(date=self._shift_date(False, 9),
                                                           project="acmacquisition",
                                                           dataset="user_retention_adjust",
                                                           table="user_retention_adjust"),
            "facebook_adjust": BigQueryDailySnapshot(date=self._shift_date(False, 9),
                                                     project="acmacquisition",
                                                     dataset="bqr",
                                                     table="facebook_adjust"),
            "display_adjust": BigQueryDailySnapshot(date=self._shift_date(False, 9),
                                                    project="acmacquisition",
                                                    dataset="bqr",
                                                    table="display_adjust"),
        }

    def run(self):
        # Fetch Data
        start_dt = self._shift_date(False, 39)
        end_dt = self._shift_date(False, 9)
        data = fetch_training_data(start_dt, end_dt)
        logging.info(data.info())
        logging.info("finished fetching training data")

        # Preprocess Data
        transformed_data, valid_campaigns, valid_contents = preprocess_training_data(data)
        logging.info(transformed_data.info())
        logging.info("finished transforming training data")

        # Preprocess Scoring Data
        scoring_dataset, transformed_scoring_data = preprocess_scoring_data(data, valid_campaigns, valid_contents)
        logging.info(scoring_dataset.info())
        logging.info("finished transforming scoring data")

        results = training_models(transformed_data, scoring_dataset, transformed_scoring_data)
        results.to_gbq(destination_table="belafonte_content_ranking.user_retention_predictions_%s" % self.date.strftime("%Y%m%d"),
                       project_id="acmacquisition", if_exists="replace")
        logging.info("job finished")
