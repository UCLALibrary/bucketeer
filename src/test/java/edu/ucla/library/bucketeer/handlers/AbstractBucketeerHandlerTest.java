
package edu.ucla.library.bucketeer.handlers;

import static edu.ucla.library.bucketeer.Constants.UNSPECIFIED_HOST;

import java.io.File;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import info.freelibrary.util.FileUtils;
import info.freelibrary.util.Logger;

import edu.ucla.library.bucketeer.AbstractBucketeerTest;
import edu.ucla.library.bucketeer.Config;
import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.HTTP;
import edu.ucla.library.bucketeer.MessageCodes;
import edu.ucla.library.bucketeer.verticles.FakeS3BucketVerticle;
import edu.ucla.library.bucketeer.verticles.MainVerticle;
import edu.ucla.library.bucketeer.verticles.S3BucketVerticle;
import io.vertx.config.ConfigRetriever;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
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

@RunWith(VertxUnitRunner.class)
public abstract class AbstractBucketeerHandlerTest extends AbstractBucketeerTest {

    protected static final String JOB_FILE_NAME = "live-test.csv";

    protected static final String JOB_NAME = FileUtils.stripExt(JOB_FILE_NAME);

    private static final String POST_URI = "/batch/input/csv";

    private static final String JOB_FILE_PATH = "src/test/resources/csv/";

    private static final String DELIMITER = "|";

    @Rule
    public RunTestOnContext myRunTestOnContextRule = new RunTestOnContext();

    @Rule
    public TestName myTestName = new TestName();

    protected Vertx myVertx;

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

        getLogger().debug(MessageCodes.BUCKETEER_021, myTestName.getMethodName(), port);

        aContext.put(Config.HTTP_PORT, port);
        options.setConfig(new JsonObject().put(Config.HTTP_PORT, port));
        socket.close();

        myVertx = myRunTestOnContextRule.vertx();

        // Grab some configs
        ConfigRetriever.create(myVertx).getConfig(config -> {
            if (config.succeeded()) {
                myVertx.deployVerticle(MainVerticle.class.getName(), options, deployment -> {
                    if (deployment.succeeded()) {
                        @SuppressWarnings("rawtypes")
                        final List<Future> futures = new ArrayList<>();
                        final LocalMap<String, String> map = myVertx.sharedData().getLocalMap(Constants.VERTICLE_MAP);
                        final String s3BucketDeploymentId = map.get(S3BucketVerticle.class.getSimpleName());

                        if (s3BucketDeploymentId.contains(DELIMITER)) {
                            for (final String delimitedId : s3BucketDeploymentId.split(DELIMITER)) {
                                futures.add(updateDeployment(delimitedId, Future.future()));
                            }
                        } else {
                            futures.add(updateDeployment(s3BucketDeploymentId, Future.future()));
                        }

                        CompositeFuture.all(futures).setHandler(handler -> {
                            if (handler.succeeded()) {
                                getLogger().debug(MessageCodes.BUCKETEER_143, getClass().getName());
                                loadCSV(asyncTask, aContext, port);
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
     * Returns the logger being used to log the test.
     *
     * @return The logger being used to log the test
     */
    protected abstract Logger getLogger();

    /**
     * Sets up the test with an initial upload.
     */
    private void loadCSV(final Async aAsyncTask, final TestContext aContext, final int aPort) {
        final String csvFile = new File(JOB_FILE_PATH, JOB_FILE_NAME).getAbsolutePath();
        final WebClient webClient = WebClient.create(myVertx);
        final HttpRequest<Buffer> postRequest = webClient.post(aPort, UNSPECIFIED_HOST, POST_URI);
        final MultipartForm form = MultipartForm.create();

        form.attribute(Constants.SLACK_HANDLE, "ksclarke");
        form.attribute("failure", "false");
        form.textFileUpload("csvFileToUpload", JOB_FILE_NAME, csvFile, "text/csv");

        postRequest.sendMultipartForm(form, sendMultipartForm -> {
            final Logger logger = getLogger();

            if (sendMultipartForm.succeeded()) {
                final HttpResponse<Buffer> postResponse = sendMultipartForm.result();
                final String postStatusMessage = postResponse.statusMessage();
                final int postStatusCode = postResponse.statusCode();

                // If batch job submission successful, submit a batch job update and check the response code
                if (postStatusCode == HTTP.OK) {
                    aAsyncTask.complete();
                } else {
                    aContext.fail(logger.getMessage(MessageCodes.BUCKETEER_022, postStatusCode, postStatusMessage));
                }
            } else {
                final Throwable throwable = sendMultipartForm.cause();

                logger.error(throwable, throwable.getMessage());
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
