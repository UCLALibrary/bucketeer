#!/bin/bash

# The following vars are stored encrypted in TravisCI
# JENKINS_USER
# JENKINS_USER_PASS
# JENKINS_URL

# Trigger latest dockerhub build if commit is on master branch
if [[ $TRAVIS_BRANCH == 'master' && $TRAVIS_PULL_REQUEST == 'false' ]]; then
  curl -I -u "${JENKINS_USER}:${JENKINS_USER_PASS}" "${JENKINS_URL}"
  # Else if it's a tag, trigger a tag build
elif [[ $TRAVIS_TAG != "" && $TRAVIS_PULL_REQUEST == 'false' ]]; then
  curl -I -u "${JENKINS_USER}:${JENKINS_USER_PASS}" "${JENKINS_URL}&BUCKETEER_VERSION=${TRAVIS_TAG}"
  # Else we're assuming this is using another branch other than master
elif [[ $TRAVIS_BRANCH != "master" && $TRAVIS_PULL_REQUEST == 'false' && $TRAVIS_TAG == "" ]]; then
  curl -I -u "${JENKINS_USER}:${JENKINS_USER_PASS}" "${JENKINS_URL}&BUCKETEER_VERSION=${TRAVIS_BRANCH}"
fi
