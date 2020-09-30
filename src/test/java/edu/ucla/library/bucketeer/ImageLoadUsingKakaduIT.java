
package edu.ucla.library.bucketeer;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import info.freelibrary.util.StringUtils;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.predicate.ResponsePredicate;

/**
 * Tests the image load verticle when running with Kakadu support.
 */
@RunWith(VertxUnitRunner.class)
public class ImageLoadUsingKakaduIT {

    private static final String TEST_IMAGE_PATH = "/images/test.tif";

    private static final String PATH = "/images/{}/{}"; // image-id and file-path

    private static final String FILE_PATH = "file-path";

    private static final String IMAGE_ID = "image-id";

    /**
     * A test context from which references to Vert.x can be retrieved.
     */
    @Rule
    public RunTestOnContext myTestContext = new RunTestOnContext();

    private String myImageID;

    private Vertx myVertx;

    private int myPort;

    /**
     * Sets up the testing environment.
     *
     * @param aContext A test context
     * @throws IOxception The exception thrown if an available port cannot be claimed
     */
    @Before
    public void setUp(final TestContext aContext) throws IOException {
        myPort = Integer.parseInt(System.getProperty(Config.HTTP_PORT, "8888"));
        myImageID = UUID.randomUUID().toString();
        myVertx = myTestContext.vertx();
    }

    /**
     * Tests to make sure that an image can be converted and uploaded as expected.
     *
     * @param aContext A test context
     */
    @Test
    public void testTiffImageLoad(final TestContext aContext) {
        final String imagePath = URLEncoder.encode(TEST_IMAGE_PATH, StandardCharsets.UTF_8);
        final String path = StringUtils.format(PATH, myImageID, imagePath);
        final WebClient webClient = WebClient.create(myVertx);
        final Async asyncTask = aContext.async();

        webClient.get(myPort, Constants.UNSPECIFIED_HOST, path).send(submission -> {
            if (submission.succeeded()) {
                final HttpResponse<Buffer> response = submission.result();
                final JsonObject body = response.bodyAsJsonObject();

                aContext.assertEquals(HTTP.CREATED, response.statusCode());
                aContext.assertEquals(TEST_IMAGE_PATH, body.getString(FILE_PATH));
                aContext.assertEquals(myImageID, body.getString(IMAGE_ID));

                if (!asyncTask.isCompleted()) {
                    asyncTask.complete();
                }
            } else {
                aContext.fail(submission.cause());
            }
        });
    }

    /**
     * Tests to make sure that a missing image throws a 404.
     *
     * @param aContext A test context
     */
    @Test
    public void testMissingTiffImageLoad(final TestContext aContext) {
        final String imagePath = URLEncoder.encode("MISSING_IMAGE", StandardCharsets.UTF_8);
        final String path = StringUtils.format(PATH, myImageID, imagePath);
        final WebClient webClient = WebClient.create(myVertx);
        final Async asyncTask = aContext.async();

        webClient.get(myPort, Constants.UNSPECIFIED_HOST, path).expect(ResponsePredicate.SC_NOT_FOUND).send(test -> {
            if (test.succeeded()) {
                asyncTask.complete();
            } else {
                aContext.fail(test.cause());
            }
        });
    }
}
