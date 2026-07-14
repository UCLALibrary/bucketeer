
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
import edu.ucla.library.bucketeer.MessageCodes;
import edu.ucla.library.bucketeer.TestConstants;

import info.freelibrary.util.FileUtils;
import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(FilesystemWriteCsvFfOnT.class, MessageCodes.BUNDLE);

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

                TestUtils.complete(asyncTask);
            } else {
                aContext.fail(statusCheck.cause());
            }
        });
    }

    /**
     * Tests writing a CSV to the local file system mount. This is an e2e test (although the AWS Lambda is mocked).
     *
     * @param aContext A test context
     */
    @Test
    public void testWriteCsv(final TestContext aContext) {
        final Vertx vertx = myTestContext.vertx();
        final WebClient webClient = WebClient.create(vertx);
        final int port = Integer.parseInt(System.getProperty(Config.HTTP_PORT));
        final Async asyncTask = aContext.async();
        final MultipartForm form = MultipartForm.create().attribute(Constants.SLACK_HANDLE, "bucketeer")
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
                        aContext.assertTrue(DockerUtils.copy(TestConstants.BUCKETEER_FF_ON, srcDir.toString(),
                                tmpDestDir.toString()));

                        // Confirm the file we expect to exist actually does
                        aContext.assertTrue(expectedFile.exists());

                        FileUtils.delete(expectedFile);
                        TestUtils.complete(asyncTask);
                    } else {
                        aContext.fail(fakeLambda.cause());
                    }
                });

                fakeLambda(webClient, port, Constants.UNSPECIFIED_HOST, jobCompletion, vertx);
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
            final Promise<Void> aPromise, final Vertx aVertx) {
        Future.<List<String>>future(getImageIds -> {
            aWebClient.get(aPort, aHost, "/batch/jobs/live-test-docker").send(get -> {
                if (get.succeeded()) {
                    final List<String> imageIds = get.result().bodyAsJsonObject().getJsonArray(Constants.JOBS).stream()
                            .map(JsonObject.class::cast)
                            .filter(job -> Constants.EMPTY.equals(job.getString(Constants.STATUS)))
                            .map(job -> job.getString(Constants.IMAGE_ID)).collect(Collectors.toList());

                    getImageIds.complete(imageIds);
                } else {
                    getImageIds.fail(get.cause());
                }
            });
        }).compose(imageIds -> {
            final List<Future> patchFutures = imageIds.stream().map(imageId -> Future.<Void>future(finishJob -> {
                final String urlEncodedImageId = URLEncoder.encode(imageId, StandardCharsets.UTF_8);
                final String urlPath = "/batch/jobs/live-test-docker/" + urlEncodedImageId + "/true";

                aWebClient.patch(aPort, aHost, urlPath).send(patch -> {
                    if (patch.succeeded()) {
                        finishJob.complete();
                    } else {
                        finishJob.fail(patch.cause());
                    }
                });
            })).collect(Collectors.toList());

            return CompositeFuture.all(patchFutures).mapEmpty();
        }).compose(wrapUp -> waitForCsvFile(aVertx, TestConstants.BUCKETEER_FF_ON,
                "/usr/local/bucketeer/csv/live-test-docker.csv")).onSuccess(wrapUp -> {
                    aPromise.complete();
                }).onFailure(aPromise::fail);
    }

    /**
     * Waits for the CSV file to be output.
     *
     * @param aVertx A Vert.x instance
     * @param aContainerName The name of the container being tested
     * @param aFilePath A file path where the expected output should be
     * @return A future that will fire when the file has been detected
     */
    private Future<Void> waitForCsvFile(final Vertx aVertx, final String aContainerName, final String aFilePath) {
        final Promise<Void> promise = Promise.promise();
        final long timerId = aVertx.setPeriodic(250, id -> {
            try {
                final Process process =
                        new ProcessBuilder("docker", "exec", aContainerName, "sh", "-c", "test -f " + aFilePath)
                                .redirectErrorStream(true).start();

                if (process.waitFor() == 0) {
                    aVertx.cancelTimer(id);
                    promise.tryComplete();
                }
            } catch (Exception details) {
                aVertx.cancelTimer(id);
                promise.tryFail(details);
            }
        });

        aVertx.setTimer(15000, id -> {
            if (!promise.future().isComplete()) {
                aVertx.cancelTimer(timerId);
                promise.tryFail(LOGGER.getMessage(MessageCodes.BUCKETEER_614, aFilePath));
            }
        });

        return promise.future();
    }
}
