#!/usr/bin/env bash
#
# Build and push the FixTestDriver Docker images as multi-arch (amd64 + arm64)
# manifests to Docker Hub.
#
# Usage:
#   ./build-and-push.sh              # build + push :latest
#   NAMESPACE=myuser ./build-and-push.sh
#   TAG=1.2.0 ./build-and-push.sh    # push :1.2.0 in addition to :latest
#
set -euo pipefail

# --- Configuration ---------------------------------------------------------
NAMESPACE="${NAMESPACE:-lamiaconsultancy}"
TAG="${TAG:-latest}"
PLATFORMS="${PLATFORMS:-linux/amd64,linux/arm64}"
BUILDER="${BUILDER:-multiarch}"

BACKEND_IMAGE="${NAMESPACE}/fixtestdriver-backend"
UI_IMAGE="${NAMESPACE}/fixtestdriver-frontend"

# Resolve the directory this script lives in, so it works from anywhere.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo ">> Namespace : ${NAMESPACE}"
echo ">> Tag       : ${TAG}"
echo ">> Platforms : ${PLATFORMS}"
echo

# --- Ensure a multi-arch capable builder exists ----------------------------
# The default 'docker' driver cannot emit multi-arch manifests, so we use a
# dedicated 'docker-container' driver builder.
if ! docker buildx inspect "${BUILDER}" >/dev/null 2>&1; then
  echo ">> Creating buildx builder '${BUILDER}'..."
  docker buildx create --name "${BUILDER}" --driver docker-container --bootstrap
fi
docker buildx use "${BUILDER}"

# --- Build + push ----------------------------------------------------------
build() {
  local context="$1" image="$2"
  echo
  echo ">> Building ${image}:${TAG} (${PLATFORMS})"
  docker buildx build \
    --platform "${PLATFORMS}" \
    --tag "${image}:${TAG}" \
    --push \
    "${context}"
}

build "${ROOT}/modern"    "${BACKEND_IMAGE}"
build "${ROOT}/modern-ui" "${UI_IMAGE}"

# --- Verify ----------------------------------------------------------------
echo
echo ">> Published manifests:"
for image in "${BACKEND_IMAGE}" "${UI_IMAGE}"; do
  echo "== ${image}:${TAG} =="
  docker buildx imagetools inspect "${image}:${TAG}" \
    | grep -iE "Platform" | grep -iv "unknown" || true
  echo
done

echo ">> Done."
