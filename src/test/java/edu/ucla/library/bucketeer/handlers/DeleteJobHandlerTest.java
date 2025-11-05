
package edu.ucla.library.bucketeer.handlers;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.bucketeer.Config;
import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.HTTP;
import edu.ucla.library.bucketeer.Item;
import edu.ucla.library.bucketeer.Job;
import edu.ucla.library.bucketeer.Job.WorkflowState;
import edu.ucla.library.bucketeer.MessageCodes;
import edu.ucla.library.bucketeer.utils.TestUtils;

import io.vertx.core.buffer.Buffer;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

/**
 * Test class for the <code>DeleteJobHandler</code>.
 */
@RunWith(VertxUnitRunner.class)
public class DeleteJobHandlerTest extends AbstractBucketeerHandlerTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeleteJobHandlerTest.class, Constants.MESSAGES);

    private static final String TEST_URL = "/batch/jobs/" + JOB_NAME;

    /**
     * Tests deleting a job from the batch workflow.
     *
     * @param aContext A testing context
     */
    @Test
    public final void testDeleteJobHandler(final TestContext aContext) {
        final Async asyncTask = aContext.async();
        final int port = aContext.get(Config.HTTP_PORT);
        final WebClient webClient = WebClient.create(myVertx);

        // Get our shared data store so we can insert a job we want to test against
        myVertx.sharedData().<String, Job>getLocalAsyncMap(Constants.LAMBDA_JOBS, getMap -> {
            if (getMap.succeeded()) {
                final List<Item> items = Arrays.asList(new Item().setWorkflowState(WorkflowState.SUCCEEDED));

                getMap.result().put(JOB_NAME, new Job(JOB_NAME).setItems(items), put -> {
                    if (put.succeeded()) {
                        // Once we've put the job into our shared data store, try to delete it
                        webClient.delete(port, Constants.UNSPECIFIED_HOST, TEST_URL).send(deletion -> {
                            if (deletion.succeeded()) {
                                final HttpResponse<Buffer> response = deletion.result();
                                final int statusCode = response.statusCode();

                                aContext.assertEquals(HTTP.OK, statusCode, LOGGER.getMessage(MessageCodes.BUCKETEER_114,
                                        statusCode, response.statusMessage()));

                                TestUtils.complete(asyncTask);
                            } else {
                                aContext.fail(deletion.cause());
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
