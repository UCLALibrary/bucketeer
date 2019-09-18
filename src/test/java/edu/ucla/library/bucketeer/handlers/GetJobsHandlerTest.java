
package edu.ucla.library.bucketeer.handlers;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

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
 * Test class for the <code>GetJobsHandler</code>.
 */
@RunWith(VertxUnitRunner.class)
public class GetJobsHandlerTest extends AbstractBucketeerHandlerTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetJobsHandlerTest.class, Constants.MESSAGES);

    private static final String TEST_URL = "/batch/jobs/";

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
     * Tests getting the in-progress batch jobs.
     *
     * @param aContext A testing context
     */
    @Test
    @SuppressWarnings("deprecation")
    public final void testGetJobsHandler(final TestContext aContext) {
        final Async asyncTask = aContext.async();
        final int port = aContext.get(Config.HTTP_PORT);
        final RequestOptions request = new RequestOptions();

        request.setPort(port).setHost(Constants.UNSPECIFIED_HOST).setURI(TEST_URL);

        myVertx.createHttpClient().getNow(request, response -> {
            final int statusCode = response.statusCode();

            if (statusCode == HTTP.OK) {
                response.bodyHandler(body -> {
                    final JsonObject expected = new JsonObject();

                    expected.put(Constants.COUNT, 1);
                    expected.put(Constants.JOBS, new JsonArray().add(JOB_NAME));

                    assertEquals(expected, body.toJsonObject());
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
