=========================
## ai-platform
=========================
  
>>> Training models using ai-platform repo - 
https://ghe.spotify.net/tech-research/ai-platform-example
https://ghe.spotify.net/bart/bart-transform/blob/master/tftransform_exp/train_evaluate.py
https://ghe.spotify.net/lingh/ai-platform

DONE
----------------------------------------------------------------------------------------------------------------------

ID                                    CREATE_TIME                DURATION  SOURCE                                                                                IMAGES                                               STATUS
7a6dfd34-ae0c-4b2f-8ebe-4434ab848342  2019-08-20T22:11:14+00:00  10M55S    gs://paradox-mo_cloudbuild/source/1566339073.99-8fa157f5d2bb468baa3fcf58570502a3.tgz  gcr.io/paradox-mo/ai-platform-example-gpu (+1 more)  SUCCESS
+ cleanup
+ cd popd
./scripts/build-docker-image.sh: line 7: cd: popd: No such file or directory

-> Install pyenv
 2943  brew install pyenv-virtualenv
 2952  pyenv install 3.7.0

 -> Activate
pyenv virtualenv 3.7.0 my-virtual-env-3.7.0
pyenv virtualenvs

exec $SHELL
eval "$(pyenv init -)"
eval "$(pyenv virtualenv-init -)"
pyenv activate my-virtual-env-3.7.0

pip install --upgrade pip
pip install --upgrade tensorflow==2.0.0-beta1
pip install --upgrade spotify-tensorflow
pip install --upgrade apache-beam

spotify-tensorflow 0.6.7 has requirement tensorflow==1.13.1, but you'll have tensorflow 2.0.0b1 which is incompatible.
Installing collected packages: tf-estimator-nightly, tb-nightly, tensorflow
  Found existing installation: tensorflow 1.13.1
    Uninstalling tensorflow-1.13.1:
      Successfully uninstalled tensorflow-1.13.1
Successfully installed tb-nightly-1.14.0a20190603 tensorflow-2.0.0b1 tf-estimator-nightly-1.14.0.dev2019060501


在~/.zshrc中加入: 
setopt no_nomatch, 然后进行source .zshrc命令

gcloud components install beta

docker run busybox date

gcloud auth configure-docker


https://ghe.spotify.net/ads/adserver-data-pipelines/blob/master/adserver-data-pipelines/src/main/python/base.py


>> build and test your Docker container locally
gcloud builds submit --config docker/cloudbuild.yaml --substitutions=_IMAGE=gcr.io/paradox-mo/ai-platform-example-gpu

...
latest: digest: sha256:c9a0b28b16d2aafad0bf1c79afc501805cb440a78993db594fc1998b5ff0a054 size: 4935
DONE
----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------

ID                                    CREATE_TIME                DURATION  SOURCE                                                                               IMAGES                                               STATUS
4ec06399-162d-4f20-87aa-6674ad4504aa  2019-08-21T18:08:11+00:00  2M2S      gs://paradox-mo_cloudbuild/source/1566410890.7-f51d4acb0d3c4835ab6f0d9902e2a850.tgz  gcr.io/paradox-mo/ai-platform-example-gpu (+1 more)  SUCCESS
+ cleanup
+ cd popd
./scripts/build-docker-image.sh: line 7: cd: popd: No such file or directory

docker run gcr.io/paradox-mo/ai-platform-example-gpu --epochs 1

>> push the container to container registry

docker push gcr.io/paradox-mo/ai-platform-example-gpu:latest 

>> submit and monitor the job

export MODEL_DIR=pytorch_model_$(date +%Y%m%d_%H%M%S)
export REGION=us-central1
export JOB_NAME=custom_container_job_$(date +%Y%m%d_%H%M%S)
export IMAGE_URI=gcr.io/paradox-mo/ai-platform-example-gpu

./scripts/train.sh $JOB_NAME

gcloud beta ai-platform jobs submit training $JOB_NAME \
  --region $REGION \
  --master-image-uri $IMAGE_URI \
  -- \
  --model-dir=gs://$BUCKET_NAME/$MODEL_DIR \
  --epochs=10

