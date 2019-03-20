# JP2 Bucketeer

A TIFF to JP2 to S3 bucket microservice. It will read a TIFF file from the file system, turn it into a lossy JP2 image, and upload that image into a configurable S3 bucket. This is a work in progress.

## Building the Project

The project builds an executable Jar that can be run to start the microservice. To build the project, run:

    mvn package

This will put the executable Jar in the `target/build-artifact` directory.

The application, in its simplest form, can be run with the following command:

    java -jar target/build-artifact/jp2-bucketeer-*.jar

To generate the site's documentation, run:

    mvn site

This will generate the documentation in the `target/site` directory.

## Running the Application for Development

You can run a development instance of JP2 Bucketeer by typing the following within the project root:

    mvn vertx:initialize vertx:run

This instance will be refreshed when the code changes so it will reflect the current state of the code. The service can be verified/accessed at [http://localhost:8888/ping](http://localhost:8888/ping).

## Contact

We use an internal ticketing system, but we've left the GitHub [issues](https://github.com/UCLALibrary/jp2-bucketeer/issues) open in case you'd like to file a ticket or make a suggestion. You can also contact Kevin S. Clarke at <a href="mailto:ksclarke@ksclarke.io">ksclarke@ksclarke.io</a> if you have a question about the project.
