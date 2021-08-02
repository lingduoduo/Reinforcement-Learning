import datetime
import pytest
from luigi_tasks import BelafonteContentRankerJob


class BelafonteContentRankerJobTest(object):
    @pytest.fixture
    def task(self):
        return BelafonteContentRankerJob(date=datetime.date(2018, 10, 3))

    def test_config(self, task):
        assert BelafonteContentRankerJob.package == "com.spotify.data.BelafonteContentRanker"
        assert BelafonteContentRankerJob.project == "acmacquisition"
        assert BelafonteContentRankerJob.region == "europe-west1"
        assert BelafonteContentRankerJob.autoscaling_algorithm == "THROUGHPUT_BASED"
        assert BelafonteContentRankerJob.max_num_workers == 25
        assert BelafonteContentRankerJob.beam

    def test_locations(self, task):
        assert task.staging_location == task.uri_prefix + "dataflow-staging/"
        assert task.temp_location == task.uri_prefix + "dataflow-temp/"

    def test_non_default_values(self, task):
        endpoint = "foo"
        uri_prefix = "gs://foo/"
        date = datetime.date(2018, 10, 3)
        task = BelafonteContentRankerJob(
            endpoint=endpoint, uri_prefix=uri_prefix, date=date
        )
        assert task.endpoint == endpoint
        assert task.uri_prefix == uri_prefix
