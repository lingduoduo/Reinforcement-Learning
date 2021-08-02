import luigi
from luigi.contrib.bigquery import BigqueryTarget
from luigi import ExternalTask


class DatedBigqueryTarget(BigqueryTarget):
    def __init__(self, project_id, dataset_id, date, table_id, client=None):
        full_table_id = '%s_%s' % (table_id, date.strftime('%Y%m%d'))
        super(DatedBigqueryTarget, self).__init__(project_id, dataset_id, full_table_id, client)


class BigQueryDailySnapshot(ExternalTask):
    date = luigi.DateParameter()
    project = luigi.Parameter()
    dataset = luigi.Parameter()
    table = luigi.Parameter(default=None)

    def output(self):
        return DatedBigqueryTarget(self.project, self.dataset, self.date, self.table)
