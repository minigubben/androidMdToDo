#!/usr/bin/env bash
set -euo pipefail

TASKS="${*:-assembleDebug}"
IMAGE_TAG="android-mdtodo-build:local"

docker build \
  --build-arg GRADLE_TASKS="$TASKS" \
  -f Dockerfile.android \
  -t "$IMAGE_TAG" \
  .

CONTAINER_ID="$(docker create "$IMAGE_TAG")"
trap 'docker rm -f "$CONTAINER_ID" >/dev/null 2>&1 || true' EXIT

mkdir -p app/build
rm -rf app/build/outputs app/build/reports
docker cp "$CONTAINER_ID:/out/app/build/outputs" app/build/outputs
if docker cp "$CONTAINER_ID:/out/app/build/reports" app/build/reports 2>/dev/null; then
  :
fi
