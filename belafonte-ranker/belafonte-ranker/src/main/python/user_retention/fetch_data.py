from bqt import bqt


def fetch_training_data(start_dt, end_dt):
    query = """WITH adjust as (
        SELECT
            user_id,
            metadata_campaign_name as campaign_name,
            content_uri
        FROM `acmacquisition.bqr.facebook_adjust_*`
        WHERE user_id != ' '
        AND content_uri is not NULL
        AND registration_date >= PARSE_DATE('%Y-%m-%d','{start_dt}')
        AND registration_date <= PARSE_DATE('%Y-%m-%d','{end_dt}')
        UNION ALL
        SELECT
            user_id,
            meta_campaign_name as campaign_name,
            content_uri
        FROM `acmacquisition.bqr.display_adjust_*`
        WHERE user_id != ' '
        AND content_uri != ' '
        AND registration_date >= PARSE_DATE('%Y-%m-%d','{start_dt}')
        AND registration_date <= PARSE_DATE('%Y-%m-%d','{end_dt}')
        )

        SELECT
            a.*,
            wau,
            d1,
            d2
        FROM adjust a
        INNER JOIN `acmacquisition.user_retention_adjust.user_retention_adjust_*` b
        ON a.user_id = b.user_id
        WHERE _table_suffix
        BETWEEN FORMAT_DATE('%Y%m%d', PARSE_DATE('%Y-%m-%d', '{start_dt}'))
        AND FORMAT_DATE('%Y%m%d', PARSE_DATE('%Y-%m-%d', '{end_dt}'))
    """
    bqt.set_param("start_dt", start_dt)
    bqt.set_param("end_dt", end_dt)
    df = bqt.query(query)
    return df
