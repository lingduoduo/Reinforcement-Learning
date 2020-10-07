#!/bin/bash -eux

# Usage: ./start.sh PACKAGE MODULE

# This script is intended to be executed as the ENTRYPOINT of a
# Dockerfile The PACKAGE needs to be uploaded to
# gs://miq-ai-platform-staging/ and the ENTRYPOINT should be the main
# python module that starts training, e.g. my_module.train if the
# train script is located in PACKAGE/my_module/train.py

gcloud auth activate-service-account --key-file=/ai-platform-staging.json

echo arguments: $@

PACKAGE=$1
shift

MODULE=$1
shift

pushd /tmp

# download package from staging bucket
gsutil -m cp -r gs://mo_ml/lingh/$PACKAGE .
cd $PACKAGE
pip install .[gpu]

popd

python -m $MODULE $@
