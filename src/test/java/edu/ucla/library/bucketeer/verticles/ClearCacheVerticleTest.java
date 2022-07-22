
package edu.ucla.library.bucketeer.verticles;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.Rule;
import org.junit.runner.RunWith;

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

    private static final String imageIDKey = "clearCacheTest.jpx";

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
        final ConfigRetriever configRetriever = ConfigRetriever.create(Vertx.vertx());
        final Async asyncTask = aContext.async();

        configRetriever.getConfig(configuration -> {
            if (configuration.failed()) {
                aContext.fail(configuration.cause());
            } else {
                final JsonObject config = configuration.result();
                final DeploymentOptions options = new DeploymentOptions().setConfig(config);

                myRunTestOnContextRule.vertx()
                    .deployVerticle(VERTICLE_NAME, options, deployment -> {
                        if (deployment.succeeded()) {
                            myVertID = deployment.result();
                            TestUtils.complete(asyncTask);

                            if (myAmazonS3 == null) {
                                final String s3AccessKey = config.getString(Config.S3_ACCESS_KEY);
                                final String s3SecretKey = config.getString(Config.S3_SECRET_KEY);

                                // get myAWSCredentials ready
                                myAWSCredentials = new BasicAWSCredentials(s3AccessKey, s3SecretKey);

                                // instantiate the myAmazonS3 client
                                myAmazonS3 = new AmazonS3Client(myAWSCredentials);
                            }

                            s3Bucket = config.getString(Config.S3_BUCKET);

                            try {
                                myAmazonS3.putObject(s3Bucket, imageIDKey, new File(TIFFVERT_PATH));
                            } catch(AmazonServiceException e) {
                                LOGGER.error(e.getErrorMessage());
                            }
                        } else {
                            aContext.fail(deployment.cause());
                        }
                    });
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
        /* An issue popped up during initial testing that gave different results when multiple
         * requests are queued so multiple messages are sent over the eventBus to see if the correct
         * response is received
         */

        final WebClient client = WebClient.create(Vertx.vertx());
        final Async asyncTask = aContext.async();

        //look into futures and compose
        // for (int index = 0; index < 500; index++) {

            //reply code should be 202
            // sendMessage(imageIDKey, asyncTask, HTTP.ACCEPTED, aContext);
        // }

        Future.<Void>future(getInfo -> {
            LOGGER.info("Entered getInfoJsonObject");
            getInfoJsonObject(getInfo);
        })
        .compose(success -> {
            return Future.<Void>future(sendMessage -> {
                myRunTestOnContextRule.vertx()
                .eventBus().<JsonObject>send(VERTICLE_NAME, new JsonObject()
                .put("imageID", imageIDKey), reply -> {
                    if (reply.failed()) {
                        ReplyException re = (ReplyException) reply.cause();

                        LOGGER.info(String.valueOf(re.failureCode()));
                        aContext.fail(re.getMessage());
                    } else {
                        sendMessage.complete();
                    }
                });
            });
        })
        .compose(success -> {
            return Future.<Void>future(putObS3 -> {
                try {
                    myAmazonS3.putObject(s3Bucket, imageIDKey, new File(TIFFHORI_PATH));
                    putObS3.complete();
                } catch(AmazonServiceException e) {
                    LOGGER.error(e.getErrorMessage());
                    putObS3.fail(e.getErrorMessage());
                }
                LOGGER.info("Entered into putObjectS3");
            });
        })
        .compose(success -> {
            return Future.<Void>future(getInfo -> {
                LOGGER.info("Entered getInfoJsonObject");
                getInfoJsonObject(getInfo);
            });
        })
        .compose(success -> {
            return Future.<Void>future(finish -> {
                LOGGER.info("Enter into final compose success");
                TestUtils.complete(asyncTask);
            });
        });


        // .compose(success -> {
        //     return putObjectS3();
        // }).compose(success -> {
        //     return getInfoJsonObject();
        // })

        // sendMessage(imageIDKey, asyncTask, HTTP.ACCEPTED, aContext);


        myAmazonS3.deleteObject(s3Bucket, imageIDKey);

        // response -> {
        //         if (response.succeeded()){
        //             JsonObject myObject = new JsonObject(response.body());
        //         } else {
        //             LOGGER.error(response.cause(), response.cause().getMessage());
        //         }
        // });
        // try {
        //     HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        //     JsonObject myObject = new JsonObject(response.body());
        // } catch (IOException e) {
        //     e.printStackTrace();
        // } catch (InterruptedException e) {
        //     e.printStackTrace();
        // }

        // clean up our test files
    }

    private void getInfoJsonObject(final Promise<Void> promise) {
        final WebClient client = WebClient.create(Vertx.vertx());
        LOGGER.info("Entered getInfoJsonObject");
        client.getAbs("https://test.iiif.library.ucla.edu/iiif/2/ClearCacheTest/info.json")
        .putHeader("content-type", "application/json")
        .send(res -> {
          if(res.succeeded()) {
            //   JsonObject body = res.result().body().toJsonObject();
            LOGGER.info("Result succeeeded");
            LOGGER.info(res.result().body().toString());
            promise.complete();
          } else {
            LOGGER.error(res.cause(), res.cause().getMessage());
            promise.complete();
          }
        });

    }

    private Future<String> putObjectS3() {
        Future<String> future = Future.future();
        try {
            myAmazonS3.putObject(s3Bucket, imageIDKey, new File(TIFFHORI_PATH));
        } catch(AmazonServiceException e) {
            LOGGER.error(e.getErrorMessage());
        }
        LOGGER.info("Entered into putObjectS3");
        return future;
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

        sendMessage(imageIDNull, asyncTask, HTTP.INTERNAL_SERVER_ERROR, aContext);
    }

    /**
     * Sends message over eventBus to ClearCacheVerticle and verifies expected code is returned with failure
     */
    private void sendMessage(final String imageID, final Async asyncTask, final Integer replyCode, final TestContext aContext){
        myRunTestOnContextRule.vertx()
                .eventBus().<JsonObject>send(VERTICLE_NAME, new JsonObject()
                .put("imageID", imageID), reply -> {
                    if (reply.failed()) {
                        ReplyException re = (ReplyException) reply.cause();

                        LOGGER.info(String.valueOf(re.failureCode()));
                        if (replyCode != re.failureCode()) {
                            aContext.fail(re.getMessage());
                        } else {
                            TestUtils.complete(asyncTask);
                        }
                    } else {
                        TestUtils.complete(asyncTask);
                    }
                });
    }
}
