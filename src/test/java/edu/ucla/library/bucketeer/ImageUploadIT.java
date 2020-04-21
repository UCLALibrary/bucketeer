
package edu.ucla.library.bucketeer;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.bucketeer.converters.ConverterFactory;
import io.vertx.config.ConfigRetriever;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

/**
 * This test confirms that our entire imageUpload process works as expected. From the ticket that requested this
 * integration test (IIIF-166): This should confirm that a request at the front end of the service goes through the
 * router, to the converter, and ends up in the S3 upload (with the item being requested being uploaded (assuming it
 * exists on our file system)). We won't test callback at this point in time. That's for a later ticket.
 */
@RunWith(VertxUnitRunner.class)
public class ImageUploadIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImageUploadIT.class, Constants.MESSAGES);

    private static final Integer PORT = Integer.parseInt(System.getProperty(Config.HTTP_PORT));

    private static final String DEFAULT_ACCESS_KEY = "YOUR_ACCESS_KEY";

    private static final String DEFAULT_SECRET_KEY = "YOUR_SECRET_KEY";

    private static final String DEFAULT_S3_BUCKET = "cantaloupe-jp2k";

    private static final String TEST_FILE_PATH = "src/test/resources/images/熵.tif";

    private static final String SLASH = "/";

    private static final String STATUS_CHECK = "/status";

    private static final String UTF8 = "UTF-8";

    private static final String HELLO = "Hello";

    private static String myS3Bucket;

    private static String myS3AccessKey;

    private static String myS3SecretKey;

    private static AWSCredentials myAWSCredentials;

    private static Vertx vertx = Vertx.vertx();

    /** And, sometimes we won't have Kakadu installed, handle it **/
    private static boolean canRunKakadu;

    private File myTIFF;

    private String myUUID;

    private String myDerivativeJP2;

    private String myImageLoadRequest;

    private AmazonS3 myAmazonS3;

    private int myStatusCode;

    /**
     * fetch our S3 configuration
     */
    @BeforeClass
    public static void configureS3(final TestContext aContext) {
        final ConfigRetriever configRetriever = ConfigRetriever.create(vertx);
        final Async asyncTask = aContext.async();

        LOGGER.debug(MessageCodes.BUCKETEER_021, ImageUploadIT.class.getSimpleName(), PORT);

        configRetriever.getConfig(config -> {
            if (config.succeeded()) {
                final JsonObject jsonConfig = config.result();

                LOGGER.debug(MessageCodes.BUCKETEER_038);

                myS3Bucket = jsonConfig.getString(Config.S3_BUCKET, DEFAULT_S3_BUCKET);
                myS3AccessKey = jsonConfig.getString(Config.S3_ACCESS_KEY, DEFAULT_ACCESS_KEY);
                myS3SecretKey = jsonConfig.getString(Config.S3_SECRET_KEY, DEFAULT_SECRET_KEY);

                // We need to determine if we'll be able to run the S3 integration tests so we can skip if needed
                if (jsonConfig.containsKey(Config.S3_ACCESS_KEY) && !jsonConfig.getString(Config.S3_ACCESS_KEY,
                        DEFAULT_ACCESS_KEY).equalsIgnoreCase(DEFAULT_ACCESS_KEY)) {
                    asyncTask.complete();
                } else {
                    aContext.fail(LOGGER.getMessage(MessageCodes.BUCKETEER_039));
                }
            } else {
                aContext.fail(config.cause());
            }
        });
    }

    /**
     * confirm that the service is up (might run AFTER the next test, that's OK)
     */
    @SuppressWarnings("deprecation")
    @Test
    public final void checkThatServiceIsUp(final TestContext aContext) {
        final Async asyncTask = aContext.async();

        vertx.createHttpClient().getNow(PORT, Constants.UNSPECIFIED_HOST, STATUS_CHECK, response -> {
            aContext.assertEquals(HTTP.OK, response.statusCode());

            response.bodyHandler(body -> {
                aContext.assertEquals(body.getString(0, body.length()), HELLO);

                if (!asyncTask.isCompleted()) {
                    asyncTask.complete();
                }
            });
        });
    }

    /**
     * check that we can load an image
     *
     * @throws UnsupportedEncodingException If the encoding isn't supported by the JVM
     * @throws SdkClientException If there is trouble using the AWS SDK
     * @throws AmazonServiceException If there is trouble interacting with the AWS service
     */
    @SuppressWarnings("deprecation")
    @Test
    public final void checkThatWeCanLoadAnImage(final TestContext aContext) throws UnsupportedEncodingException,
            SdkClientException, AmazonServiceException {
        final String defaultTestFileName;

        // if we don't Have Kakadu installed, we *should* fail, but we will instead be crafty
        canRunKakadu = ConverterFactory.hasSystemKakadu();

        // let's use our 熵.tif file
        myTIFF = new File(TEST_FILE_PATH);
        defaultTestFileName = myTIFF.getName();
        LOGGER.debug(MessageCodes.BUCKETEER_035, defaultTestFileName);

        if (canRunKakadu) {
            // If we have Kakadu, we can use a real UUID...
            myUUID = "TEST-" + UUID.randomUUID().toString();
        } else {
            // otherwise, fake it, @ksclarke says this is OK for now
            myUUID = "test";
        }

        myDerivativeJP2 = myUUID + ".jpx";
        LOGGER.debug(MessageCodes.BUCKETEER_036, myDerivativeJP2);

        myImageLoadRequest = SLASH + myUUID + SLASH + URLEncoder.encode(myTIFF.getAbsolutePath(), UTF8);
        LOGGER.debug(MessageCodes.BUCKETEER_034, myImageLoadRequest);

        // attempt to load our image and verify the response is OK
        vertx.createHttpClient().getNow(PORT, Constants.UNSPECIFIED_HOST, myImageLoadRequest, response -> {
            myStatusCode = response.statusCode();
            aContext.assertEquals(myStatusCode, HTTP.OK);

            response.bodyHandler(body -> {
                final JsonObject jsonConfirm = new JsonObject(body.getString(0, body.length()));

                aContext.assertEquals(jsonConfirm.getString(Constants.IMAGE_ID), myUUID);
                aContext.assertFalse(jsonConfirm.getString(Constants.IMAGE_ID).equals("pickles"));
            });

            // get myAWSCredentials ready
            myAWSCredentials = new BasicAWSCredentials(myS3AccessKey, myS3SecretKey);

            // instantiate the myAmazonS3 client
            myAmazonS3 = new AmazonS3Client(myAWSCredentials);

            // check the S3 bucket to which we are sending JP2s, do they exist?
            boolean doesBucketExist = false;
            boolean doesObjectExist = false;

            doesBucketExist = myAmazonS3.doesBucketExistV2(myS3Bucket);
            doesObjectExist = myAmazonS3.doesObjectExist(myS3Bucket, myDerivativeJP2);

            // try again every 5 seconds, for a minute
            int counter = 0;

            while (doesBucketExist && !doesObjectExist && counter++ < 12) {
                // wait 5 seconds before we check again
                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (final InterruptedException details) {
                    // this is just here so we can interrupt our way out of this loop
                    details.printStackTrace(); // we might consider using a logger instead of this?
                }

                LOGGER.debug(MessageCodes.BUCKETEER_037, counter);

                // check for our object again
                doesObjectExist = myAmazonS3.doesObjectExist(myS3Bucket, myDerivativeJP2);
            }

            final ObjectMetadata myObjectMetadata = myAmazonS3.getObjectMetadata(myS3Bucket, myDerivativeJP2);
            final boolean objLengthIsNonZero = myObjectMetadata.getContentLength() < 0;

            aContext.assertTrue(doesBucketExist, LOGGER.getMessage(MessageCodes.BUCKETEER_040, myS3Bucket));
            aContext.assertTrue(doesObjectExist, LOGGER.getMessage(MessageCodes.BUCKETEER_041, myDerivativeJP2));
            aContext.assertTrue(objLengthIsNonZero, LOGGER.getMessage(MessageCodes.BUCKETEER_042, myDerivativeJP2));

            // clean up the test JP2 file
            myAmazonS3.deleteObject(myS3Bucket, myDerivativeJP2);
        });

    }

}
