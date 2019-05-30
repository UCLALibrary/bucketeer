
package edu.ucla.library.bucketeer.handlers;

import java.io.IOException;
import java.net.ServerSocket;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;

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
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class LoadImageHandlerTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoadImageHandlerTest.class, Constants.MESSAGES);

    private static String s3Bucket = "unconfigured";
    private static final String DOT_JPX = ".jpx";

    private static AWSCredentials myAWSCredentials;

    private Vertx myVertx;

    private AmazonS3 myAmazonS3;

    /**
     * Test set up.
     *
     * @param aContext A testing context
     */
    @SuppressWarnings("deprecation")
    @Before
    public void setUp(final TestContext aContext) throws IOException {
        final DeploymentOptions options = new DeploymentOptions();
        final ServerSocket socket = new ServerSocket(0);
        final int port = socket.getLocalPort();
        final Async asyncTask = aContext.async();

        LOGGER.debug(MessageCodes.BUCKETEER_021, port);

        aContext.put(Config.HTTP_PORT, port);
        options.setConfig(new JsonObject().put(Config.HTTP_PORT, port));
        socket.close();

        // Initialize the Vert.x environment and start our main verticle
        myVertx = Vertx.vertx();
        myVertx.deployVerticle(MainVerticle.class.getName(), options, aContext.asyncAssertSuccess());
        final ConfigRetriever configRetriever = ConfigRetriever.create(myVertx);

        // grab some configs
        configRetriever.getConfig(config -> {
            if (config.succeeded()) {
                final JsonObject jsonConfig = config.result();
                // set up our Amazon S3 client
                if (myAmazonS3 == null) {
                    final String s3AccessKey = jsonConfig.getString(Config.S3_ACCESS_KEY);
                    final String s3SecretKey = jsonConfig.getString(Config.S3_SECRET_KEY);

                    // get myAWSCredentials ready
                    myAWSCredentials = new BasicAWSCredentials(s3AccessKey, s3SecretKey);

                    // instantiate the myAmazonS3 client
                    myAmazonS3 = new AmazonS3Client(myAWSCredentials);

                    // grab our bucket name
                    s3Bucket = jsonConfig.getString(Config.S3_BUCKET);

                }
            }
            asyncTask.complete();
        }
        );


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
    @Test
    @SuppressWarnings("deprecation")
    public void confirmLoadImageHandlerResponseMatchesSpec(final TestContext aContext) {
        LOGGER.debug(MessageCodes.BUCKETEER_017, Thread.currentThread().getStackTrace()[1].getMethodName());

        final Async async = aContext.async();
        final int port = aContext.get(Config.HTTP_PORT);
        final String encodedImagePath = "/test/src%2Ftest%2Fresources%2Fimages%2Ftest.tif";

        myVertx.createHttpClient().getNow(port, Constants.UNSPECIFIED_HOST, encodedImagePath, response -> {
            final int statusCode = response.statusCode();

            if (response.statusCode() == HTTP.OK) {
                response.bodyHandler(body -> {
                    final JsonObject jsonConfirm = new JsonObject(body.getString(0, body.length()));
                    final String id = "test";

                    aContext.assertEquals(jsonConfirm.getString(Constants.IMAGE_ID), id);
                    aContext.assertEquals(jsonConfirm.getString(Constants.FILE_PATH),
                            "src/test/resources/images/test.tif");

                    jsonConfirm.put(Constants.WAIT_COUNT, 0);

                    // Check every 5 seconds to see if our process is done
                    myVertx.setPeriodic(5000, timer -> {
                        if (myVertx.sharedData().getLocalMap(Constants.RESULTS_MAP).get(id + DOT_JPX) != null) {
                            // clean up the created .jpx file
                            myAmazonS3.deleteObject(s3Bucket, id + DOT_JPX );
                            async.complete();
                        } else {
                            int counter = jsonConfirm.getInteger(Constants.WAIT_COUNT);

                            // Keep trying for a minute
                            if (counter++ >= 12) {
                                aContext.fail(LOGGER.getMessage(MessageCodes.BUCKETEER_027));
                                async.complete();
                            } else {
                                LOGGER.debug(MessageCodes.BUCKETEER_029);
                                jsonConfirm.put(Constants.WAIT_COUNT, counter);
                            }
                        }
                    });
                });
            } else {
                aContext.fail(LOGGER.getMessage(MessageCodes.BUCKETEER_022, statusCode));
                async.complete();
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
        LOGGER.debug(MessageCodes.BUCKETEER_017, Thread.currentThread().getStackTrace()[1].getMethodName());

        final Async async = aContext.async();
        final int port = aContext.get(Config.HTTP_PORT);

        // Testing the main loadImage path defined in our OpenAPI YAML file returns an error response when given
        // incomplete data
        myVertx.createHttpClient().getNow(port, Constants.UNSPECIFIED_HOST, "/12345/", response -> {
            aContext.assertNotEquals(response.statusCode(), HTTP.OK);
            async.complete();
        });
    }

}
