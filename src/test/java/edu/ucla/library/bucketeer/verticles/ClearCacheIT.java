
package edu.ucla.library.bucketeer.verticles;

import static edu.ucla.library.bucketeer.Constants.CONTENT_TYPE;
import static edu.ucla.library.bucketeer.Constants.JSON;

import java.io.File;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;

import info.freelibrary.util.HTTP;
import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.bucketeer.Config;
import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.MessageCodes;
import edu.ucla.library.bucketeer.utils.TestUtils;

import io.vertx.config.ConfigRetriever;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

/**
 * Testing the mechanism used to clear Cantaloupe's cache.
 */
@RunWith(VertxUnitRunner.class)
public class ClearCacheIT {

    static {
        // For running this test independently, we need to configure the logging config source
        System.setProperty("logback.configurationFile", "src/test/resources/logback-test.xml");
    }

    /** Logger for the <code>ClearCacheIT</code>. */
    private static final Logger LOGGER = LoggerFactory.getLogger(ClearCacheIT.class, Constants.MESSAGES);

    /** The name of the verticle that clears the Cantaloupe cache. */
    private static final String VERTICLE_NAME = ClearCacheVerticle.class.getName();

    /** The path to a test JPX file. **/
    private static final String VERT_PATH = "src/test/resources/images/key.jpx";

    /** The path to a JPX file that helps test rotation. */
    private static final String HORI_PATH = "src/test/resources/images/keyRotate.jpx";

    /** A JPX file that helps test new keys. */
    private static final String IMAGE_JPX = "newKeyTest2.jpx";

    /** A new key to use when testing. */
    private static final String IMAGE_KEY = "newKeyTest2";

    /** The IIIF cache username. */
    private static final String IIIF_USERNAME = "bucketeer.iiif.cache.user";

    /** The IIIF cache password. */
    private static final String IIIF_PASSWORD = "bucketeer.iiif.cache.password";

    /** The expected IIIF information resource. */
    private static final String IIIF_RESOURCE = "/iiif/2/newKeyTest2/info.json";

    /** Individual AWS credential. */
    private static AWSCredentials myAWSCredentials;

    /** A default S3 bucket name. */
    private static String s3Bucket = "unconfigured";

    /** A test context from which references to Vert.x can be retrieved. */
    @Rule
    public RunTestOnContext myRunTestOnContextRule = new RunTestOnContext();

    /** The URL for the IIIF image server. */
    private String myImageServerURL;

    /** Deployment options for this test. */
    private JsonObject myConfigs;

    /** Verticle ID for undeploying ClearCacheVerticle. */
    private String myVertID;

    /** We can't, as of yet, execute these tests without a non-default S3 configuration. */
    private AmazonS3 myAmazonS3;

    /**
     * Sets up the tests.
     *
     * @param aContext A testing environment
     */
    @SuppressWarnings("deprecation")
    @Before
    public final void setup(final TestContext aContext) {
        final ConfigRetriever configRetriever = ConfigRetriever.create(myRunTestOnContextRule.vertx());
        final Async asyncTask = aContext.async();

        configRetriever.getConfig(configuration -> {
            if (configuration.failed()) {
                aContext.fail(configuration.cause());
            } else {
                myConfigs = configuration.result();
                myImageServerURL = myConfigs.getString(Config.IIIF_URL);

                if (myAmazonS3 == null) {
                    final String s3AccessKey = myConfigs.getString(Config.S3_ACCESS_KEY);
                    final String s3SecretKey = myConfigs.getString(Config.S3_SECRET_KEY);

                    // get myAWSCredentials ready
                    myAWSCredentials = new BasicAWSCredentials(s3AccessKey, s3SecretKey);

                    // instantiate the myAmazonS3 client
                    myAmazonS3 = new AmazonS3Client(myAWSCredentials);
                }

                s3Bucket = myConfigs.getString(Config.S3_BUCKET);
                TestUtils.complete(asyncTask);
            }
        });
    }

    /**
     * Tear down the testing environment.
     *
     * @param aContext A test context
     */
    @After
    public void tearDown(final TestContext aContext) {
        if (myVertID != null) {
            final Async asyncTask = aContext.async();

            myRunTestOnContextRule.vertx().undeploy(myVertID, undeployment -> {
                if (undeployment.failed()) {
                    aContext.fail(undeployment.cause());
                } else {
                    asyncTask.complete();
                }
            });
        } else {
            LOGGER.info(MessageCodes.BUCKETEER_605);
        }
    }

    /**
     * Tests clearing Cantaloupe's cache.
     *
     * @param aContext A testing context
     */
    @Test
    public void testCacheClear(final TestContext aContext) {
        final Async asyncTask = aContext.async();
        final Future<Integer> vertImageWidth = deployNewVerticle(myConfigs).compose(verticleID -> {
            myVertID = verticleID;

            return putObjectS3(VERT_PATH).compose(success -> getImageWidth());
        });

        vertImageWidth.compose(width -> {
            final Future<Integer> horiImageWidth = sendMessage(IMAGE_KEY).compose(success -> putObjectS3(HORI_PATH))
                    .compose(success -> getImageWidth());

            return horiImageWidth.compose(horiWidth -> {
                if (horiWidth != width) {
                    return sendMessage(IMAGE_KEY);
                }

                return sendMessage(IMAGE_KEY)
                        .compose(success -> Future.failedFuture(LOGGER.getMessage(MessageCodes.BUCKETEER_610)));
            });
        }).onSuccess(result -> {
            myAmazonS3.deleteObject(s3Bucket, IMAGE_JPX);
            TestUtils.complete(asyncTask);
        }).onFailure(failure -> {
            myAmazonS3.deleteObject(s3Bucket, IMAGE_JPX);
            LOGGER.error(failure.getMessage());
            aContext.fail();
        });
    }

