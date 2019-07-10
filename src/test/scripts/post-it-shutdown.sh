#! /bin/bash

# And we do a little clean up after the integration tests have been run
kill `cat bucketeer-it.pid`
rm bucketeer-it.pid