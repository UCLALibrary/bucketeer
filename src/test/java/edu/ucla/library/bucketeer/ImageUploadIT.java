package edu.ucla.library.bucketeer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import io.restassured.RestAssured;
import io.vertx.config.ConfigRetriever;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;

/**
 * This test confirms that our entire imageUpload process works as expected.
 * From the ticket that requested this integration test (IIIF-166):
 * This should confirm that a request at the front end of the service goes
 * through the router, to the converter, and ends up in the S3 upload (with the
 * item being requested being uploaded (assuming it exists on our file system)).
 * We won't test callback at this point in time. That's for a later ticket.
 */
public class ImageUploadIT {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImageUploadIT.class, Constants.MESSAGES);
    private static final Integer PORT = Integer.parseInt(System.getProperty("http.port"));
    private static final String DEFAULT_ACCESS_KEY = "YOUR_ACCESS_KEY";
    private static final String DEFAULT_SECRET_KEY = "YOUR_SECRET_KEY";
    private static final String DEFAULT_S3_BUCKET = "cantaloupe-jp2k";
    private static final String TEST_FILE_PATH = "src/test/resources/images/test.tif";
    private static final String SLASH = "/";
    private static final String PING = "/ping";
    private static final String UTF8 = "UTF-8";
    private static final String DOESNOTEXIST = ") does not exist!";
    private static final String HELLO = "Hello";

    private File myTIFF;
    private String myUUID;
    private String myDerivativeJP2;
    private String myImageLoadRequest;
    private AmazonS3 myAmazonS3;
    private int myStatusCode;

    private String myS3Bucket;
    private String myS3AccessKey;
    private String myS3SecretKey;
    private AWSCredentials myAWSCredentials;


    /** We can't, as of yet, execute these tests without a non-default S3 configuration */
    private boolean isExecutable;

    /**
     * Use RestAssured to connect to our service
     */
    @BeforeClass
    public static void configureRestAssured() {
        RestAssured.baseURI = "0.0.0.0";
        RestAssured.port = PORT;
        LOGGER.debug(MessageCodes.BUCKETEER_021, RestAssured.port);
    }

    /**
     * And... turn off RestAssured...
     */
    @AfterClass
    public static void unconfigureRestAssured() {
        RestAssured.reset();
    }

    /**
     * check that we can load an image
     * @throws UnsupportedEncodingException
     */
    @SuppressWarnings("deprecation")
    @Test
    public final void checkThatWeCanLoadAnImage() throws UnsupportedEncodingException,
            SdkClientException, AmazonServiceException {

        final Vertx vertx;
        vertx = Vertx.vertx();
        final ConfigRetriever configRetriever = ConfigRetriever.create(vertx);

        configRetriever.getConfig(config -> {
            if (config.succeeded()) {
                LOGGER.debug("config succeeded");
                final JsonObject jsonConfig = config.result();
                LOGGER.debug(jsonConfig.getString(Config.S3_BUCKET, DEFAULT_S3_BUCKET));
                myS3Bucket = jsonConfig.getString(Config.S3_BUCKET, DEFAULT_S3_BUCKET);
                myS3AccessKey = jsonConfig.getString(Config.S3_ACCESS_KEY, DEFAULT_ACCESS_KEY);
                myS3SecretKey = jsonConfig.getString(Config.S3_SECRET_KEY, DEFAULT_SECRET_KEY);

                // We need to determine if we'll be able to run the S3 integration tests so we can skip if needed
                if (jsonConfig.containsKey(Config.S3_ACCESS_KEY) && !jsonConfig.getString(Config.S3_ACCESS_KEY,
                        DEFAULT_ACCESS_KEY).equalsIgnoreCase(DEFAULT_ACCESS_KEY)) {
                    isExecutable = true;
                }
            }

        });


        if (isExecutable) {

            // let's use our test.tif file
            myTIFF = new File(TEST_FILE_PATH);
            final String defaultTestFileName = myTIFF.getName();
            LOGGER.debug(MessageCodes.BUCKETEER_035, defaultTestFileName);
            // and we'll pick a random ID for it
            myUUID = "TEST-" + UUID.randomUUID().toString();
            myDerivativeJP2 = myUUID + ".jpx";
            LOGGER.debug(MessageCodes.BUCKETEER_036, myDerivativeJP2);

            myImageLoadRequest = SLASH + myUUID + SLASH + URLEncoder.encode(myTIFF.getAbsolutePath(), UTF8);
            LOGGER.debug(MessageCodes.BUCKETEER_034, myImageLoadRequest);

            // first, let's sanity-check our service ping endpoint before we do anything real
            vertx.createHttpClient().getNow(PORT, Constants.UNSPECIFIED_HOST, PING, response -> {
                // validate the response
                myStatusCode = response.statusCode();
                assertThat(myStatusCode).isEqualTo(200);
                response.bodyHandler(body -> {
                    assertThat(body.getString(0, body.length())).isEqualTo(HELLO);
                });
                LOGGER.debug("JP2-Bucketeer service confirmed to be available.");
            });

            // now attempt to load it and verify the response is OK
            vertx.createHttpClient().getNow(PORT, Constants.UNSPECIFIED_HOST, myImageLoadRequest, response -> {
                // validate the response
                myStatusCode = response.statusCode();
                assertThat(myStatusCode).isEqualTo(200);
                response.bodyHandler(body -> {
                    final JsonObject jsonConfirm = new JsonObject(body.getString(0, body.length()));
                    assertThat(jsonConfirm.getString(Constants.IMAGE_ID)).isEqualTo(myUUID);
                    // @ksclarke wanted me to be sure there aren't any pickles in here --@hardyoyo
                    assertThat(jsonConfirm.getString(Constants.IMAGE_ID)).isNotEqualTo("pickles");
                });

            });

            // get myAWSCredentials ready
            myAWSCredentials = new BasicAWSCredentials(myS3AccessKey, myS3SecretKey);

            // instantiate the myAmazonS3 client
            myAmazonS3 = new AmazonS3Client(myAWSCredentials);

            // check the S3 bucket to which we are sending JP2s, do they exist?
            boolean doesBucketExist = false;
            boolean doesObjectExist = false;
            doesBucketExist = myAmazonS3.doesBucketExistV2(myS3Bucket);
            doesObjectExist = myAmazonS3.doesObjectExist(myS3Bucket,myDerivativeJP2);

            // try again every 5 seconds, for a minute
            int counter = 0;
            while (doesBucketExist && !doesObjectExist && counter++ < 12) {
                // wait 5 seconds before we check again
                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException e) {
                    // this is just here so we can interrupt our way out of this loop
                    e.printStackTrace(); // we might consider usin a logger instead of this?
                }
                LOGGER.debug(MessageCodes.BUCKETEER_037 , counter);
                // check for our object again
                doesObjectExist = myAmazonS3.doesObjectExist(myS3Bucket,myDerivativeJP2);
            }
            assertTrue(" --> Bucket (" + myS3Bucket + DOESNOTEXIST, doesBucketExist);
            assertTrue(" --> JP2 (" + myDerivativeJP2 + DOESNOTEXIST, doesObjectExist);
        } else {
            LOGGER.debug("configuration not found, skipping integration test");
        }

    }
}