➜  ai-platform-example git:(ml-testing-ai) ✗ ./scripts/train.sh $JOB_NAME
+ JOB_ID=custom_container_job_20190821_142038
+ HYPERTUNE_ARG=x
+ REGION=us-east1
+ IMAGE_URI=gcr.io/paradox-mo/ai-platform-example-gpu
+ STAGING_BUCKET=mo_ml/lingh
+ PACKAGE=ai-platform-example
+ MODULE=ai_platform_example.train
+ [[ x == \h\y\p\e\r\t\u\n\e ]]
+ HYPERTUNE_PARAM=
+++ dirname ./scripts/train.sh
++ pushd ./scripts
++ pwd
++ popd
+ SCRIPT_DIR=/Users/lingh/Git/ML/ai-platform-example/scripts
+ gsutil -mq rm -r gs://mo_ml/lingh/ai-platform-example
CommandException: 1 files/objects could not be removed.
+ true
+ gsutil -m rsync -r -x '((.*/|^)\..*|.*\.ipynb)' /Users/lingh/Git/ML/ai-platform-example/scripts/.. gs://mo_ml/lingh/ai-platform-example/
Building synchronization state...


Operation completed over 21 objects/27.5 KiB.
+ gcloud beta ai-platform jobs submit training custom_container_job_20190821_142038 --region us-east1 --scale-tier BASIC_GPU --master-image-uri gcr.io/paradox-mo/ai-platform-example-gpu -- ai-platform-example ai_platform_example.train --job-id=custom_container_job_20190821_142038
Job [custom_container_job_20190821_142038] submitted successfully.
>>> Your job is still active. You may view the status of your job with the command

  $ gcloud ai-platform jobs describe custom_container_job_20190821_142038

or continue streaming the logs with the command

  $ gcloud ai-platform jobs stream-logs custom_container_job_20190821_142038
jobId: custom_container_job_20190821_142038
state: QUEUED
+ gcloud ai-platform jobs describe custom_container_job_20190821_142038
createTime: '2019-08-21T18:29:07Z'
etag: yj3IaCrczg0=
jobId: custom_container_job_20190821_142038
state: PREPARING
trainingInput:
  args:
  - ai-platform-example
  - ai_platform_example.train
  - --job-id=custom_container_job_20190821_142038
  masterConfig:
    imageUri: gcr.io/paradox-mo/ai-platform-example-gpu
  region: us-east1
  scaleTier: BASIC_GPU
trainingOutput: {}

View job in the Cloud Console at:
https://console.cloud.google.com/mlengine/jobs/custom_container_job_20190821_142038?project=paradox-mo

View logs at:
https://console.cloud.google.com/logs?resource=ml.googleapis.com%2Fjob_id%2Fcustom_container_job_20190821_142038&project=paradox-mo



>> Train Model locally

pip install -e .[cpu]  # install the package in develop mode
python -m ai_platform_example.train --configuration local
tensorboard --logdir=/tmp/ai-platform-example/tensorboard


>> Train Model on AI Platform
./scripts/train.sh lingh_ai_platform_test1

tensorboard --logdir=gs://andreasj-adhoc/ai-platform-example/tensorboard/andreasj_ai_platform_test1


gcloud ml-engine local train \
    --job-dir gs://slayton_test/email_labels/tf/jobs/20190211/15 \
    --module-name trainers.linear_model \
    --package-path tf-supervised/src/main/python/trainers --distributed \
    -- \
    --labeled-data gs://slayton_test/email_labels/tf/examples/20190211 \
    --verbosity DEBUG \
    --tf-transform-dir gs://slayton_test/preprocess/20190211 
    --schema-txt-file gs://slayton_test/email_labels/tf/examples/20190211/training/schema.pbtxt```

Cloud AI Platform API.


Based on tfrecords
gsutil ls gs://mo_ml/lingh/tfrecords/pdx_mo.Push.BaseInputDataV1.train/2019-08-13/20190816T164036.125362-d670cf9b363e
gsutil ls gs://mo_ml/lingh/tfrecords/pdx_mo.Push.BaseInputDataV1.evaluation/2019-08-13/20190816T164036.125082-58a84db69595

gsutil ls gs://mo_ml/lingh/tfrecords/pdx_mo.Push.BaseInputDataV1.train/
gsutil rm -r gs://$BUCKET_NAME


 3165  git add README.md
 3166  git add ai_platform_example/config.py
 3167  git add docker/ai-platform-staging.json
 3168  git add docker/start.sh
 3169  git scripts/build-docker-image.sh
 3170  git add scripts/build-docker-image.sh
 3171  git add scripts/train.sh

>>> cloudml-samples

virtualenv --python python3 env
source env/bin/activate

https://github.com/GoogleCloudPlatform/cloudml-samples/blob/master/census/tf-keras/requirements.txt
numpy>=1.14
pandas>=0.22
six>=1.11
tensorflow>=1.13,<2


gcloud ai-platform local train --package-path trainer --module-name trainer.task --job-dir local-training-output



ERROR: apache-beam 2.15.0 has requirement httplib2<=0.12.0,>=0.8, but you'll have httplib2 0.13.1 which is incompatible.
