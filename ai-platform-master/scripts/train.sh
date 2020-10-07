#!/bin/bash -eux

# Usage: ./train.sh JOB_ID [hypertune]

JOB_ID=$1  # needs to be updated every time
HYPERTUNE_ARG=${2-x}

REGION=us-east1
IMAGE_URI=gcr.io/paradox-mo/ai-platform-example-gpu
STAGING_BUCKET=mo_ml/lingh
PACKAGE=ai-platform-example
MODULE=ai_platform_example.train

if [[ $HYPERTUNE_ARG == 'hypertune' ]]; then
    HYPERTUNE_PARAM="--config=job-config.yaml"
else
    HYPERTUNE_PARAM=""
fi

SCRIPT_DIR="$(pushd "$(dirname "${BASH_SOURCE[0]}")" > /dev/null && pwd && popd > /dev/null)"

# upload code to working directory (excluding ipython notebooks and dotfiles/"dot-dirs")
gsutil -mq rm -r gs://$STAGING_BUCKET/$PACKAGE || true
gsutil -m rsync -r -x '((.*/|^)\..*|.*\.ipynb)' "$SCRIPT_DIR/.." gs://$STAGING_BUCKET/$PACKAGE/

# submit job
gcloud beta ai-platform jobs submit training $JOB_ID \
  $HYPERTUNE_PARAM \
  --region $REGION \
  --scale-tier BASIC_GPU \
  --master-image-uri $IMAGE_URI \
  -- \
  $PACKAGE $MODULE \
  --job-id=$JOB_ID

# show information about started job
gcloud ai-platform jobs describe $JOB_ID