    /**
     * Tests clearing Cantaloupe's cache with null imageID
     *
     * @param aContext
     */
    @Test
    public void testNoImageID(final TestContext aContext) {
        final String imageIDNull = null;
        final Async asyncTask = aContext.async();

        deployNewVerticle(myConfigs).compose(verticleID -> {
            myVertID = verticleID;
            return sendMessage(imageIDNull);
        }).onFailure(failure -> {
            if (Integer.parseInt(failure.getMessage()) == HTTP.INTERNAL_SERVER_ERROR) {
                TestUtils.complete(asyncTask);
            } else {
                aContext.fail();
            }
        }).onSuccess(success -> {
            aContext.fail();
        });
    }

    /**
     * Tests that verticle deployment fails when given null credentials
     *
     * @param aContext A testing context
     */
    @Test
    public void testNullCredential(final TestContext aContext) {
        final Async asyncTask = aContext.async();
        final JsonObject nullConfigs = myConfigs;

        nullConfigs.put(IIIF_USERNAME, (String) null);
        nullConfigs.put(IIIF_PASSWORD, (String) null);

        deployNewVerticle(nullConfigs).onFailure(failure -> {
            TestUtils.complete(asyncTask);
        }).onSuccess(success -> {
            aContext.fail();
        });
    }

    /**
     * Tests that verticle deployment fails when given invalid credentials
     *
     * @param aContext A testing context
     */
    @Test
    public void testInvalidCredential(final TestContext aContext) {
        final Async asyncTask = aContext.async();
        final JsonObject newConfigs = myConfigs;

        newConfigs.put(IIIF_USERNAME, "username");
        newConfigs.put(IIIF_PASSWORD, "password");

        deployNewVerticle(newConfigs).onFailure(failure -> {
            TestUtils.complete(asyncTask);
        }).onSuccess(success -> {
            aContext.fail();
        });
    }

    /**
     * Gets width from info.json from Cantaloupe of specific image.
     *
     * @return A future with the width
     */
    private Future<Integer> getImageWidth() {
        final WebClient client = WebClient.create(Vertx.vertx());
        final Promise<Integer> promise = Promise.promise();

        LOGGER.debug(myImageServerURL + IIIF_RESOURCE);

        client.getAbs(myImageServerURL + IIIF_RESOURCE).putHeader(CONTENT_TYPE, JSON).send(request -> {
            if (request.succeeded()) {
                final HttpResponse<Buffer> response = request.result();

                if (response.statusCode() == HTTP.OK) {
                    promise.complete(response.bodyAsJsonObject().getInteger("width"));
                } else {
                    promise.fail(LOGGER.getMessage(MessageCodes.BUCKETEER_611, response.statusCode(),
                            response.statusMessage()));
                }
            } else {
                promise.fail(request.cause());
            }

            client.close();
        });

        return promise.future();
    }

    /**
     * Puts image into AmazonS3 bucket.
     *
     * @param aPath An S3 path
     * @return A future indicating whether the object was successfully PUT into S3
     */
    private Future<Void> putObjectS3(final String aPath) {
        final Promise<Void> promise = Promise.promise();

        try {
            myAmazonS3.putObject(s3Bucket, IMAGE_JPX, new File(aPath));
            promise.complete();
        } catch (final AmazonServiceException details) {
            LOGGER.error(details.getErrorMessage());
            promise.fail(details.getErrorMessage());
        }

        return promise.future();
    }

    /**
     * Sends message over eventBus to ClearCacheVerticle and verifies expected code is returned with failure.
     *
     * @param aImageID An image ID
     * @return A future indicating the message has been sent
     */
    @SuppressWarnings("deprecation")
    private Future<Void> sendMessage(final String aImageID) {
        final Promise<Void> promise = Promise.promise();

        myRunTestOnContextRule.vertx().eventBus().<JsonObject>send(VERTICLE_NAME,
                new JsonObject().put(Constants.IMAGE_ID, aImageID), reply -> {
                    if (reply.failed()) {
                        final ReplyException re = (ReplyException) reply.cause();
                        promise.fail(String.valueOf(re.failureCode()));
                    } else {
                        promise.complete();
                    }
                });

        return promise.future();
    }

    /**
     * Deploys ClearCache verticle.
     *
     * @param aConfig A new verticle configuration
     * @return A future with the deployment ID of the new verticle
     */
    private Future<String> deployNewVerticle(final JsonObject aConfig) {
        final Promise<String> promise = Promise.promise();
        final DeploymentOptions option = new DeploymentOptions().setConfig(aConfig);

        myRunTestOnContextRule.vertx().deployVerticle(VERTICLE_NAME, option, deployment -> {
            if (deployment.succeeded()) {
                promise.complete(deployment.result());
            } else {
                promise.fail(deployment.cause());
            }
        });

        return promise.future();
    }
}
