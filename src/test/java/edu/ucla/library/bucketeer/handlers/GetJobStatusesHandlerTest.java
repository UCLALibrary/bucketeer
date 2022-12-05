
package edu.ucla.library.bucketeer.handlers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.Test;
import org.junit.runner.RunWith;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;
import info.freelibrary.util.StringUtils;

import edu.ucla.library.bucketeer.Config;
import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.HTTP;
import edu.ucla.library.bucketeer.Item;
import edu.ucla.library.bucketeer.Job;
import edu.ucla.library.bucketeer.Job.WorkflowState;
import edu.ucla.library.bucketeer.MessageCodes;
import edu.ucla.library.bucketeer.utils.TestUtils;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

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
     * Tests getting job statuses.
     *
     * @param aContext A testing context
     */
    @Test
    public final void testGetJobStatusesHandler(final TestContext aContext) {
        final Async asyncTask = aContext.async();

        // Get the jobs map so we can put our test job in there
        myVertx.sharedData().<String, Job>getLocalAsyncMap(Constants.LAMBDA_JOBS, getMap -> {
            if (getMap.succeeded()) {
                try {
                    final JsonObject statuses = new JsonObject(StringUtils.read(JSON_STATUSES));
                    final JsonArray items = statuses.getJsonArray(Constants.JOBS);
                    final AsyncMap<String, Job> map = getMap.result();

                    map.put(JOB_NAME, getJob(items), new TestHandler(aContext, asyncTask, statuses));
                } catch (final IOException details) {
                    aContext.fail(details);
                }
            } else {
                aContext.fail(getMap.cause());
            }
        });
    }

    /**
     * Gets a fake job build from our test fixture.
     *
     * @param aItemArray An array of fake items
     * @return A fake job for testing against
     */
    private Job getJob(final JsonArray aItemArray) {
        final List<Item> items = new ArrayList<>();
        final Job job = new Job(JOB_NAME);

        job.setItems(items).setSlackHandle("ksclarke");

        // Populate our pretend in-process job
        for (int index = 0; index < aItemArray.size(); index++) {
            final Item item = new Item().setID(aItemArray.getJsonObject(index).getString(Constants.IMAGE_ID));

            if (index != 7) {
                item.setFilePath(Optional.of(FILE_PATH.getAbsolutePath()));
            } else {
                item.setFilePath(Optional.of(FAIL_PATH.getAbsolutePath()));
                item.setWorkflowState(WorkflowState.FAILED);
            }

            items.add(item);
        }

        return job;
    }

    /**
     * TestHandler handles the result of a test of our job statuses.
     */
    private final class TestHandler implements Handler<AsyncResult<Void>> {

        private final TestContext myContext;

        private final Async myAsyncTask;

        private final JsonObject myExpectedObj;

        private final JsonArray myJob;

        /**
         * Creates a new TestHandler to handle the results of our test.
         *
         * @param aContext A test context
         * @param aAsyncTask A asynchronous task
         * @param aExpectedObj The JSON object test fixture
         */
        private TestHandler(final TestContext aContext, final Async aAsyncTask, final JsonObject aExpectedObj) {
            myJob = aExpectedObj.getJsonArray(Constants.JOBS);
            myExpectedObj = aExpectedObj;
            myAsyncTask = aAsyncTask;
            myContext = aContext;
        }

        @Override
        public void handle(final AsyncResult<Void> aEvent) {
            final int port = myContext.get(Config.HTTP_PORT);
            final WebClient webClient = WebClient.create(myVertx);

            if (aEvent.succeeded()) {
                webClient.get(port, Constants.UNSPECIFIED_HOST, TEST_URL).send(get -> {
                    if (get.succeeded()) {
                        final HttpResponse<Buffer> response = get.result();
                        final int statusCode = response.statusCode();
                        final String message = response.statusMessage();

                        if (response.statusCode() == HTTP.OK) {
                            // Update file paths to match the absolute paths on the machine that's running the test
                            for (int index = 0; index < myJob.size(); index++) {
                                if (index != 7) {
                                    myJob.getJsonObject(index).put(Constants.FILE_PATH, FILE_PATH.getAbsolutePath());
                                } else {
                                    myJob.getJsonObject(index).put(Constants.FILE_PATH, FAIL_PATH.getAbsolutePath());
                                }
                            }

                            myContext.assertEquals(myExpectedObj, response.bodyAsJsonObject());

                            TestUtils.complete(myAsyncTask);
                        } else {
                            myContext.fail(LOGGER.getMessage(MessageCodes.BUCKETEER_114, statusCode, message));
                        }
                    } else {
                        myContext.fail(LOGGER.getMessage(MessageCodes.BUCKETEER_508, get.cause().getMessage()));
                    }
                });
            } else {
                myContext.fail(aEvent.cause());
            }
        }
    }
}
