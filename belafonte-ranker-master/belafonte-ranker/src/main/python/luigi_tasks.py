import luigi
from spotify_scala_luigi.scio import ScioJobTask
from spotify_bigquery_luigi import BigQueryOverwriteMixin, BigQueryTarget
from base import BigQueryDailySnapshot


LOCATION = "EU"


class StyxDateSecondParameter(luigi.DateSecondParameter):
    date_format = '%Y-%m-%dT%H:%M:%SZ'


class BelafonteContentRankerJob(BigQueryOverwriteMixin, ScioJobTask):
    # --endpoint and --uri-prefix luigi parameters for HadesTarget output()
    # defines default values for styx scheduling, but can be overridden locally in the command line
    endpoint = luigi.Parameter(default="acmacquisition.belafonte_content_ranking.belafonte_content_ranking_YYYYMMDD")
    uri_prefix = luigi.Parameter(default="gs://belafonte/", significant=False)
    date = StyxDateSecondParameter()

    service_account = "belafonte-ranker-wk@acmacquisition.iam.gserviceaccount.com"

    package = "com.spotify.data.BelafonteContentRanker"
    project = "acmacquisition"
    region = "europe-west1"
    # subnetwork needs to be configured to enable XPN networking, please refer to scio-cookie
    # readme: https://ghe.spotify.net/scala/scio-cookie/wiki#your-own-xpn-project
    # uncomment to enable XPN for europe-west
    # subnetwork =
    # "https://www.googleapis.com/compute/v1/projects/
    # xpn-master/regions/europe-west1/subnetworks/xpn-euw1"
    autoscaling_algorithm = "THROUGHPUT_BASED"
    max_num_workers = 25
    beam = True

    def __init__(self, *args, **kwargs):
        super(BelafonteContentRankerJob, self).__init__(*args, **kwargs)
        # Staging/temp location use Hades uri_prefix, so temp data is stored close to output data
        self.staging_location = self.uri_prefix + "dataflow-staging/"
        self.temp_location = self.uri_prefix + "dataflow-temp/"

    def requires(self):
        return {
            'performance-table': BigQueryDailySnapshot(date=self.date,
                                                       project=self.project,
                                                       dataset='zissou_belafonte_metrics',
                                                       table='zissou_belafonte_metrics'),
            'knowledge-graph-table': BigQueryDailySnapshot(date=self.date,
                                                           project=self.project,
                                                           dataset='content_knowledge_graph',
                                                           table='content_knowledge_graph')}

    def args(self):
        return [
            "--dataEndpoint=%s" % self.endpoint,
            "--dataPartition=%s" % self.date.strftime("%Y-%m-%d"),
            "--date=%s" % self.date.strftime("%Y-%m-%d")
        ]

    def output(self):
        return {
            "outputbq": BigQueryTarget(project_id="acmacquisition",
                                       dataset_id="belafonte_content_ranking",
                                       table_id="belafonte_content_ranking_%s"
                                                % self.date.strftime("%Y%m%d"),
                                       location=LOCATION)
        }
