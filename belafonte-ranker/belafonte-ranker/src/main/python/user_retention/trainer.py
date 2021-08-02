from sklearn.model_selection import train_test_split
import pandas as pd
from random import random
import xgboost as xgb


def training_models(preprocessed_data, scoring_data, transformed_scoring_data):
    del_attribs = [
        "user_id",
        "campaign_name",
        "content_uri",
        "wau",
    ]
    config = {
        "verbosity": 0,
        "num_threads": 1,
        "objective": "binary:logistic",
        "booster": "gbtree",
        "eval_metric": ["auc"],
        "max_depth": 4,
        "eta": 0.1,
        "gamma": 1
    }

    target = (preprocessed_data["wau"] == 1)
    data = preprocessed_data.drop(del_attribs, 1)
    train_x, test_x, train_y, test_y = train_test_split(data, target, test_size=0.3, random_state=42)

    train_set = xgb.DMatrix(train_x, label=train_y)
    test_set = xgb.DMatrix(test_x, label=test_y)
    bst = xgb.train(config, train_set, evals=[(test_set, "eval")])

    target = (transformed_scoring_data["wau"] == 1)
    transformed_scoring_data = transformed_scoring_data.drop(del_attribs, 1)
    test_set = xgb.DMatrix(transformed_scoring_data, label=target)

    scoring_data["score"] = 1 - bst.predict(test_set)
    scoring_data["score"] += [random() * 10 ** (-4) for _ in scoring_data["score"]]
    results = (pd.DataFrame(scoring_data.groupby(["campaign_name", "content_uri"])["score"]
                            .mean().reset_index()))
    results['rank'] = results.groupby('campaign_name')['score'].rank(method='first')
    return results
