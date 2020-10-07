# AI Platform

Borrow copy codes from [Ai Platform Example](https://ghe.spotify.net/tech-research/ai-platform-example) for AI Platform.

[AI Platform](https://cloud.google.com/ai-platform/) is a sort of rebranding of ML Engine, with some added features. For the purposes of this tutorial, you can think of it as a Google-managed service for training machine learning models. AI Platform takes care of creating compute instances and shutting them down when you're done, and also has a nice built-in hyperparameter tuning module.

## Setup environments

The Docker inherits from the offical Tensorflow 2.0 GPU image and installs Python 3.7.

```
 lingh@Lings-MacBook-Pro  ~/Git/ML   master  python3 -m pip --version
pyenv: python3: command not found

The `python3' command exists in these Python versions:
  3.6.9
  3.6.9/envs/ml-golden-path
  3.7.0
  3.7.0/envs/my-virtual-env-3.7.0
  ml-golden-path
  my-virtual-env-3.7.0
  
  equirement already satisfied: werkzeug>=0.11.15 in /Users/lingh/.pyenv/versions/3.7.0/envs/my-virtual-env-3.7.0/lib/python3.7/site-packages (from tensorboard<1.15.0,>=1.14.0->tensorflow==1.14.0) (0.16.1)
Requirement already satisfied: h5py in /Users/lingh/.pyenv/versions/3.7.0/envs/my-virtual-env-3.7.0/lib/python3.7/site-packages (from keras-applications>=1.0.6->tensorflow==1.14.0) (2.10.0)
ERROR: spotify-kubeflow 0.1.3 has requirement numpy<1.17, but you'll have numpy 1.18.1 which is incompatible.
Installing collected packages: tensorboard, tensorflow-estimator, tensorflow

  pip uninstall spotify-kubeflow
  pip uninstall tensorflow
  pip install tensorflow==1.14.0
  pip install --force-reinstall tf-nightly
  pip install --force-reinstall tfp-nightly
  pip install xvfbwrapper
  pip install ffmpeg-python
```


ERROR: tfx-nightly-stripped 0.14.0 has requirement tensorflow-data-validation<0.14,>=0.13.1, but you'll have tensorflow-data-validation 0.14.1 which is incompatible.
ERROR: tfx-nightly-stripped 0.14.0 has requirement tensorflow-transform<0.14,>=0.13, but you'll have tensorflow-transform 0.14.0 which is incompatible.
ERROR: tensorflow 1.15.0 has requirement gast==0.2.2, but you'll have gast 0.3.3 which is incompatible.
ERROR: tensorflow-model-analysis 0.13.2 has requirement scipy==1.1.0, but you'll have scipy 1.4.1 which is incompatible.
ERROR: spotify-kubeflow 0.1.3 has requirement numpy<1.17, but you'll have numpy 1.18.1 which is incompatible.
ERROR: kfp 0.1.27 has requirement urllib3<1.25,>=1.15, but you'll have urllib3 1.25.8 which is incompatible.
ERROR: ai-platform-example 0.1.0 has requirement numpy==1.16.4, but you'll have numpy 1.18.1 which is incompatible.
ERROR: ai-platform-example 0.1.0 has requirement scipy==1.3.0, but you'll have scipy 1.4.1 which is incompatible.
Installing collected packages: numpy, six, h5py, gast, tf-estimator-nightly, grpcio, opt-einsum, keras-preprocessing, wheel, astunparse, absl-py, setuptools, protobuf, werkzeug, idna, chardet, urllib3, certifi, requests, cachetools, pyasn1


### Install pyenv

```
$ brew install pyenv-virtualenv
$ pyenv install 3.7.0
```

### Activate the virtual environment

```
pyenv virtualenv 3.7.1 message-ranking-env-3.7.1
```

```
exec $SHELL
eval "$(pyenv init -)"
eval "$(pyenv virtualenv-init -)"
pyenv activate my-virtual-env-3.7.0

virtualenv --python python3 env
source env/bin/activate

pip install --upgrade pip
pip install --upgrade tensorflow==1.14.0
pip install --upgrade spotify-tensorflow==0.7.4
pip install --upgrade protobuf==3.9.1
brew upgrade skaffold
pip install --upgrade apache-beam==2.15.0
pip install --upgrade spotify-kubeflow

pip install --force-reinstall tf-nightly
pip install --force-reinstall tfp-nightly
```

## Build the Docker image

AI Platform just runs Docker containers and has no idea what code you want to run. So by default, the only way to run new code is to rebuild the entire image. That's an incredibly frustrating developer experience, so in this Dockerfile I've hacked around it.

The container entrypoint, [start.sh](https://ghe.spotify.net/tech-research/ai-platform-example/blob/master/docker/start.sh), downloads code from a staging bucket. 

To build the image, we use [Cloud Build](https://cloud.google.com/cloud-build/). That's faster than building locally, and saves precious local disk space. The [scripts/build-docker-image.sh](https://ghe.spotify.net/tech-research/ai-platform-example/blob/master/scripts/build-docker-image.sh) script submits the Cloud Build job based on [docker/cloudbuild.yaml](https://ghe.spotify.net/tech-research/ai-platform-example/blob/master/docker/cloudbuild.yaml).

Update the image name from `gcr.io/audio-understanding/ai-platform-example-gpu` to something you have write access to, and run the build script:

```
$ ./scripts/build-docker-image.sh
```

## Implement the model

The model code in the [`ai_platform_example` folder](https://ghe.spotify.net/tech-research/ai-platform-example/tree/master/ai_platform_example) is fairly standard Tensorflow 2.0.

* [train.py](https://ghe.spotify.net/tech-research/ai-platform-example/blob/master/ai_platform_example/train.py) is the main entrypoint, that builds the model, loads the data, and starts training with callbacks for visualization, hyperparameter tuning, checkpointing, and early stopping.

* [data.py](https://ghe.spotify.net/tech-research/ai-platform-example/blob/master/ai_platform_example/data.py) loads the Mnist model and creates [tf.data.Datasets](https://www.tensorflow.org/api_docs/python/tf/data/Dataset) for training and validation.

* [model.py](https://ghe.spotify.net/tech-research/ai-platform-example/blob/master/ai_platform_example/model.py) defines a simple convnet with many configurable parameters.

* [visualize.py](https://ghe.spotify.net/tech-research/ai-platform-example/blob/master/ai_platform_example/visualize.py) saves TensorBoard charts of losses, as well as Matplotlib plots for debugging.

* [config.py](https://ghe.spotify.net/tech-research/ai-platform-example/blob/master/ai_platform_example/config.py) is a central, global configuration registry. Since it uses [effortless-config](https://github.com/andreasjansson/effortless-config), any configuration flags can be overridden on the command line.

## Train locally

An AI Platform job takes a few minutes to spin up, so you can save debugging time by running a smaller version of the experiment locally. [Effortless-config](https://github.com/andreasjansson/effortless-config) makes it easy to define groups of config flags for local testing.

In [config.py](https://ghe.spotify.net/tech-research/ai-platform-example/blob/master/ai_platform_example/config.py) we set `groups = ['local']`, which means that any setting accepts a `local=...` keyword argument.

To run the local configuration:

```
$ pip install -e .[cpu]  # install the package in develop mode

$ python -m ai_platform_example.train --configuration local
```

As we can see in [config.py](https://ghe.spotify.net/tech-research/ai-platform-example/blob/master/ai_platform_example/config.py), the `TENSORBOARD_DIR` setting is locally overridden to `'/tmp/ai-platform-example/tensorboard'`, which means that we can view TensorBoard locally on `localhost:6006` after running:

```
$ tensorboard --logdir=/tmp/ai-platform-example/tensorboard
```

## Train on AI Platform

Now we're almost ready to train our model on AI Platform, after the following code changes:

* Update the `TENSORBOARD_DIR` and `CHECKPOINT_DIR` settings in [config.py](https://ghe.spotify.net/tech-research/ai-platform-example/blob/master/ai_platform_example/config.py) to your own bucket.
* Update the `IMAGE_URI` in [scripts/train.sh](https://ghe.spotify.net/tech-research/ai-platform-example/blob/master/scripts/train.sh).

The [scripts/train.sh](https://ghe.spotify.net/tech-research/ai-platform-example/blob/master/scripts/train.sh) script is a thin wrapper around the [`gcloud beta ai-platform jobs submit training`](https://cloud.google.com/sdk/gcloud/reference/ai-platform/jobs/submit/training) command that first uploads your local code to the staging bucket (described in the Docker section above).

Start training by invoking this script one argument, corresponding to the job ID:

```
$ ./scripts/train.sh andreasj_ai_platform_test1
```

For some reason it's impossible to delete jobs on AI Platform, so ever time you have to come up with a new name. This usually means that you'll suffix your job name with a monotonically increasing number.

If everything goes well, the `train.sh` script outputs something along the lines of

```
[...]

View job in the Cloud Console at:
https://console.cloud.google.com/mlengine/jobs/andreasj_ai_platform_example10?project=audio-understanding

View logs at:
https://console.cloud.google.com/logs?resource=ml.googleapis.com%2Fjob_id%2Fandreasj_ai_platform_example10&project=audio-understanding
```

Visit that second link to follow the logs of your training job. It can take up to ten minutes for things to start happening.

You can also see the list of all jobs in the GCP GUI: [console.cloud.google.com/mlengine/jobs](https://console.cloud.google.com/mlengine/jobs). Click your job ID to see details and get to the link to view logs.

As defined in [config.py](https://ghe.spotify.net/tech-research/ai-platform-example/blob/master/ai_platform_example/config.py), the default configuration writes TensorBoard summaries to Google Cloud Storage. You can follow the logs during training using:

```
$ tensorboard --logdir=gs://andreasj-adhoc/ai-platform-example/tensorboard/andreasj_ai_platform_test1
```

Note however that TensorBoard is [very slow over GCS](https://github.com/tensorflow/tensorboard/issues/158), so you might want to download the logs locally first:

```
$ mkdir /tmp/ai-platform-tensorboard

$ gsutil -mq rsync -r gs://andreasj-adhoc/ai-platform-example/tensorboard/andreasj_ai_platform_test1 /tmp/ai-platform-tensorboard-sync/

$ tensorboard --logdir=/tmp/ai-platform-tensorboard-sync
```

Keras [Checkpoints](https://www.tensorflow.org/tutorials/keras/save_and_restore_models) are also saved to GCS, with the location specified by the `CHECKPOINT_DIR` setting in [config.py](https://ghe.spotify.net/tech-research/ai-platform-example/blob/master/ai_platform_example/config.py).

## Hyperparameter tuning

Before getting into hyperparameter tuning, have a read through [the official docs](https://cloud.google.com/ml-engine/docs/hyperparameter-tuning-overview).

The [scripts/train.sh](https://ghe.spotify.net/tech-research/ai-platform-example/blob/master/scripts/train.sh) script can be used to start a hyperparameter tuning job by adding a second `hypertune` argument, e.g.:

```
$ ./scripts/train.sh andreasj_ai_platform_test1 hypertune
```

If you read the [scripts/train.sh](https://ghe.spotify.net/tech-research/ai-platform-example/blob/master/scripts/train.sh) source code, you see that adding the `hypertune` flag adds `--config=job-config.yaml` to the gcloud command line.

It is in [job-config.yaml](https://ghe.spotify.net/tech-research/ai-platform-example/blob/master/job-config.yaml) that we define the hyperparameters to tune, as well as their allowed values and ranges. The `parameterName` is used on the command line by prefixing double dash, e.g. `parameterName: dropout-rate` becomes `--dropout-rate NNN`. This plays nicely with config.py, since [effortless-config](https://github.com/andreasjansson/effortless-config) turns every configuration setting into a command line argument by lowercasing, replacing underscores with dashes, and prefixing a double dash.

Once we've started training, we can follow progress in TensorBoard as before:

```
$ tensorboard --logdir=gs://andreasj-adhoc/ai-platform-example/tensorboard/andreasj_ai_platform_test1
```

Each trial will have its own subdirectory under the job directory, e.g. `gs://andreasj-adhoc/ai-platform-example/tensorboard/andreasj_ai_platform_test1/1`, which means that TensorBoard will display all trials in the same interface.
