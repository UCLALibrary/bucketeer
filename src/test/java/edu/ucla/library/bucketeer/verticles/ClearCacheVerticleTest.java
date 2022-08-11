
package edu.ucla.library.bucketeer.verticles;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.Rule;
import org.junit.runner.RunWith;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;
import info.freelibrary.util.StringUtils;
import info.freelibrary.util.HTTP;

import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.utils.TestUtils;
import edu.ucla.library.bucketeer.MessageCodes;
import edu.ucla.library.bucketeer.Config;

import java.io.IOException;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;

import io.vertx.config.ConfigRetriever;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.DeploymentOptions;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.web.codec.BodyCodec;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;

/**
 * Testing the mechanism used to clear Cantaloupe's cache.
 */
@RunWith(VertxUnitRunner.class)
// @FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ClearCacheVerticleTest {

    static {
        // For running this test independently, we need to configure the logging config source
        System.setProperty("logback.configurationFile", "src/test/resources/logback-test.xml");
    }

    /**
     * Logger for the <code>ClearCacheVerticleTest</code>.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(ClearCacheVerticleTest.class, Constants.MESSAGES);

    /**
     * Verticle name that tests are run for
     */
    private static final String VERTICLE_NAME = ClearCacheVerticle.class.getName();

    private static final String TIFFVERT_PATH = "src/test/resources/images/key.jpx";

    private static final String TIFFHORI_PATH = "src/test/resources/images/keyRotate.jpx";

    private static final String imageIDKeyJPX = "newKeyTest2.jpx";

    private static final String imageIDKey = "newKeyTest2";

    /**
     * Individual AWS credential
     */
    private static AWSCredentials myAWSCredentials;

    private static String s3Bucket = "unconfigured";

    /**
     * Deployment options for this test
     */
    // private static DeploymentOptions options;
    private JsonObject configs;

    /**
    * A test context from which references to Vert.x can be retrieved.
    */
    @Rule
    public RunTestOnContext myRunTestOnContextRule = new RunTestOnContext();

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

    // private List<String> vertIDs = new ArrayList<>();

    /**
     * Sets up the tests.
     *
     * @param aContext A testing environment
     */
    @Before
    public final void setup(final TestContext aContext) {
        final ConfigRetriever configRetriever = ConfigRetriever.create(Vertx.vertx());
        final Async asyncTask = aContext.async();

        configRetriever.getConfig(configuration -> {
            if (configuration.failed()) {
                aContext.fail(configuration.cause());
            } else {
                configs = configuration.result();
                TestUtils.complete(asyncTask);
                if (myAmazonS3 == null) {
                    final String s3AccessKey = configs.getString(Config.S3_ACCESS_KEY);
                    final String s3SecretKey = configs.getString(Config.S3_SECRET_KEY);

                    // get myAWSCredentials ready
                    myAWSCredentials = new BasicAWSCredentials(s3AccessKey, s3SecretKey);

                    // instantiate the myAmazonS3 client
                    myAmazonS3 = new AmazonS3Client(myAWSCredentials);
                }
                s3Bucket = configs.getString(Config.S3_BUCKET);
            }
        });
    }

    //Look into how Junit runs things serially
    // Test 1 just do deployVerticle without any config
    // Test 2 create Json Object with wrong config options
    //Test 3 Test without image ID *
    //Test 4 if given a fake response what is the result
    //look into S3 bucketVerticleTest and create AWS and put something into the S3 bucket
    //clear the cache and then put it into the bucket again and check it again myAmazonS3.put()
    //p

    /**
     * Tear down the testing environment.
     *
     * @param aContext A test context
     */
    @After
    public void tearDown(final TestContext aContext) {
        if (myVertID != null) {
            final Async async = aContext.async();
            myRunTestOnContextRule.vertx().undeploy(myVertID, undeployment -> {
                if (undeployment.failed()) {
                    aContext.fail(undeployment.cause());
                } else {
                    async.complete();
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
        // final Future<String> deployThisVerticle = deployNewVerticle(options);
        final Future<Integer> vertImageWidth = deployNewVerticle(configs).compose(verticleID -> {
            myVertID = verticleID;
            return putObjectS3(TIFFVERT_PATH).compose(success -> {
                return getInfoJsonObject();
            });
        });

        vertImageWidth.compose(width -> {
            final Future<Integer> horiImageWidth = sendMessage(imageIDKey).compose(success -> {
                return putObjectS3(TIFFHORI_PATH);
            }).compose(success -> {
                return getInfoJsonObject();
            });

            return horiImageWidth.compose(horiWidth -> {
                if(horiWidth == width) {
                    return sendMessage(imageIDKey).compose(success -> {
                            return Future.failedFuture("Cantaloupe cache was not properly cleared. Image remained the same after attempt of clearing.");
                        });
                } else {
                    return sendMessage(imageIDKey);
                }
            });

        }).onSuccess(result -> {
            myAmazonS3.deleteObject(s3Bucket, imageIDKeyJPX);
            TestUtils.complete(asyncTask);
        }).onFailure(failure -> {
            myAmazonS3.deleteObject(s3Bucket, imageIDKeyJPX);
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
        // final Future<String> deployThisVerticle = deployNewVerticle(options);
        deployNewVerticle(configs).compose(verticleID -> {
            myVertID = verticleID;
            return sendMessage(imageIDNull);
        }).onFailure(failure -> {
            if (Integer.parseInt(failure.getMessage()) == HTTP.INTERNAL_SERVER_ERROR){
                LOGGER.info(failure.getMessage());
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
        final JsonObject nullConfigs = configs;

        nullConfigs.put("bucketeer.iiif.cache.user", (String) null);
        nullConfigs.put("bucketeer.iiif.cache.password", (String) null);

        deployNewVerticle(nullConfigs)
            .onFailure(failure -> {
                LOGGER.info(failure.getMessage());
                TestUtils.complete(asyncTask);
            }).onSuccess(success ->{
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
        final JsonObject newConfigs = configs;

        newConfigs.put("bucketeer.iiif.cache.user", "username");
        newConfigs.put("bucketeer.iiif.cache.password", "password");

        deployNewVerticle(newConfigs)
            .onFailure(failure -> {
                LOGGER.info(failure.getMessage());
                TestUtils.complete(asyncTask);
            }).onSuccess(success ->{
                aContext.fail();
            });
    }

    /**
     * Gets info.json from Cantaloupe of specific image
     *
     * @param aContext
     */
    private Future<Integer> getInfoJsonObject() {
        final WebClient client = WebClient.create(Vertx.vertx());
        final Promise<Integer> promise = Promise.promise();

        client.getAbs("https://test.iiif.library.ucla.edu/iiif/2/newKeyTest2/info.json")
        .putHeader("content-type", "application/json")
        .send(result -> {
            if (result.succeeded()){
                promise.complete(result.result().body().toJsonObject().getInteger("width"));
            }
        });

        return promise.future();
   }

    /**
     * Puts image into AmazonS3 bucket
     */
    private Future<Void> putObjectS3(final String path) {
        final Promise<Void> promise = Promise.promise();
        try {
            myAmazonS3.putObject(s3Bucket, imageIDKeyJPX, new File(path));
            promise.complete();
        } catch(AmazonServiceException e) {
            LOGGER.error(e.getErrorMessage());
            promise.fail(e.getErrorMessage());
        }
        return promise.future();
    }

    /**
     * Sends message over eventBus to ClearCacheVerticle and verifies expected code is returned with failure
     */
    private Future<Void> sendMessage(final String imageID) {
        final Promise<Void> promise = Promise.promise();
        myRunTestOnContextRule.vertx()
                .eventBus().<JsonObject>send(VERTICLE_NAME, new JsonObject()
                .put("imageID", imageID), reply -> {
                    if (reply.failed()) {
                        ReplyException re = (ReplyException) reply.cause();
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
    private Future<String> deployNewVerticle(final JsonObject config) {
        final Promise<String> promise = Promise.promise();
        final DeploymentOptions option = new DeploymentOptions().setConfig(config);
        myRunTestOnContextRule.vertx()
            .deployVerticle(VERTICLE_NAME, option, deployment -> {
                if (deployment.succeeded()) {
                    promise.complete(deployment.result());
                } else {
                    promise.fail(deployment.cause());
                }
            });
        return promise.future();
    }
}