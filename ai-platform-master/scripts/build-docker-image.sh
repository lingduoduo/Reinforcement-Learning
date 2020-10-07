#!/bin/bash -eux

IMAGE=gcr.io/paradox-mo/ai-platform-example-gpu
SCRIPT_DIR="$(pushd "$(dirname "${BASH_SOURCE[0]}")" > /dev/null && pwd && popd > /dev/null)"

function cleanup() {
    cd popd
}

pushd $SCRIPT_DIR/..
trap cleanup EXIT

gcloud builds submit --config "docker/cloudbuild.yaml" --substitutions=_IMAGE=$IMAGE
