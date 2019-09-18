
package edu.ucla.library.bucketeer.handlers;

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
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

/**
 * Test class for the <code>DeleteJobHandler</code>.
 */
@RunWith(VertxUnitRunner.class)
public class DeleteJobHandlerTest extends AbstractBucketeerHandlerTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeleteJobHandlerTest.class, Constants.MESSAGES);

    private static final String TEST_URL = "/batch/jobs/" + JOB_NAME;

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
     * Tests deleting a job from the batch workflow.
     *
     * @param aContext A testing context
     */
    @Test
    @SuppressWarnings("deprecation")
    public final void testDeleteJobHandler(final TestContext aContext) {
        final Async asyncTask = aContext.async();
        final int port = aContext.get(Config.HTTP_PORT);
        final RequestOptions request = new RequestOptions();

        request.setPort(port).setHost(Constants.UNSPECIFIED_HOST).setURI(TEST_URL);

        myVertx.createHttpClient().delete(request, response -> {
            final int statusCode = response.statusCode();

            if (statusCode != HTTP.NO_CONTENT) {
                final String statusMessage = response.statusMessage();

                aContext.fail(LOGGER.getMessage(MessageCodes.BUCKETEER_114, statusCode, statusMessage));
            } else {
                asyncTask.complete();
            }
        }).end();
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }

}
