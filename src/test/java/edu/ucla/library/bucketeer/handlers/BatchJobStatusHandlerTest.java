
package edu.ucla.library.bucketeer.handlers;

import static edu.ucla.library.bucketeer.Constants.EMPTY;

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
import edu.ucla.library.bucketeer.utils.TestUtils;

import ch.qos.logback.classic.Level;
import io.vertx.core.buffer.Buffer;
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
                    TestUtils.complete(asyncTask);
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
    public final void testInternalServerErrorResponse(final TestContext aContext) {
        final String uri = StringUtils.format(PATCH_BATCH_URI, JOB_NAME, TEST_ARK, TRUE);
        final Level level = setLogLevel(BatchJobStatusHandler.class, Level.OFF);
        final WebClient client = WebClient.create(myVertx);
        final Async asyncTask = aContext.async();

        LOGGER.debug(MessageCodes.BUCKETEER_017, myTestName.getMethodName());

        client.patch(aContext.get(Config.HTTP_PORT), Constants.UNSPECIFIED_HOST, uri).send(send -> {
            if (send.succeeded()) {
                aContext.assertEquals(HTTP.INTERNAL_SERVER_ERROR, send.result().statusCode());
                setLogLevel(BatchJobStatusHandler.class, level);
                TestUtils.complete(asyncTask);
            } else {
                aContext.fail(send.cause());
            }
        });
    }

    /**
     * Tests whether a request with a missing item ID is handled correctly.
     *
     * @param aContext A testing context
     */
    @Test
    public final void testBadRequestMissingID(final TestContext aContext) {
        final String uri = StringUtils.format(PATCH_BATCH_URI, JOB_NAME, EMPTY, TRUE);
        final Level level = setLogLevel(BatchJobStatusHandler.class, Level.OFF);
        final WebClient client = WebClient.create(myVertx);
        final Async asyncTask = aContext.async();

        LOGGER.debug(MessageCodes.BUCKETEER_017, myTestName.getMethodName());

        client.patch(aContext.get(Config.HTTP_PORT), Constants.UNSPECIFIED_HOST, uri).send(send -> {
            if (send.succeeded()) {
                final HttpResponse<Buffer> response = send.result();

                aContext.assertEquals(HTTP.BAD_REQUEST, response.statusCode());
                aContext.assertEquals(LOGGER.getMessage(MessageCodes.BUCKETEER_600), response.statusMessage());

                setLogLevel(BatchJobStatusHandler.class, level);
                TestUtils.complete(asyncTask);
            } else {
                aContext.fail(send.cause());
            }
        });
    }

    /**
     * Tests whether a request with a missing job name is handled correctly.
     *
     * @param aContext A testing context
     */
    @Test
    public final void testBadRequestMissingJobName(final TestContext aContext) {
        final String uri = StringUtils.format(PATCH_BATCH_URI, EMPTY, TEST_ARK, TRUE);
        final Level level = setLogLevel(BatchJobStatusHandler.class, Level.OFF);
        final WebClient client = WebClient.create(myVertx);
        final Async asyncTask = aContext.async();

        LOGGER.debug(MessageCodes.BUCKETEER_017, myTestName.getMethodName());

        client.patch(aContext.get(Config.HTTP_PORT), Constants.UNSPECIFIED_HOST, uri).send(send -> {
            if (send.succeeded()) {
                final HttpResponse<Buffer> response = send.result();

                aContext.assertEquals(HTTP.BAD_REQUEST, response.statusCode());
                aContext.assertEquals(LOGGER.getMessage(MessageCodes.BUCKETEER_601), response.statusMessage());

                setLogLevel(BatchJobStatusHandler.class, level);
                TestUtils.complete(asyncTask);
            } else {
                aContext.fail(send.cause());
            }
        });
    }

    /**
     * Tests whether a request with a missing status update is handled correctly.
     *
     * @param aContext A testing context
     */
    @Test
    public final void testBadRequestMissingStatusUpdateWithSlash(final TestContext aContext) {
        final String uri = StringUtils.format(PATCH_BATCH_URI, JOB_NAME, TEST_ARK, EMPTY);
        final Level level = setLogLevel(BatchJobStatusHandler.class, Level.OFF);
        final WebClient client = WebClient.create(myVertx);
        final Async asyncTask = aContext.async();

        LOGGER.debug(MessageCodes.BUCKETEER_017, myTestName.getMethodName());

        client.patch(aContext.get(Config.HTTP_PORT), Constants.UNSPECIFIED_HOST, uri).send(send -> {
            if (send.succeeded()) {
                final HttpResponse<Buffer> response = send.result();

                aContext.assertEquals(HTTP.BAD_REQUEST, response.statusCode());
                aContext.assertEquals("Bad Request", response.statusMessage());

                setLogLevel(BatchJobStatusHandler.class, level);
                TestUtils.complete(asyncTask);
            } else {
                aContext.fail(send.cause());
            }
        });
    }

    /**
     * Tests whether a request with a missing status update is handled correctly.
     *
     * @param aContext A testing context
     */
    @Test
    public final void testBadRequestMissingStatusUpdateWithoutSlash(final TestContext aContext) {
        final String uri = StringUtils.format(PATCH_BATCH_URI, JOB_NAME, TEST_ARK, EMPTY);
        final String slashlessURI = uri.substring(0, uri.length() - 1);
        final Level level = setLogLevel(BatchJobStatusHandler.class, Level.OFF);
        final WebClient client = WebClient.create(myVertx);
        final Async asyncTask = aContext.async();

        LOGGER.debug(MessageCodes.BUCKETEER_017, myTestName.getMethodName());

        client.patch(aContext.get(Config.HTTP_PORT), Constants.UNSPECIFIED_HOST, slashlessURI).send(send -> {
            if (send.succeeded()) {
                final HttpResponse<Buffer> response = send.result();

                aContext.assertEquals(HTTP.BAD_REQUEST, response.statusCode());
                aContext.assertEquals(LOGGER.getMessage(MessageCodes.BUCKETEER_602), response.statusMessage());

                setLogLevel(BatchJobStatusHandler.class, level);
                TestUtils.complete(asyncTask);
            } else {
                aContext.fail(send.cause());
            }
        });
    }

    /**
     * Tests whether a request with a bogus PATCH API endpoint is handled correctly.
     *
     * @param aContext A testing context
     */
    @Test
    public final void testBadRequestToBogusEndpoint(final TestContext aContext) {
        final String uri = StringUtils.format("/this/does/not/exist");
        final Level level = setLogLevel(BatchJobStatusHandler.class, Level.OFF);
        final WebClient client = WebClient.create(myVertx);
        final Async asyncTask = aContext.async();

        LOGGER.debug(MessageCodes.BUCKETEER_017, myTestName.getMethodName());

        client.patch(aContext.get(Config.HTTP_PORT), Constants.UNSPECIFIED_HOST, uri).send(send -> {
            if (send.succeeded()) {
                final HttpResponse<Buffer> response = send.result();

                aContext.assertEquals(HTTP.BAD_REQUEST, response.statusCode());
                aContext.assertEquals(LOGGER.getMessage(MessageCodes.BUCKETEER_162, uri), response.statusMessage());

                setLogLevel(BatchJobStatusHandler.class, level);
                TestUtils.complete(asyncTask);
            } else {
                aContext.fail(send.cause());
            }
        });
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
                                    TestUtils.complete(asyncTask);
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
