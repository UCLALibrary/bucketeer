package edu.ucla.library.bucketeer;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.UUID;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import io.restassured.RestAssured;
import io.vertx.config.ConfigRetriever;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

import static io.restassured.RestAssured.get;
import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;

/**
 * This test confirms that our entire imageUpload process works as expected.
 * From the ticket that requested this integration test (IIIF-166):
 * This should confirm that a request at the front end of the service goes
 * through the router, to the converter, and ends up in the S3 upload (with the
 * item being requested being uploaded (assuming it exists on our file system)).
 * We won't test callback at this point in time. That's for a later ticket.
 */
public class imageUploadIT {
    private static final Logger LOGGER = LoggerFactory.getLogger(imageUploadIT.class, Constants.MESSAGES);
    private static final Integer PORT = Integer.parseInt(System.getProperty("http.port"));
    private File myTIFF;
    private String myUUID;
    private String myImageLoadRequest;
    private AmazonS3 amazonS3;
    private static final String DEFAULT_ACCESS_KEY = "YOUR_ACCESS_KEY";
    private static final String DEFAULT_SECRET_KEY = "YOUR_SECRET_KEY";
    private static final String DEFAULT_S3_BUCKET = "cantaloupe-jp2k";
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
        RestAssured.baseURI = "http://localhost";
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
    public final void checkThatWeCanLoadAnImage() throws UnsupportedEncodingException {
        
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
        
        // let's use our test.tif file
        myTIFF = new File("src/test/resources/images/test.tif");
        // and we'll pick a random ID for it
        myUUID = UUID.randomUUID().toString();
        
        myImageLoadRequest = "/" +  myUUID + "/" + URLEncoder.encode(myTIFF.getAbsolutePath(), "UTF-8");
        LOGGER.debug(MessageCodes.BUCKETEER_034, myImageLoadRequest);

        });


        if (isExecutable) {
            
            // let's use our test.tif file
            myTIFF = new File("src/test/resources/images/test.tif");
            final String defaultTestFileName = myTIFF.getName();
            LOGGER.debug("defaultTestFileName:");
            LOGGER.debug(defaultTestFileName);
            // and we'll pick a random ID for it
            myUUID = UUID.randomUUID().toString();
            
            myImageLoadRequest = "/" +  myUUID + "/" + URLEncoder.encode(myTIFF.getAbsolutePath(), "UTF-8");
            LOGGER.debug(MessageCodes.BUCKETEER_024, myImageLoadRequest);
    
            // now attempt to load it and verify the response is OK
            get(myImageLoadRequest).then()
                .assertThat()
                .statusCode(200)
                .body("imageId", equalTo(myUUID))
                .body("filePath", equalTo(URLEncoder.encode(myTIFF.getAbsolutePath(), "UTF-8")));
            
            // we should probably wait a bit for things to happen
            
            // get myAWSCredentials ready
            myAWSCredentials = new BasicAWSCredentials(myS3AccessKey, myS3SecretKey);
            
            // instantiate the amazonS3 client
            amazonS3 = new AmazonS3Client(myAWSCredentials);
    
            // then we should check the S3 bucket to which we are sending JP2s
            assertTrue(amazonS3.doesBucketExist(myS3Bucket));
            assertTrue(amazonS3.doesObjectExist(myS3Bucket, defaultTestFileName ));
        } else {
            LOGGER.debug("configuration not found, skipping integration test");
        }
        
    }
}
