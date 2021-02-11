## Bucketeer
[![Maven Build](https://github.com/uclalibrary/bucketeer/workflows/Maven%20PR%20Build/badge.svg)](https://github.com/UCLALibrary/bucketeer/actions) [![Known Vulnerabilities](https://img.shields.io/snyk/vulnerabilities/github/uclalibrary/bucketeer.svg)](https://snyk.io/test/github/uclalibrary/bucketeer)
 
A TIFF to JP2/JPX to S3 bucket microservice. It will turn TIFF images into JPEG 2000 images in two ways:

1) The first way is to convert individual TIFF images into JPEG 2000 images on the local machine. To do this Bucketeer receives individual requests, accesses the TIFFs from a mounted directory, converts TIFFs into JPEG 2000 images, and then uploads them to S3. This method is triggered by a RESTful API.

2) The second way is to convert TIFF images into JPEG 2000 images in batch. With this method, a CSV file is uploaded to Bucketeer and TIFF images from the CSV file, available to Bucketeer from a locally mounted directory, are uploaded to an S3 bucket. An AWS Lambda function picks up on that event and converts the TIFFs into JPEG 2000s. Lastly, the Bucketeer Lambda function stores the JPEG 2000 images in another S3 bucket. This method is triggered by uploading a CSV file through a Web page on the Bucketeer site.

Currently, the CSV upload method is hard-coded for UCLA's particular metadata model. This will be changed to make the process more generic. Examples of UCLA's metadata fields can be found in CSVs in the project's test resources. In the future, there will be actual documentation describing the more generic approach.

## Requirements

