# Bucketeer  &nbsp;[![Build Status](https://travis-ci.com/UCLALibrary/bucketeer.svg?branch=master)](https://travis-ci.com/UCLALibrary/bucketeer) [![Known Vulnerabilities](https://img.shields.io/snyk/vulnerabilities/github/uclalibrary/bucketeer.svg)](https://snyk.io/test/github/uclalibrary/bucketeer)

A TIFF to JP2 to S3 bucket microservice. It will read a TIFF file from the file system, turn it into a lossy JP2 image, and upload that image into a configurable S3 bucket. To use this project, you need to have a copy of the Kakadu binaries on your system's PATH.

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

Once run, the service can be verified/accessed at [http://localhost:8888/ping](http://localhost:8888/ping). The API documentation can be accessed at [http://localhost:8888/docs](http://localhost:8888/docs)

## Testing Considerations

If you do not have Kakadu installed or an S3 bucket to push JP2 images too, you can still run the tests that are a part of the build. In the results, you'll just see that there were a couple of tests that were skipped.

If you have a copy of Kakadu and would like for tests related to it to be run, you can install it on your system and the tests should pick it up. If you have several copies of Kakadu and you'd like to tell the tests which one to use you can set the `KAKADU_HOME` environmental variable and point it to the Kakadu binaries you want to use.

If you have an S3 account you'd like to use for testing, you can copy the `bucketeer.s3.*` settings out of the sample settings.xml file in `src/test/resources` and copy them into your own settings.xml file (perhaps at `/etc/maven/settings.xml`), supplying the values from your own S3 account.

## Contact

We use an internal ticketing system, but we've left the GitHub [issues](https://github.com/UCLALibrary/bucketeer/issues) open in case you'd like to file a ticket or make a suggestion. You can also contact Kevin S. Clarke at <a href="mailto:ksclarke@ksclarke.io">ksclarke@ksclarke.io</a> if you have a question about the project.
