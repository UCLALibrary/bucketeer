
package edu.ucla.library.bucketeer.handlers;

import static edu.ucla.library.bucketeer.Constants.UNSPECIFIED_HOST;

import java.io.File;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import info.freelibrary.util.FileUtils;
import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;
import info.freelibrary.util.StringUtils;

import edu.ucla.library.bucketeer.Config;
import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.HTTP;
import edu.ucla.library.bucketeer.MessageCodes;
import edu.ucla.library.bucketeer.verticles.FakeS3BucketVerticle;
import edu.ucla.library.bucketeer.verticles.MainVerticle;
import edu.ucla.library.bucketeer.verticles.S3BucketVerticle;
import edu.ucla.library.bucketeer.verticles.ThumbnailVerticle;
import io.vertx.config.ConfigRetriever;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.multipart.MultipartForm;

/**
 * Test class for the <code>BatchJobStatusHandler</code>.
 */
@RunWith(VertxUnitRunner.class)
public class BatchJobStatusHandlerTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(BatchJobStatusHandlerTest.class, Constants.MESSAGES);

    private static final String POST_URI = "/batch/input/csv";

    // jobName, imageId, success
    private static final String PATCH_BATCH_URI = "/batch/jobs/{}/{}/{}";

    private static final String TEST_ARK = "ark%3A%2F13030%2Fhb000003n9";

    private static final String JOB_FILE_NAME = "live-test.csv";

    private static final String JOB_FILE_PATH = "src/test/resources/csv/";

    private static final String JOB_NAME = FileUtils.stripExt(JOB_FILE_NAME);

    private static final String TRUE = "true";

    private static final String DELIMITER = "|";

    @Rule
    public RunTestOnContext myRunTestOnContextRule = new RunTestOnContext();

    @Rule
    public TestName myTestName = new TestName();

    private Vertx myVertx;

    /**
     * Test set up.
     *
     * @param aContext A testing context
     * @throws Exception When a problem occurs while trying to set up the test.
     */
    @Before
    public void setUp(final TestContext aContext) throws Exception {
        final DeploymentOptions options = new DeploymentOptions();
        final ServerSocket socket = new ServerSocket(0);
        final int port = socket.getLocalPort();
        final Async asyncTask = aContext.async();
        final String test_channel_id = "dev-null";

        LOGGER.debug(MessageCodes.BUCKETEER_021, myTestName.getMethodName(), port);

        aContext.put(Config.HTTP_PORT, port);
        final JsonObject myConfig = new JsonObject().put(Config.HTTP_PORT, port);
        // put our Slack Error Channel ID in the config, so that we send any errors to the
        // correct channel
        myConfig.put(Config.SLACK_ERROR_CHANNEL_ID, test_channel_id);
        options.setConfig(myConfig);
        socket.close();

        myVertx = myRunTestOnContextRule.vertx();

        // grab some configs
        ConfigRetriever.create(myVertx).getConfig(config -> {
            if (config.succeeded()) {
                // HERE
                myVertx.deployVerticle(MainVerticle.class.getName(), options, deployment -> {
                    if (deployment.succeeded()) {
                        @SuppressWarnings("rawtypes")
                        final List<Future> futures = new ArrayList<>();
                        final LocalMap<String, String> map = myVertx.sharedData().getLocalMap(Constants.VERTICLE_MAP);
                        final String s3BucketDeploymentId = map.get(S3BucketVerticle.class.getSimpleName());
                        final String thumbnailDeploymentId = map.get(ThumbnailVerticle.class.getSimpleName());

                        if (s3BucketDeploymentId.contains(DELIMITER)) {
                            for (final String delimitedId : s3BucketDeploymentId.split(DELIMITER)) {
                                futures.add(updateDeployment(delimitedId, Future.future()));
                            }
                        } else {
                            futures.add(updateDeployment(s3BucketDeploymentId, Future.future()));
                        }

                        if (thumbnailDeploymentId.contains(DELIMITER)) {
                            for (final String delimitedId : thumbnailDeploymentId.split(DELIMITER)) {
                                futures.add(updateDeployment(delimitedId, Future.future()));
                            }
                        } else {
                            futures.add(updateDeployment(thumbnailDeploymentId, Future.future()));
                        }

                        CompositeFuture.all(futures).setHandler(handler -> {
                            if (handler.succeeded()) {
                                asyncTask.complete();
                            } else {
                                aContext.fail(handler.cause());
                            }
                        });
                    } else {
                        aContext.fail(deployment.cause());
                    }
                });
            } else {
                aContext.fail(config.cause());
            }
        });
    }

    /**
     * Clean up after the test has been run.
     *
     * @param aContext A testing context
     * @throws Exception When a problem occurs while cleaning up the test
     */
    @After
    public void tearDown(final TestContext aContext) throws Exception {
        myVertx.close(aContext.asyncAssertSuccess());
    }

    /**
     * Tests the <code>BatchJobStatusHandler</code>.
     *
     * @param aContext A testing context
     */
    @Test
    @SuppressWarnings("deprecation")
    public final void testGetHandle(final TestContext aContext) {
        final Async asyncTask = aContext.async();
        final int port = aContext.get(Config.HTTP_PORT);
        final RequestOptions request = new RequestOptions();
        final String uri = StringUtils.format(PATCH_BATCH_URI, JOB_NAME, TEST_ARK, TRUE);

        request.setPort(port).setHost(Constants.UNSPECIFIED_HOST).setURI(uri);

        myVertx.createHttpClient().getNow(request, response -> {
            final int statusCode = response.statusCode();

            if (statusCode == HTTP.NOT_FOUND) {
                asyncTask.complete();
            } else {
                aContext.fail(LOGGER.getMessage(MessageCodes.BUCKETEER_083));
            }
        });
    }

    /**
     * Tests the <code>BatchJobStatusHandler</code>.
     *
     * @param aContext A testing context
     */

    @Test
    @SuppressWarnings("deprecation")
    public final void testPatchHandle500(final TestContext aContext) {
        final Async asyncTask = aContext.async();
        final int port = aContext.get(Config.HTTP_PORT);
        final RequestOptions options = new RequestOptions();
        final String uri = StringUtils.format(PATCH_BATCH_URI, JOB_NAME, TEST_ARK, TRUE);
        final HttpClient client = myVertx.createHttpClient();

        options.setPort(port).setHost(Constants.UNSPECIFIED_HOST).setURI(uri);

        client.request(HttpMethod.PATCH, options, response -> {
            final int statusCode = response.statusCode();
            final String statusMessage = response.statusMessage();

            if (statusCode == HTTP.INTERNAL_SERVER_ERROR) {
                asyncTask.complete();
            } else {
                aContext.fail(LOGGER.getMessage(MessageCodes.BUCKETEER_022, statusCode, statusMessage));
            }
        }).end();
    }

    /**
     * Tests the <code>BatchJobStatusHandler</code>.
     *
     * @param aContext A testing context
     */
    @Test
    public final void testPatchHandle(final TestContext aContext) {
        final Async asyncTask = aContext.async();
        final int port = aContext.get(Config.HTTP_PORT);
        final String csvFile = new File(JOB_FILE_PATH, JOB_FILE_NAME).getAbsolutePath();
        final String patchUri = StringUtils.format(PATCH_BATCH_URI, JOB_NAME, TEST_ARK, TRUE);
        final WebClient webClient = WebClient.create(myVertx);
        final HttpRequest<Buffer> postRequest = webClient.post(port, UNSPECIFIED_HOST, POST_URI);
        final MultipartForm form = MultipartForm.create();

        form.attribute(Constants.SLACK_HANDLE, "ksclarke");
        form.attribute("failure", "false");
        form.textFileUpload("csvFileToUpload", JOB_FILE_NAME, csvFile, "text/csv");

        postRequest.sendMultipartForm(form, sendMultipartForm -> {
            if (sendMultipartForm.succeeded()) {
                final HttpResponse<Buffer> postResponse = sendMultipartForm.result();
                final String postStatusMessage = postResponse.statusMessage();
                final int postStatusCode = postResponse.statusCode();

                // If batch job submission successful, submit a batch job update and check the response code
                if (postStatusCode == HTTP.OK) {
                    webClient.patch(port, UNSPECIFIED_HOST, patchUri).send(sendPatch -> {
                        if (sendPatch.succeeded()) {
                            final HttpResponse<Buffer> patchResponse = sendPatch.result();
                            final int statusCode = patchResponse.statusCode();
                            final String statusMessage = patchResponse.statusMessage();

                            if (statusCode == HTTP.NO_CONTENT) {
                                asyncTask.complete();
                            } else {
                                aContext.fail(LOGGER.getMessage(MessageCodes.BUCKETEER_022, statusCode,
                                        statusMessage));
                            }
                        } else {
                            aContext.fail(sendPatch.cause());
                        }
                    });
                } else {
                    aContext.fail(LOGGER.getMessage(MessageCodes.BUCKETEER_022, postStatusCode, postStatusMessage));
                }
            } else {
                final Throwable throwable = sendMultipartForm.cause();

                LOGGER.error(throwable, throwable.getMessage());
                aContext.fail(throwable);
            }
        });
    }

    /**
     * Removes the real S3UploadVerticle and replaces it with a fake version for our tests. The fake version
     * acknowledges it receives a request but doesn't try to upload the item into S3.
     *
     * @param aDeploymentId
     * @param aFuture
     */
    private Future<Void> updateDeployment(final String aDeploymentId, final Future<Void> aFuture) {
        myVertx.undeploy(aDeploymentId, undeployment -> {
            if (undeployment.succeeded()) {
                myVertx.deployVerticle(FakeS3BucketVerticle.class.getName(), fakeDeployment -> {
                    if (fakeDeployment.succeeded()) {
                        aFuture.complete();
                    } else {
                        aFuture.fail(fakeDeployment.cause());
                    }
                });
            } else {
                aFuture.fail(undeployment.cause());
            }
        });

        return aFuture;
    }
}