* A [Slack](https://slack.com/) Team and a Slack [bot](https://api.slack.com/start/overview), with at least one channel [configured to support file uploads](https://api.slack.com/messaging/files/setup).
    * In order to run tests that use Slack, you should copy the `bucketeer.slack.*` settings out of the sample settings.xml file in `src/test/resources` and copy them into your own settings.xml file (perhaps at `/etc/maven/settings.xml`), supplying the values from your own Slack account.

* An [AWS](https://docs.aws.amazon.com/index.html?nc2=h_ql_doc) account with [S3 buckets](https://docs.aws.amazon.com/s3/?id=docs_gateway) created.
    * In order to run tests that use S3, you should copy the `bucketeer.s3.*` settings out of the sample settings.xml file in `src/test/resources` and copy them into your own settings.xml file (perhaps at `/etc/maven/settings.xml`), supplying the values from your own S3 account.

* A valid license for [Kakadu](https://kakadusoftware.com/)
    * In order to make your copy of Kakadu available to the build, it needs to be placed in its own GitHub repository with the name of the version you've licensed in the root directory (i.e., the root directory will contain a directory named something like `v7_A_7-01642E`). Our current build has only been tested with Kakadu v7. We don't yet support v8. For more details, see the Kakadu section below.

* An installation of [kakadu-lambda-converter](https://github.com/UCLALibrary/kakadu-lambda-converter/) (to use the batch conversion)
    * See that project's GitHub page for information about how to install it.

## Building the Project

The project builds an executable Jar that can be run to start the microservice. To build the project, run:

    mvn package

This will put the executable Jar in the `target/build-artifact` directory.

The application, in its simplest form, can be run with the following command:

    java -Dvertx-config-path=target/test-classes/test-config.properties -jar target/build-artifact/bucketeer-*.jar

To generate the site's Javadocs documentation, run:

    mvn site

This will generate the documentation in the `target/site` directory.

If you'd like to run Bucketeer in a Docker container, you need to have Docker installed and working on your system. To run the version of the build that creates a Docker container and runs tests against that, type:

    mvn verify

_Hint: If you want to run a build without a Docker cache, add -Ddocker.noCache to your mvn command; for instance: `mvn verify -Ddocker.noCache`_

You can also specify that only a certain set of tests be run against the test containers. To do this, supply a runtime argument that excludes a set of tests:

    -DskipUTs
    -DskipITs
    -DskipFTs
    -DskipFfTs

The first will skip the unit tests; the second will skip the integration tests; the third will skip the functional tests; and, the fourth will skip the feature flag tests. They can also be combined so that two types of tests are skipped. For instance, only the feature flag tests will be run if the following is typed:

    mvn verify -DskipUTs -DskipITs -DskipFTs

To see logging from the containers that are being run for the tests, consult the docker-maven-plugin [documentation](https://dmp.fabric8.io/#docker:logs). This is also useful when running the containers for manual testing, which is described below.

## Running the Bucketeer container

The simplest way to run the newly built Bucketeer container (for development purposes) is to use the Maven Docker plugin. To do that, run:

    mvn docker:start

This will output logging that will tell you what random port Bucketeer has been started on (e.g. http://localhost:32772). If you visit the URL found in the logging output in your browser, you will see the Bucketeer landing page. If you haven't changed the `image.root` location, `src/test/resources/images` will be the source of your images. There are some sample images there used for testing.

If you'd like to change the location where Bucketeer will look for images (to your own test images), you can start the container with a custom `image.root` location:

    mvn docker:start -Dimage.root=/path/to/your/imageroot

To stop the Bucketeer container, when you are done testing, you should run:

    mvn docker:stop

You can always see which containers are running by using Docker's `docker ps` command.

## Mocking AWS Lambda's Response

When you run the Bucketeer container on a local machine, there is no connected AWS Lambda function. While we hope in the future to orchestrate a fully functional testing environment, where the developer can mock AWS Lambda for testing through the use of [TestContainers](https://www.testcontainers.org/), in the meantime, there is a Bash script that can be run to "mock" the interaction with AWS Lambda. This is only relevant when one is testing batch jobs against Bucketeer. Individual image conversion jobs do not use AWS Lambda and so don't require any mocking of that interaction.

To use this testing script, first submit a batch job to your local Bucketeer instance. This will initiate the job and put Bucketeer in a state of waiting for feedback from AWS Lambda. In this project's `src/test/scripts` directory, there is a Bash script called `fake-lambda.sh`. That can be run with `src/test/scripts/fake-lambda.sh [my-job-name]` where `my-job-name` is the name of the CSV file you've uploaded, minus the ".csv" extension.

When you run this script, it will get a listing of all the images to be converted as a part of the batch job and report back to Bucketeer's status update endpoint with successful conversion statuses for each. Once all have been reported as successfully converted, the batch job will conclude and the developer will be able to complete the manual test. This allows end-to-end manual testing of Bucketeer without actually setting up a test instance of the AWS Lambda function (specific to the S3 bucket that the developer has configured Bucketeer to use in its manual tests).

It's also possible, of course, to set up one's own instance of the Lambda function and to configure that to interact with the Bucketeer service. For more information on that possibility, consult the documentation on the GitHub page for the [kakadu-lambda-converter](https://github.com/UCLALibrary/kakadu-lambda-converter).

## Running the Application for Development

You can run a development instance of Bucketeer by typing the following within the project root:

    mvn -Plive test

Once run, the service can be verified/accessed at [http://localhost:8888/status](http://localhost:8888/status). The API documentation can be accessed at [http://localhost:8888/docs](http://localhost:8888/docs)

If you want to run the application with a different mount point (for image sources) and file prefix (e.g. the UCLA file path prefix), you can use something like:

    mvn -Plive test -Dbucketeer.fs.image.mount=/opt/data -Dbucketeer.fs.image.prefix=UCLAFilePathPrefix

If you leave off the `bucketeer.fs.image.prefix` Bucketeer will treat the `bucketeer.fs.image.mount` as the default directory.

## Including Kakadu

To build an image that includes Kakadu, supply two additional build parameters: the repository and the version number; this should look something like:

    mvn verify -Dkakadu.git.repo=scm:git:git@github.com:uclalibrary/kakadu.git -Dkakadu.version=v7_A_7-01642E

Once you've done this, you'll get the following warning:

    warning: adding embedded git repository: src/main/docker/kakadu
    hint: You've added another git repository inside your current repository.
    hint: Clones of the outer repository will not contain the contents of
    hint: the embedded repository and will not know how to obtain it.
    hint: If you meant to add a submodule, use:
    hint: 
    hint:   git submodule add <url> src/main/docker/kakadu
    hint: 
    hint: If you added this path by mistake, you can remove it from the
    hint: index with:
    hint: 
    hint:   git rm --cached src/main/docker/kakadu
    hint: 
    hint: See "git help submodule" for more information.

This is what you want. You do not want to add your Kakadu code as a submodule since the repository is private and should not be linked to this project's code.

UCLA developers only need to supply the correct `kakadu.version` v7 value. The build is set up to use our private Kakadu GitHub repository by default. Non-UCLA developers should not supply `kakadu.version` without also supplying `kakadu.git.repo`, since the UCLA Kakadu repository is a private repository that cannot be accessed by others.

It's important to remember that if you build a Docker container with `kakadu.version`, you must also supply that same argument when you run the `mvn docker:start` and `mvn docker:stop` commands. They will look something like:

    mvn docker:start -Dkakadu.version=v7_A_7-01642E

and

    mvn docker:stop -Dkakadu.version=v7_A_7-01642E

You do not need to supply the `kakadu.git.repo` argument when just starting or stopping your previously built Kakadu-enabled containers. That's only needed at the point of building them.

Kakadu is only needed if you want to do Kakadu in Bucketeer, instead of using Bucketeer to send TIFFs to AWS Lambda to process.

## Testing Locally with Kakadu

The Kakadu instructions above are for including Kakadu in the Bucketeer container. It's also possible to run Bucketeer, with Kakadu, in the live test mode that's described in the "Running the Application for Development" section of this document. To do this, you need to have the Kakadu binaries installed on your local development system. The instructions for how to do this should have been included with your Kakadu distribution. Once installed, the binaries should be accessible from the $PATH and the related libraries should be included in the system's $LD_LIBRARY_PATH. To confirm everything is set up correctly and working, run:

    kdu_compress -v

If you want to test the large image feature, where large images can be sent from one Bucketeer to another (or, between the same one if you'd like, since you're running in test mode), you will need to enable Bucketeer's large image feature. To do this, create a feature configuration file at: `/etc/bucketeer/bucketeer-features.conf`. The contents of that file should be:

    moirai {
      bucketeer.large.images {
        featureEnabled = true
      }
    }

This should enable the feature. To confirm the feature has been configured correctly, once Bucketeer is started in the live test mode, visit the Bucketeer status page at: `http://localhost:8888/status`. In the JSON that's returned from the page, you should see that features are enabled and that the `bucketeer.large.images` feature, in particular, is enabled.

## Enabling Writing Output CSVs to a Local Filesystem

If you want the output CSVs to be written to a directory on a local filesystem, specify the directory with `bucketeer.fs.csv.mount` and set the `bucketeer.fs.write.csv` key in your feature flags configuration, e.g.:

    moirai {
      bucketeer.fs.write.csv {
        featureEnabled = true
      }
    }

Make sure the directory is writable by the application.

_Note: if you're running Bucketeer in a container, this value should be the path to the destination directory from the container's perspective._

## Tweaking the Batch Upload

Choosing between conversion methods depends largely on how quickly TIFF images can be uploaded to the AWS Lambda bucket. AWS Lambda scales horizontally (up to 1000 simultaneous functions), so if you can upload TIFFs to the S3 bucket faster than they can be processed by the X number of cores on your local machine, it makes sense to use the batch method.

To support getting TIFFs up to S3 as quickly as possible, there are a number of controls in Bucketeer that can be adjusted to improve performance.

<dl>
  <dt>The worker verticle count</dt>
  <dd>The S3 upload verticle is a worker that has its own S3 client. If you configure multiple worker / upload verticles, there will be multiple clients sending TIFF images to the S3 bucket. This is controlled by the <code>s3.uploader.instances</code> property.<br/><br/>When you run the application locally, through the live test method, this can be set in a Maven settings file (for permanent usage) or can be set at runtime (for easier testing) by passing the value in as a system property.<br/><br/><i>Note: If you're using the Docker container mentioned earlier, the value would be passed in as an ENV property or be set in the application's configuration file. See the <a href="https://github.com/uclalibrary/docker-bucketeer">Docker Bucketeer project</a> for more detail.</i></dd>
  <dt>The worker verticle's thread count</dt>
  <dd>Each S3 client (in a worker verticle) can be configured to use one or more threads. Setting the thread count to more than one will allow each S3 client to upload multiple files at a time. The property for this value is <code>s3.uploader.threads</code>. Keep in mind that this doesn't set the total number of threads used, but just the number of threads per S3 client.<br/><br/>This value, like the worker verticle count, can be set in a Maven settings file or passed in via the command line as a system property. In the Docker environment, it should be set through the application's configuration file or as an ENV property.
  <dt>The maximum number of S3 requests</dt>
  <dd>If you set the above two values very high, it's easy to run out of RAM on your machine (since the S3 clients read all the TIFF files concurrently). The maximum number of S3 requests threshold provides an upper limit on the number of S3 PUTs that can be in process at any given time, regardless of the number of worker verticles or threads that have been configured.<br/><br/>The maximum number of requests you allow will depend on the amount of RAM available on the machine. When the maximum has been reached, conversion requests are requeued until there are resources available. The property for this configuration is <code>s3.max.requests</code>.<br/><br/>The ways that it can be set are just like the prior two properties.</dd>
  <dt>The requeuing delay</dt>
  <dd>Bucketeer is built on a messaging platform so, when the maximum number of PUT requests has been reached, any additional requests that come in for conversion will be requeued (until a new upload slot is available). The requeuing delay isn't really a performance configuration, like the above three, but it does allow you to reduce the number of messages flowing through the system by introducing an X number of seconds wait until a message is requeued. The property for this is <code>s3.requeue.delay</code>.<br/><br/>It's fine to leave this with its default value, but if you want to change it you'd do so in the same way as you would for the above properties.</dd>
</dl>

We're still experimenting with different configurations, so we don't have a recommendation for best values, given a particular type of machine, for these properties at this time.

## Working with Pinned OS Packages

We pin the versions of packages that we install into our base image. What this means is that periodically a pinned version will become obsolete and the build will break. We have a nightly build that should catch this issues for us, but in the case that you find the breakage before us, there is a handy way to tell which pinned version has broken the build. To see the current versions inside the base image, run:

    mvn validate -Dversions

This will output a list of current versions, which can be compared to the pinned versions defined in the project's POM file (i.e., pom.xml).

## Contact

We use an internal ticketing system, but we've left the GitHub [issues](https://github.com/UCLALibrary/bucketeer/issues) open in case you'd like to file a ticket or make a suggestion.
