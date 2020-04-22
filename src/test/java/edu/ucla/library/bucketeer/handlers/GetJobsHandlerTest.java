
package edu.ucla.library.bucketeer.handlers;

import org.junit.Test;
import org.junit.runner.RunWith;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.bucketeer.Config;
import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.HTTP;
import edu.ucla.library.bucketeer.Job;
import edu.ucla.library.bucketeer.MessageCodes;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

/**
 * Test class for the <code>GetJobsHandler</code>.
 */
@RunWith(VertxUnitRunner.class)
public class GetJobsHandlerTest extends AbstractBucketeerHandlerTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetJobsHandlerTest.class, Constants.MESSAGES);

    private static final String TEST_URL = "/batch/jobs/";

    /**
     * Tests getting the in-progress batch jobs.
     *
     * @param aContext A testing context
     */
    @Test
    public final void testGetJobsHandler(final TestContext aContext) {
        final Async asyncTask = aContext.async();
        final int port = aContext.get(Config.HTTP_PORT);
        final WebClient webClient = WebClient.create(myVertx);

        myVertx.sharedData().<String, Job>getLocalAsyncMap(Constants.LAMBDA_JOBS, getMap -> {
            if (getMap.succeeded()) {
                getMap.result().put(JOB_NAME, new Job(JOB_NAME), put -> {
                    if (put.succeeded()) {
                        webClient.get(port, Constants.UNSPECIFIED_HOST, TEST_URL).send(get -> {
                            if (get.succeeded()) {
                                final HttpResponse<Buffer> response = get.result();
                                final int statusCode = response.statusCode();
                                final String message = response.statusMessage();

                                if (response.statusCode() == HTTP.OK) {
                                    final JsonObject found = response.bodyAsJsonObject();
                                    final JsonObject expected = new JsonObject();

                                    expected.put(Constants.COUNT, 1);
                                    expected.put(Constants.JOBS, new JsonArray().add(JOB_NAME));

                                    aContext.assertEquals(expected, found);
                                    complete(asyncTask);
                                } else {
                                    aContext.fail(LOGGER.getMessage(MessageCodes.BUCKETEER_114, statusCode, message));
                                }
                            } else {
                                aContext.fail(get.cause());
                            }
                        });
                    } else {
                        aContext.fail(put.cause());
                    }
                });
            } else {
                aContext.fail(getMap.cause());
            }
        });
    }
}
