
package edu.ucla.library.bucketeer.utils;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import edu.ucla.library.bucketeer.Config;
import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.DockerUtils;
import edu.ucla.library.bucketeer.Features;
import edu.ucla.library.bucketeer.TestConstants;
import info.freelibrary.util.FileUtils;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.multipart.MultipartForm;

/**
 * Tests the large image feature flag when it is enabled.
 */
@RunWith(VertxUnitRunner.class)
public class FilesystemWriteCsvFfOnT {

    private static final File TEST_CSV = new File("src/test/resources/csv/live-test-docker.csv");

    /**
     * A JUnit rule to run the test on the active context.
     */
    @Rule
    public RunTestOnContext myTestContext = new RunTestOnContext();


    /**
     * Tests the status of the large image feature flag.
     *
     * @param aContext A test context
     */
    @Test
    public void testStatus(final TestContext aContext) {
        final WebClient webClient = WebClient.create(myTestContext.vertx());
        final int port = Integer.parseInt(System.getProperty(Config.HTTP_PORT));
        final Async asyncTask = aContext.async();

        webClient.get(port, Constants.UNSPECIFIED_HOST, "/status").send(statusCheck -> {
            if (statusCheck.succeeded()) {
                final HttpResponse<Buffer> response = statusCheck.result();
                final JsonObject status = response.bodyAsJsonObject();
                final JsonObject features = status.getJsonObject(Features.FEATURES);

                aContext.assertEquals(status.getString(Constants.STATUS), "ok");
                aContext.assertEquals(true, features.getBoolean(Features.ENABLED, false));
                aContext.assertEquals(true, features.getBoolean(Features.FS_WRITE_CSV, false));

                asyncTask.complete();
            } else {
                aContext.fail(statusCheck.cause());
            }
        });
    }

    /**
     * Tests writing a CSV to the local filesystem mount.
     *
     * This is an e2e test (although the AWS Lambda is mocked).
     *
     * @param aContext A test context
     */
    @Test
    public void testWriteCsv(final TestContext aContext) {
        final WebClient webClient = WebClient.create(myTestContext.vertx());
        final int port = Integer.parseInt(System.getProperty(Config.HTTP_PORT));
        final Async asyncTask = aContext.async();
        final MultipartForm form = MultipartForm.create()
                .attribute(Constants.SLACK_HANDLE, "bucketeer")
                .textFileUpload(Constants.CSV_DATA, TEST_CSV.getName(), TEST_CSV.getAbsolutePath(), Constants.CSV);

        webClient.post(port, Constants.UNSPECIFIED_HOST, "/batch/input/csv").sendMultipartForm(form, sendForm -> {
            if (sendForm.succeeded()) {
                // Complete the job
                final Promise<Void> jobCompletion = Promise.promise();

                jobCompletion.future().onComplete(fakeLambda -> {
                    if (fakeLambda.succeeded()) {
                        final Path srcDir = Path.of(System.getProperty(Config.FILESYSTEM_CSV_MOUNT));
                        final String srcDirName = srcDir.getFileName().toString();
                        final File tmpDestDir = new File(TestConstants.TMP_DEST_DIR);

                        final Path expectedFilePath = Path.of(tmpDestDir.getPath(), srcDirName, TEST_CSV.getName());
                        final File expectedFile = new File(expectedFilePath.toString());

                        // Confirm we can create our temporary test directory (or that it already exists)
                        aContext.assertTrue(tmpDestDir.exists() || tmpDestDir.mkdirs());

                        // Confirm we can copy the test container's files to the temporary test directory
                        aContext.assertTrue(
                                DockerUtils.copy(TestConstants.BUCKETEER_FF_ON, srcDir.toString(),
                                        tmpDestDir.toString()));

                        // Confirm the file we expect to exist actually does
                        aContext.assertTrue(expectedFile.exists());

                        FileUtils.delete(expectedFile);
                        asyncTask.complete();
                    } else {
                        aContext.fail(fakeLambda.cause());
                    }
                });
                fakeLambda(webClient, port, Constants.UNSPECIFIED_HOST, jobCompletion);
            } else {
                aContext.fail(sendForm.cause());
            }
        });
    }

    /**
     * Mock the response from AWS Lambda in order to complete the job.
     *
     * @param aWebClient A client used to send requests to our Bucketeer instance
     * @param aPort The port of our Bucketeer instance
     * @param aHost The host of our Bucketeer instance
     * @param aPromise A promise to complete upon job completion, or fail
     */
    private void fakeLambda(final WebClient aWebClient, final int aPort, final String aHost,
            final Promise<Void> aPromise) {
        Future.<Void>future(s3BucketVerticleSwap -> {
            s3BucketVerticleSwap.complete();
        }).compose(success -> {
            return Future.<List<String>>future(getImageIds -> {
                aWebClient.get(aPort, aHost, "/batch/jobs/live-test-docker").send(ar -> {
                    if (ar.succeeded()) {
                        // Gets a list of the image IDs of the current jobs
                        final List<String> imageIds = ar.result().bodyAsJsonObject().getJsonArray("jobs").stream()
                                .map(JsonObject.class::cast).filter(job -> job.getString("status").equals(""))
                                .map(job -> job.getString("image-id")).collect(Collectors.toList());

                        getImageIds.complete(imageIds);
                    } else {
                        getImageIds.fail(ar.cause());
                    }
                });
            });
        }).compose(imageIds -> {
            final List<Future> futures = imageIds.stream().map(imageId -> {
                return Future.<Void>future(finishJob -> {
                    final String urlEncodedImageId = URLEncoder.encode(imageId, StandardCharsets.UTF_8);
                    final String urlPath = "/batch/jobs/live-test-docker/" + urlEncodedImageId + "/true";

                    // Tell our Bucketeer instance to treat the job as completed
                    aWebClient.patch(aPort, aHost, urlPath).send(ar -> {
                        if (ar.succeeded()) {
                            finishJob.complete();
                        } else {
                            finishJob.fail(ar.cause());
                        }
                    });
                });
            }).collect(Collectors.toList());

            return CompositeFuture.all(futures);
        }).onSuccess(success -> {
            aPromise.complete();
        }).onFailure(failure -> {
            aPromise.fail(failure.getCause());
        });
    }
}
