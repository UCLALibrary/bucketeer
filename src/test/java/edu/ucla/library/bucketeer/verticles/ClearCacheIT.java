
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
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
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

    /**
     * Logger for the <code>ClearCacheIT</code>.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(ClearCacheIT.class, Constants.MESSAGES);

    private static final String VERTICLE_NAME = ClearCacheVerticle.class.getName();

    private static final String TIFFVERT_PATH = "src/test/resources/images/key.jpx";

    private static final String TIFFHORI_PATH = "src/test/resources/images/keyRotate.jpx";

    private static final String IMAGE_JPX = "newKeyTest2.jpx";

    private static final String IMAGE_KEY = "newKeyTest2";

    private static final String IIIIF_USERNAME = "bucketeer.iiif.cache.user";

    private static final String IIIIF_PASSWORD = "bucketeer.iiif.cache.password";

    private static final String IIIF_RESOURCE = "/iiif/2/newKeyTest2/info.json";

    /**
     * Individual AWS credential
     */
    private static AWSCredentials myAWSCredentials;

    private static String s3Bucket = "unconfigured";

    /**
     * A test context from which references to Vert.x can be retrieved.
     */
    @Rule
    public RunTestOnContext myRunTestOnContextRule = new RunTestOnContext();

    /**
     * The URL for the IIIF image server.
     */
    private String myIiifURL;

    /**
     * Deployment options for this test
     */
    private JsonObject myConfigs;

    /**
     * Cantaloupe username for testing Cantaloupe cache clearing.
     */
    private String myUsername;

    /**
     * Cantaloupe password for testing Cantaloupe cache clearing.
     */
    private String myPassword;

    /**
     * Verticle ID for undeploying ClearCacheVerticle
     */
    private String myVertID;

    /**
     * We can't, as of yet, execute these tests without a non-default S3 configuration
     */
    private AmazonS3 myAmazonS3;

    /**
     * Sets up the tests.
     *
     * @param aContext A testing environment
     */
    @Before
    public final void setup(final TestContext aContext) {
        final ConfigRetriever configRetriever = ConfigRetriever.create(myRunTestOnContextRule.vertx());
        final Async asyncTask = aContext.async();

        configRetriever.getConfig(configuration -> {
            if (configuration.failed()) {
                aContext.fail(configuration.cause());
            } else {
                myConfigs = configuration.result();
                myIiifURL = myConfigs.getString(Config.IIIF_URL);

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
        final WebClient client = WebClient.create(Vertx.vertx());
        final Async asyncTask = aContext.async();
        final Future<Integer> vertImageWidth = deployNewVerticle(myConfigs).compose(verticleID -> {
            myVertID = verticleID;

            return putObjectS3(TIFFVERT_PATH).compose(success -> {
                return getInfoJsonObject();
            });
        });

        vertImageWidth.compose(width -> {
            final Future<Integer> horiImageWidth = sendMessage(IMAGE_KEY).compose(success -> {
                return putObjectS3(TIFFHORI_PATH);
            }).compose(success -> {
                return getInfoJsonObject();
            });

            return horiImageWidth.compose(horiWidth -> {
                if (horiWidth != width) {
                    return sendMessage(IMAGE_KEY);
                }

                return sendMessage(IMAGE_KEY).compose(success -> {
                    return Future.failedFuture(LOGGER.getMessage(MessageCodes.BUCKETEER_610));
                });
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
     * Tests that verticles fails when given null credentials
     *
     * @param aContext A testing context
     */
    @Test
    public void testNullCredential(final TestContext aContext) {
        final Async asyncTask = aContext.async();
        final JsonObject nullConfigs = myConfigs;

        nullConfigs.put(IIIIF_USERNAME, (String) null);
        nullConfigs.put(IIIIF_PASSWORD, (String) null);

        deployNewVerticle(nullConfigs).onFailure(failure -> {
            TestUtils.complete(asyncTask);
        }).onSuccess(success -> {
            aContext.fail();
        });
    }

    /**
     * Tests that verticles fails when given invalid credentials
     *
     * @param aContext A testing context
     */
    @Test
    public void testInvalidCredential(final TestContext aContext) {
        final Async asyncTask = aContext.async();
        final JsonObject newConfigs = myConfigs;

        newConfigs.put(IIIIF_USERNAME, "username");
        newConfigs.put(IIIIF_PASSWORD, "password");

        deployNewVerticle(newConfigs).onFailure(failure -> {
            TestUtils.complete(asyncTask);
        }).onSuccess(success -> {
            aContext.fail();
        });
    }

    /**
     * Gets info.json from Cantaloupe of specific image
     */
    private Future<Integer> getInfoJsonObject() {
        final WebClient client = WebClient.create(Vertx.vertx());
        final Promise<Integer> promise = Promise.promise();

        LOGGER.debug(myIiifURL + IIIF_RESOURCE);

        client.getAbs(myIiifURL + IIIF_RESOURCE).putHeader(CONTENT_TYPE, JSON).send(result -> {
            if (result.succeeded()) {
                final String body = result.result().bodyAsString();

                try {
                    promise.complete(new JsonObject(body).getInteger("width"));
                } catch (final DecodeException details) {
                    promise.fail(details);
                }
            } else {
                promise.fail(result.cause());
            }
        });

        return promise.future();
    }

    /**
     * Puts image into AmazonS3 bucket
     */
    private Future<Void> putObjectS3(final String aPath) {
        final Promise<Void> promise = Promise.promise();

        try {
            myAmazonS3.putObject(s3Bucket, IMAGE_JPX, new File(aPath));
            promise.complete();
        } catch (final AmazonServiceException e) {
            LOGGER.error(e.getErrorMessage());
            promise.fail(e.getErrorMessage());
        }

        return promise.future();
    }

    /**
     * Sends message over eventBus to ClearCacheVerticle and verifies expected code is returned with failure
     */
    private Future<Void> sendMessage(final String aImageID) {
        final Promise<Void> promise = Promise.promise();
        myRunTestOnContextRule.vertx().eventBus().<JsonObject>send(VERTICLE_NAME,
                new JsonObject().put("imageID", aImageID), reply -> {
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
     * Deploys ClearCache verticle
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
