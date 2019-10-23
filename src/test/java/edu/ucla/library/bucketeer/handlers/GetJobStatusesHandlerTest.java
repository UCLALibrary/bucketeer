
package edu.ucla.library.bucketeer.handlers;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;
import info.freelibrary.util.StringUtils;

import edu.ucla.library.bucketeer.Config;
import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.HTTP;
import edu.ucla.library.bucketeer.MessageCodes;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

/**
 * Test class for the <code>GetJobStatusesHandler</code>.
 */
@RunWith(VertxUnitRunner.class)
public class GetJobStatusesHandlerTest extends AbstractBucketeerHandlerTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetJobStatusesHandlerTest.class, Constants.MESSAGES);

    private static final String TEST_URL = "/batch/jobs/" + JOB_NAME;

    private static final File FILE_PATH = new File("src/test/resources/images/test.tif");

    private static final File FAIL_PATH = new File("src/test/resources/images/fail.tif");

    private static final File JSON_STATUSES = new File("src/test/resources/json/jobStatuses.json");

    /**
     * Test set up.
     *
     * @param aContext A testing context
     * @throws Exception When a problem occurs while trying to set up the test.
     */
    @Override
    @Before
    public void setUp(final TestContext aContext) throws Exception {
        super.setUp(aContext);
    }

    /**
     * Tests getting job statuses.
     *
     * @param aContext A testing context
     */
    @Test
    @SuppressWarnings("deprecation")
    public final void testGetJobStatusesHandler(final TestContext aContext) {
        final Async asyncTask = aContext.async();
        final int port = aContext.get(Config.HTTP_PORT);
        final RequestOptions request = new RequestOptions();

        request.setPort(port).setHost(Constants.UNSPECIFIED_HOST).setURI(TEST_URL);

        myVertx.createHttpClient().getNow(request, response -> {
            final int statusCode = response.statusCode();

            if (statusCode == HTTP.OK) {
                response.bodyHandler(body -> {
                    try {
                        final String json = StringUtils.read(JSON_STATUSES);
                        final JsonObject expected = new JsonObject(json);
                        final JsonArray jobs = expected.getJsonArray(Constants.JOBS);

                        // Update files' paths for the machine that's running the test
                        for (int index = 0; index < jobs.size(); index++) {
                            if (index != 7) {
                                jobs.getJsonObject(index).put(Constants.FILE_PATH, FILE_PATH.getAbsolutePath());
                            } else {
                                jobs.getJsonObject(index).put(Constants.FILE_PATH, FAIL_PATH.getAbsolutePath());
                            }
                        }

                        assertEquals(expected, body.toJsonObject());
                    } catch (final IOException details) {
                        aContext.fail(details);
                    }
                });

                if (!asyncTask.isCompleted()) {
                    asyncTask.complete();
                }
            } else {
                final String statusMessage = response.statusMessage();

                aContext.fail(LOGGER.getMessage(MessageCodes.BUCKETEER_114, statusCode, statusMessage));
            }
        });
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }

}
