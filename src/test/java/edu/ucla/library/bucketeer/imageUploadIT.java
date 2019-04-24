package edu.ucla.library.bucketeer;

import static org.hamcrest.Matchers.equalTo;

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
     */
    @Test
    public final void checkThatWeCanLoadAnImage() {
        // attempt to load a testImage.tif file
        // TODO: get the system path to our test image
        get("/12345/imageFile.tif").then()
            .assertThat()
            .statusCode(200)
            .body("imageId", equalTo("12345"))
            .body("filePath", equalTo("imageFile.tif"));
    }
}
