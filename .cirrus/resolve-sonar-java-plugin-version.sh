#!/usr/bin/env bash

set -euo pipefail

function resolve_version() {
  local VERSION="${1:-}"
  local REPOSITORY
  if [[ -z "${VERSION}" ]]; then
    echo "Usage: resolve-sonar-java-plugin-version.sh <version>"
    return 1
  elif [[ "${VERSION}" =~ ^[0-9]+(\.[0-9]+)*$ ]]; then
    echo -n "${VERSION}"
    return 0
  elif [[ "${VERSION}" == "POM_PROPERTY" ]]; then
    sed -E -n 's/^ *<sonar\.java\.version>([^<]+)<\/sonar\.java\.version> *$/\1/p' pom.xml
    return 0
  elif [[ "${VERSION}" == "LATEST_RELEASE" ]]; then
    REPOSITORY="sonarsource-public-releases"
  elif [[ "${VERSION}" == "LATEST_MASTER" ]]; then
    REPOSITORY="sonarsource-public-builds"
  elif [[ "${VERSION}" == "LATEST_DOGFOOD" ]]; then
    REPOSITORY="sonarsource-dogfood-builds"
  else
    echo "Invalid version format: ${VERSION}"
    return 1
  fi
  local REPOX_URL="https://repox.jfrog.io/repox/${REPOSITORY}/org/sonarsource/java/sonar-java-plugin/maven-metadata.xml"
  local REPOX_AUTHORIZATION="Authorization: Bearer ${ARTIFACTORY_PRIVATE_PASSWORD:-$ARTIFACTORY_PASSWORD}"
  local LATEST_VERSION
  LATEST_VERSION="$(curl -slf -H "${REPOX_AUTHORIZATION}" -o - "${REPOX_URL}" | sed -E -n 's/^ *<latest>([^<]+)<\/latest> *$/\1/p')"
  if [[ -z "${LATEST_VERSION}" ]]; then
    echo "No version found for ${GROUP_ID}:${ARTIFACT_ID}"
    return 1
  fi
  echo -n "${LATEST_VERSION}"
}

resolve_version "$@"
