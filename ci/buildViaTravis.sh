#!/bin/bash
# This script will build the project.

if [ "$TRAVIS_PULL_REQUEST" != "false" ]; then
  echo -e "Build Pull Request #$TRAVIS_PULL_REQUEST => Branch [$TRAVIS_BRANCH]"
  ./gradlew -Pbranch="${TRAVIS_BRANCH}" build
elif [ "$TRAVIS_PULL_REQUEST" == "false" ] && [ "$TRAVIS_TAG" == "" ]; then
  echo -e 'Build Branch => Branch ['$TRAVIS_BRANCH']'
  ./gradlew -PbintrayUser="${bintrayUser}" -PbintrayKey="${bintrayKey}" -Pbranch="${TRAVIS_BRANCH}" build artifactoryPublish --stacktrace
else
  echo -e 'Build Tag => Tag ['$TRAVIS_TAG']'
  ./gradlew build
fi
