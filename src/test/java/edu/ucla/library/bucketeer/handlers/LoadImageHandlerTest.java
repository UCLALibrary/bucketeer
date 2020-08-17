
package edu.ucla.library.bucketeer.handlers;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import com.amazonaws.SdkClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.bucketeer.Config;
import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.HTTP;
import edu.ucla.library.bucketeer.MessageCodes;
import edu.ucla.library.bucketeer.verticles.MainVerticle;
import io.vertx.config.ConfigRetriever;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class LoadImageHandlerTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoadImageHandlerTest.class, Constants.MESSAGES);

    private static String s3Bucket = "unconfigured";

    private static final String DOT_JPX = ".jpx";

    private static final String REGION = "us-east-1";

    private static final int FIVE = 5;

    @Rule
    public RunTestOnContext myTestContext = new RunTestOnContext();

    @Rule
    public TestName myTestName = new TestName();

    @Rule
    public Timeout myTimeout = new Timeout(FIVE, TimeUnit.MINUTES);

    private Vertx myVertx;

    private AmazonS3 myAmazonS3;

    /**
     * Test set up.
     *
     * @param aContext A testing context
     * @throws IOException If there is trouble reading or writing data
     */
    @Before
    public void setUp(final TestContext aContext) throws IOException {
        final DeploymentOptions options = new DeploymentOptions();
        final ServerSocket socket = new ServerSocket(0);
        final int port = socket.getLocalPort();
        final Async asyncTask = aContext.async();

        LOGGER.debug(MessageCodes.BUCKETEER_021, myTestName.getMethodName(), port);

        aContext.put(Config.HTTP_PORT, port);
        options.setConfig(new JsonObject().put(Config.HTTP_PORT, port));
        socket.close();

        myVertx = myTestContext.vertx();

        ConfigRetriever.create(myVertx).getConfig(config -> {
            if (config.succeeded()) {
                final JsonObject jsonConfig = config.result();

                if (myAmazonS3 == null) {
                    final String s3AccessKey = jsonConfig.getString(Config.S3_ACCESS_KEY);
                    final String s3SecretKey = jsonConfig.getString(Config.S3_SECRET_KEY);

                    try {
                        final AWSCredentials credentials = new BasicAWSCredentials(s3AccessKey, s3SecretKey);
                        final AWSCredentialsProvider provider = new AWSStaticCredentialsProvider(credentials);

                        myAmazonS3 = AmazonS3ClientBuilder.standard().withCredentials(provider).withRegion(REGION)
                                .build();
                    } catch (final SdkClientException details) {
                        aContext.fail(details);
                    }

                    // grab our bucket name
                    s3Bucket = jsonConfig.getString(Config.S3_BUCKET);
                }

                myVertx.deployVerticle(MainVerticle.class.getName(), options, deployment -> {
                    if (deployment.succeeded()) {
                        LOGGER.debug(MessageCodes.BUCKETEER_143, getClass().getName());
                        asyncTask.complete();
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
     * Test tear down.
     *
     * @param aContext A testing context
     */
    @After
    public void tearDown(final TestContext aContext) {
        myVertx.close(aContext.asyncAssertSuccess());
    }

    /**
     * Confirm that the response from LoadImageHander matches the spec
     *
     * @param aContext A testing context
     */
    @Ignore
    @Test
    @SuppressWarnings("deprecation")
    public void confirmLoadImageHandlerResponseMatchesSpec(final TestContext aContext) {
        final Async async = aContext.async();
        final int port = aContext.get(Config.HTTP_PORT);
        final String encodedImagePath = "/test/src%2Ftest%2Fresources%2Fimages%2F熵.tif";
        final RequestOptions request = new RequestOptions();

        request.setPort(port).setHost(Constants.UNSPECIFIED_HOST).setURI(encodedImagePath);

        myVertx.createHttpClient().getNow(request, response -> {
            final int statusCode = response.statusCode();
            final String statusMessage = response.statusMessage();

            if (statusCode == HTTP.OK) {
                response.bodyHandler(body -> {
                    final JsonObject jsonConfirm = new JsonObject(body.getString(0, body.length()));
                    final String id = "test";

                    aContext.assertEquals(id, jsonConfirm.getString(Constants.IMAGE_ID));
                    aContext.assertEquals("src/test/resources/images/熵.tif", jsonConfirm.getString(
                            Constants.FILE_PATH));

                    jsonConfirm.put(Constants.WAIT_COUNT, 0);

                    // Check every 5 seconds to see if our process is done
                    myVertx.setPeriodic(5000, timer -> {
                        if (myVertx.sharedData().getLocalMap(Constants.RESULTS_MAP).get(id) != null) {
                            myAmazonS3.deleteObject(s3Bucket, id + DOT_JPX);

                            if (!async.isCompleted()) {
                                async.complete();
                            }
                        } else {
                            int counter = jsonConfirm.getInteger(Constants.WAIT_COUNT);

                            // Keep trying for a minute
                            if (counter++ >= 12) {
                                aContext.fail(LOGGER.getMessage(MessageCodes.BUCKETEER_027));
                            } else {
                                LOGGER.debug(MessageCodes.BUCKETEER_029);
                                jsonConfirm.put(Constants.WAIT_COUNT, counter);
                            }
                        }
                    });
                });
            } else {
                aContext.fail(LOGGER.getMessage(MessageCodes.BUCKETEER_022, statusCode, statusMessage));
            }
        });
    }

    /**
     * Confirm that LoadImageHander fails if we do not provide an ID
     *
     * @param aContext A testing context
     */
    @Test
    @SuppressWarnings("deprecation")
    public void confirmLoadImageHandlerFailsWithMissingParam(final TestContext aContext) {
        final Async asyncTask = aContext.async();
        final int port = aContext.get(Config.HTTP_PORT);

        myVertx.createHttpClient().getNow(port, Constants.UNSPECIFIED_HOST, "/12345/", response -> {
            aContext.assertNotEquals(response.statusCode(), HTTP.OK);

            if (!asyncTask.isCompleted()) {
                asyncTask.complete();
            }
        });
    }

    /**
     * Confirm that LoadImageHander fails if we provide and invalid filePath
     *
     * @param aContext A testing context
     */
    @Test
    @SuppressWarnings("deprecation")
    public void confirmLoadImageHandlerFailsWithInvalideFilepath(final TestContext aContext) {
        final Async async = aContext.async();
        final int port = aContext.get(Config.HTTP_PORT);
        final String path = "/12345/this-file-does-not-exist.tiff";

        // Tests loadImage path from our OpenAPI YAML file returns an error response when given incomplete data
        myVertx.createHttpClient().getNow(port, Constants.UNSPECIFIED_HOST, path, response -> {
            aContext.assertNotEquals(response.statusCode(), HTTP.OK);

            if (!async.isCompleted()) {
                async.complete();
            }
        });
    }

}
