package edu.ucla.library.bucketeer;

import static org.hamcrest.Matchers.equalTo;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.UUID;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import io.restassured.RestAssured;
import static io.restassured.RestAssured.get;
import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

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
    @Test
    public final void checkThatWeCanLoadAnImage() throws UnsupportedEncodingException {
        
        // let's use our test.if file
        myTIFF = new File("src/test/resources/images/test.tif");
        // and we'll pick a random ID for it
        myUUID = UUID.randomUUID().toString();
        
        myImageLoadRequest = "/" +  myUUID + "/" + URLEncoder.encode(myTIFF.getAbsolutePath(), "UTF-8");
        LOGGER.debug(MessageCodes.BUCKETEER_034, myImageLoadRequest);

        // now attempt to load it and verify the response is OK
        get(myImageLoadRequest).then()
            .assertThat()
            .statusCode(200)
            .body("imageId", equalTo(myUUID))
            .body("filePath", equalTo(URLEncoder.encode(myTIFF.getAbsolutePath(), "UTF-8")));
        
        // we should probably wait a bit for things to happen

        // then we should check the S3 bucket to which we are sending JP2s
        
    }
}
