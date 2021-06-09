#! /bin/bash

#
# Fake AWS Lambda responses, useful for testing Bucketeer.
#
# Usually a Bucketeer batch job has to wait for Lambda to respond, but if you don't want to
# actually send the requests off to Lambda this script will read the job you supply and send
# a bunch of fake processing results to Bucketeer so that you can see how the job finishes.
#
# This is only for development purposes and thus assumes you are running at http://localhost:8888
#
# This pattern can also be used to clear Bucketeer jobs that are stuck on a real server; for that, type the full URL:
#   curl -X PATCH "https://bucketeer.library.ucla.edu/batch/jobs/minasian-pages-3/ark%3A%2F21198%2Fzz002gp8t4/true"
#
# The quotation marks around the URL are important.
#

if [ -z "$1" ]; then
  echo "Please include a job name when you run this script:"
  echo "  ./fake-lambda.sh my-job-name"
  exit 1
fi

for id in `curl -s "http://localhost:8888/batch/jobs/${1}" | jq -r '.jobs[] | select(."status" == "")."image-id"'`; do
  encodedID=$(echo $id | sed 's|/|%2F|g')
  curl -X PATCH http://localhost:8888/batch/jobs/${1}/${encodedID}/true
done
