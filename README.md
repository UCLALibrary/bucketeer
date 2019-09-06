# Bucketeer  &nbsp;[![Build Status](https://travis-ci.com/UCLALibrary/bucketeer.svg?branch=master)](https://travis-ci.com/UCLALibrary/bucketeer) [![Known Vulnerabilities](https://img.shields.io/snyk/vulnerabilities/github/uclalibrary/bucketeer.svg)](https://snyk.io/test/github/uclalibrary/bucketeer)

A TIFF to JP2 to S3 bucket microservice. It will read a TIFF file from the file system, turn it into a lossy JP2 image, and upload that image into a configurable S3 bucket. To use this project, you need to have a copy of the Kakadu binaries on your system's PATH.

## Requirements

* A [Slack](https://slack.com/) Team, and a Slack [app](https://api.slack.com/start/overview), with at least one channel, named `dev-null` [configured to support file uploads](https://api.slack.com/messaging/files/setup) from that app. NOTE the name `dev-null` is significant for the service, and is hard-coded as part of a conditional. This will be addressed in a future refactor.
    * in order to run tests that use Slack, you should copy the `bucketeer.slack.*` settings out of the sample settings.xml file in `src/test/resources` and copy them into your own settings.xml file (perhaps at `/etc/maven/settings.xml`), supplying the values from your own Slack account.

* An [AWS](https://docs.aws.amazon.com/index.html?nc2=h_ql_doc) account with [S3 buckets](https://docs.aws.amazon.com/s3/?id=docs_gateway) created.
    * In order to run tests that use S3, you should copy the `bucketeer.s3.*` settings out of the sample settings.xml file in `src/test/resources` and copy them into your own settings.xml file (perhaps at `/etc/maven/settings.xml`), supplying the values from your own S3 account.

* A valid license for [Kakadu](https://kakadusoftware.com/), and Kakadu binaries installed
    * in order to run tests that use Kakadu, you should install Kakadu on your system and the tests should pick it up. If you have several copies of Kakadu and you'd like to tell the tests which one to use you can set the `KAKADU_HOME` environmental variable and point it to the Kakadu binaries you want to use.

## Building the Project

The project builds an executable Jar that can be run to start the microservice. To build the project, run:

    mvn package

This will put the executable Jar in the `target/build-artifact` directory.

The application, in its simplest form, can be run with the following command:

    java -Dvertx-config-path=target/test-classes/test-config.properties -jar target/build-artifact/bucketeer-*.jar

To generate the site's Javadocs documentation, run:

    mvn site

This will generate the documentation in the `target/site` directory.

If you'd like to run Bucketeer in a Docker container, a [repository to build a Docker image](https://github.com/uclalibrary/docker-bucketeer) is available. Using it requires you to supply your own AWS credentials and to have a licensed copy of the Kakadu source code.

## Running the Application for Development

You can run a development instance of Bucketeer by typing the following within the project root:

    mvn -Plive test

Once run, the service can be verified/accessed at [http://localhost:8888/status](http://localhost:8888/status). The API documentation can be accessed at [http://localhost:8888/docs](http://localhost:8888/docs)

## Contact

We use an internal ticketing system, but we've left the GitHub [issues](https://github.com/UCLALibrary/bucketeer/issues) open in case you'd like to file a ticket or make a suggestion. You can also contact Kevin S. Clarke at <a href="mailto:ksclarke@ksclarke.io">ksclarke@ksclarke.io</a> if you have a question about the project.
