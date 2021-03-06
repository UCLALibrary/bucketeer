
package edu.ucla.library.bucketeer.handlers;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;
import info.freelibrary.util.StringUtils;

import edu.ucla.library.bucketeer.Config;
import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.HTTP;
import edu.ucla.library.bucketeer.Item;
import edu.ucla.library.bucketeer.Job;
import edu.ucla.library.bucketeer.MessageCodes;

import ch.qos.logback.classic.Level;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

/**
 * Test class for the <code>BatchJobStatusHandler</code>.
 */
@RunWith(VertxUnitRunner.class)
public class BatchJobStatusHandlerTest extends AbstractBucketeerHandlerTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(BatchJobStatusHandlerTest.class, Constants.MESSAGES);

    // Parameters for the URI: jobName, imageId, success
    private static final String PATCH_BATCH_URI = "/batch/jobs/{}/{}/{}";

    private static final String TEST_ARK = "ark%3A%2F13030%2Fhb000003n9";

    private static final String TRUE = "true";

    @Rule
    public TestName myTestName = new TestName();

    /**
     * Tests the <code>BatchJobStatusHandler</code>.
     *
     * @param aContext A testing context
     */
    @Test
    public final void testMethodNotAllowedResponse(final TestContext aContext) {
        LOGGER.debug(MessageCodes.BUCKETEER_017, myTestName.getMethodName());

        final Async asyncTask = aContext.async();
        final int port = aContext.get(Config.HTTP_PORT);
        final RequestOptions request = new RequestOptions();
        final WebClient webClient = WebClient.create(myVertx);
        final String uri = StringUtils.format(PATCH_BATCH_URI, JOB_NAME, TEST_ARK, TRUE);

        request.setPort(port).setHost(Constants.UNSPECIFIED_HOST).setURI(uri);

        webClient.get(port, Constants.UNSPECIFIED_HOST, uri).send(get -> {
            if (get.succeeded()) {
                final HttpResponse<Buffer> response = get.result();
                final int statusCode = response.statusCode();

                if (statusCode == HTTP.METHOD_NOT_ALLOWED) {
                    asyncTask.complete();
                } else {
                    aContext.fail(LOGGER.getMessage(MessageCodes.BUCKETEER_083, statusCode, response.statusMessage()));
                }
            } else {
                aContext.fail(get.cause());
            }
        });
    }

    /**
     * Tests the <code>BatchJobStatusHandler</code>.
     *
     * @param aContext A testing context
     */

    @Test
    @SuppressWarnings("deprecation")
    public final void testInternalServerErrorResponse(final TestContext aContext) {
        final Async asyncTask = aContext.async();
        final int port = aContext.get(Config.HTTP_PORT);
        final RequestOptions options = new RequestOptions();
        final String uri = StringUtils.format(PATCH_BATCH_URI, JOB_NAME, TEST_ARK, TRUE);
        final HttpClient client = myVertx.createHttpClient();
        final Level level = setLogLevel(BatchJobStatusHandler.class, Level.OFF);

        LOGGER.debug(MessageCodes.BUCKETEER_017, myTestName.getMethodName());
        options.setPort(port).setHost(Constants.UNSPECIFIED_HOST).setURI(uri);

        client.request(HttpMethod.PATCH, options, response -> {
            final int statusCode = response.statusCode();
            final String statusMessage = response.statusMessage();

            setLogLevel(BatchJobStatusHandler.class, level);

            if (statusCode == HTTP.INTERNAL_SERVER_ERROR) {
                asyncTask.complete();
            } else {
                aContext.fail(LOGGER.getMessage(MessageCodes.BUCKETEER_022, statusCode, statusMessage));
            }
        }).end();
    }

    /**
     * Tests the <code>BatchJobStatusHandler</code>.
     *
     * @param aContext A testing context
     */
    @Test
    public final void testSuccessfulStatusUpdate(final TestContext aContext) {
        final Async asyncTask = aContext.async();
        final int port = aContext.get(Config.HTTP_PORT);
        final WebClient webClient = WebClient.create(myVertx);
        final String patchUri = StringUtils.format(PATCH_BATCH_URI, JOB_NAME, TEST_ARK, TRUE);

        LOGGER.debug(MessageCodes.BUCKETEER_017, myTestName.getMethodName());

        myVertx.sharedData().<String, Job>getLocalAsyncMap(Constants.LAMBDA_JOBS, getMap -> {
            if (getMap.succeeded()) {
                final Job job = new Job(JOB_NAME);
                final String firstItemId = URLDecoder.decode(TEST_ARK, StandardCharsets.UTF_8);

                // Add two items to the job so it doesn't complete when we update the status of one of them
                job.setItems(Arrays.asList(new Item().setID(firstItemId), new Item().setID("item2")));

                // Put the job in our jobs queue so we can test against it
                getMap.result().put(JOB_NAME, job, put -> {
                    if (put.succeeded()) {
                        webClient.patch(port, Constants.UNSPECIFIED_HOST, patchUri).send(sendPatch -> {
                            if (sendPatch.succeeded()) {
                                final HttpResponse<Buffer> patchResponse = sendPatch.result();
                                final int statusCode = patchResponse.statusCode();
                                final String message = patchResponse.statusMessage();

                                if (statusCode == HTTP.OK) {
                                    asyncTask.complete();
                                } else {
                                    aContext.fail(LOGGER.getMessage(MessageCodes.BUCKETEER_022, statusCode, message));
                                }
                            } else {
                                aContext.fail(sendPatch.cause());
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

    /**
     * Set the log level of the supplied logger. This is really something that should bubble back up into the logging
     * facade library.
     *
     * @param aLogClass A logger for which to set the level
     * @param aLogLevel A new log level
     * @return The logger's previous log level
     */
    private Level setLogLevel(final Class<?> aLogClass, final Level aLogLevel) {
        final ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(aLogClass, Constants.MESSAGES).getLoggerImpl();
        final Level level = logger.getEffectiveLevel();

        logger.setLevel(aLogLevel);

        return level;
    }
}
