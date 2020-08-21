
package edu.ucla.library.bucketeer;

import static io.vertx.ext.web.client.predicate.ResponsePredicate.SC_BAD_REQUEST;
import static io.vertx.ext.web.client.predicate.ResponsePredicate.SC_SUCCESS;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;
import info.freelibrary.util.StringUtils;

import io.vertx.config.ConfigRetriever;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;

/**
 * This test confirms that the imageUpload process works as expected: a request at the front end of the service goes
 * through the router, to the converter, and ends up in the S3 upload.
 */
@RunWith(VertxUnitRunner.class)
public class ImageUploadKakaduIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImageUploadKakaduIT.class, Constants.MESSAGES);

    private static final BodyCodec<JsonObject> JSON_CODEC = BodyCodec.jsonObject();

    private static final Integer PORT = Integer.parseInt(System.getProperty(Config.HTTP_PORT));

    private static final String DEFAULT_ACCESS_KEY = "YOUR_ACCESS_KEY";

    private static final String DEFAULT_SECRET_KEY = "YOUR_SECRET_KEY";

    private static final String DEFAULT_S3_BUCKET = "cantaloupe-jp2k";

    private static final String DEFAULT_S3_REGION = "us-east-1";

    private static final String TEST_FILE_PATH = "/images/熵.tif";

    @Rule
    public RunTestOnContext myTestContext = new RunTestOnContext();

    private AmazonS3 myS3Client;

    private String myS3Bucket;

    private String myUUID;

    private String myJP2;

    private Vertx myVertx;

    /**
     * Configure the test S3 configuration.
     *
     * @param aContext A test context
     */
    @Before
    public void setUp(final TestContext aContext) {
        myVertx = myTestContext.vertx();

        final ConfigRetriever configRetriever = ConfigRetriever.create(myVertx);
        final Async asyncTask = aContext.async();

        LOGGER.debug(MessageCodes.BUCKETEER_021, ImageUploadKakaduIT.class.getSimpleName(), PORT);

        configRetriever.getConfig(config -> {
            if (config.succeeded()) {
                final JsonObject jsonConfig = config.result();
                final String s3AccessKey = jsonConfig.getString(Config.S3_ACCESS_KEY, DEFAULT_ACCESS_KEY);
                final String s3SecretKey = jsonConfig.getString(Config.S3_SECRET_KEY, DEFAULT_SECRET_KEY);
                final String s3Region = jsonConfig.getString(Config.S3_REGION, DEFAULT_S3_REGION);
                final AWSCredentials credentials = new BasicAWSCredentials(s3AccessKey, s3SecretKey);
                final AWSCredentialsProvider provider = new AWSStaticCredentialsProvider(credentials);
                final AmazonS3ClientBuilder builder = AmazonS3ClientBuilder.standard();

                // Configure our S3 client builder
                builder.withCredentials(provider).setRegion(s3Region);

                // Check that we found all the values we needed to from the test configuration
                aContext.assertNotEquals(s3AccessKey, DEFAULT_ACCESS_KEY);
                aContext.assertNotEquals(s3SecretKey, DEFAULT_SECRET_KEY);

                myS3Bucket = jsonConfig.getString(Config.S3_BUCKET, DEFAULT_S3_BUCKET);
                myS3Client = builder.build();

                complete(asyncTask);
            } else {
                aContext.fail(config.cause());
            }

            configRetriever.close();
        });

        myUUID = UUID.randomUUID().toString();
    }

    /**
     * Cleans up after the test is done.
     *
     * @param aContext A test context
     */
    @After
    public void tearDown(final TestContext aContext) {
        if (myS3Client.doesBucketExistV2(myS3Bucket) && myJP2 != null) {
            myS3Client.deleteObject(myS3Bucket, myJP2);
            LOGGER.debug(MessageCodes.BUCKETEER_039, myJP2, myS3Bucket);
        }
    }

    /**
     * Check that we can load an image.
     *
     * @param aContext A test context
     * @throws UnsupportedEncodingException If the encoding isn't supported by the JVM
     * @throws SdkClientException If there is trouble using the AWS SDK
     * @throws AmazonServiceException If there is trouble interacting with the AWS service
     */
    @Test
    public final void testImageUpload(final TestContext aContext)
            throws UnsupportedEncodingException, SdkClientException, AmazonServiceException {
        final String filePath = URLEncoder.encode(TEST_FILE_PATH, StandardCharsets.UTF_8);
        final String request = StringUtils.format("/images/{}/{}", myUUID, filePath);
        final WebClient client = WebClient.create(myVertx);
        final Async asyncTask = aContext.async();

        myJP2 = myUUID + ".jpx";
        LOGGER.debug(MessageCodes.BUCKETEER_036, request);

        client.get(PORT, Constants.UNSPECIFIED_HOST, request).expect(SC_SUCCESS).as(JSON_CODEC).send(handler -> {
            if (handler.succeeded()) {
                final JsonObject body = handler.result().body();

                // Check that we received an S3 key back from our request
                aContext.assertEquals(body.getString(Constants.IMAGE_ID), myUUID);
                aContext.assertEquals(body.getString(Constants.FILE_PATH), TEST_FILE_PATH);

                // We keep checking S3 to see if our JP2 has been added to the S3 bucket, up to one minute
                for (int counter = 0; !asyncTask.isCompleted() && counter < 12; counter++) {
                    LOGGER.debug(MessageCodes.BUCKETEER_037, counter + 1);

                    if (myS3Client.doesBucketExistV2(myS3Bucket) && myS3Client.doesObjectExist(myS3Bucket, myJP2)) {
                        break; // If our JP2 exists, we can quit checking
                    }

                    waitFiveSeconds(aContext);
                }

                if (!asyncTask.isCompleted()) {
                    if (myS3Client.doesObjectExist(myS3Bucket, myJP2)) {
                        final ObjectMetadata metadata = myS3Client.getObjectMetadata(myS3Bucket, myJP2);
                        final long contentLength = metadata.getContentLength();

                        aContext.assertTrue(contentLength > 0, LOGGER.getMessage(MessageCodes.BUCKETEER_042, myJP2));
                        complete(asyncTask);
                    } else {
                        aContext.fail(LOGGER.getMessage(MessageCodes.BUCKETEER_038, myJP2));
                    }
                } // else, we've already failed an assertion
            } else {
                aContext.fail(handler.cause());
            }
        });
    }

    /**
     * Confirm that LoadImageHander fails if we do not provide an ID.
     *
     * @param aContext A testing context
     */
    @Test
    public void testLoadImageHandlerFailsWithMissingParam(final TestContext aContext) {
        final WebClient webClient = WebClient.create(myVertx);
        final Async asyncTask = aContext.async();

        webClient.get(PORT, Constants.UNSPECIFIED_HOST, "/12345/").expect(SC_BAD_REQUEST).send(handler -> {
            complete(asyncTask);
        });
    }

    /**
     * Confirm that LoadImageHander fails if we provide and invalid filePath.
     *
     * @param aContext A testing context
     */
    @Test
    public void confirmLoadImageHandlerFailsWithInvalideFilepath(final TestContext aContext) {
        final String filePath = "/12345/this-file-does-not-exist.tiff";
        final WebClient webClient = WebClient.create(myVertx);
        final Async asyncTask = aContext.async();

        // Tests loadImage path from our OpenAPI YAML file returns an error response when given incomplete data
        webClient.get(PORT, Constants.UNSPECIFIED_HOST, filePath).expect(SC_BAD_REQUEST).send(handler -> {
            complete(asyncTask);
        });
    }

    /**
     * Pause for five seconds and let Bucketeer work its uploading magic.
     *
     * @param aContext A test context that is failed if our waiting is interrupted
     */
    private void waitFiveSeconds(final TestContext aContext) {
        try {
            TimeUnit.SECONDS.sleep(5);
        } catch (final InterruptedException details) {
            aContext.fail(details);
        }
    }

    /**
     * Completes the supplied Async task.
     *
     * @param aAsyncTask A task to be completed
     */
    private void complete(final Async aAsyncTask) {
        if (!aAsyncTask.isCompleted()) {
            aAsyncTask.complete();
        }
    }
}
