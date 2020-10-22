
package edu.ucla.library.bucketeer.verticles;

import static edu.ucla.library.bucketeer.Constants.MESSAGES;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.bucketeer.Config;
import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.Job;
import edu.ucla.library.bucketeer.JobFactory;
import edu.ucla.library.bucketeer.MessageCodes;
import edu.ucla.library.bucketeer.ProcessingException;

import io.vertx.config.ConfigRetriever;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

/**
 * Tests of the Slack message verticle. This verticle sends Slack messages after batch jobs have been completed.
 */
@RunWith(VertxUnitRunner.class)
public class SlackMessageVerticleTest extends AbstractBucketeerVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(SlackMessageVerticleTest.class, MESSAGES);

    private static final String VERTICLE_NAME = SlackMessageVerticle.class.getName();

    private static final File LIVE_TEST_CSV = new File("src/test/resources/csv/live-test.csv");

    private static final String TEST_JOB = "test-job";

    @Rule
    public RunTestOnContext myRunTestOnContextRule = new RunTestOnContext();

    private String mySlackUserHandle;

    private String mySlackErrorChannelID;

    private String mySlackChannelID;

    private Vertx myVertx;

    /**
     * The tests' set up.
     *
     * @param aContext A test context
     * @throws Exception If there is trouble starting Vert.x or configuring the tests
     */
    @Before
    public void setUp(final TestContext aContext) throws Exception {
        final DeploymentOptions options = new DeploymentOptions();
        final Async asyncTask = aContext.async();

        myVertx = myRunTestOnContextRule.vertx();

        ConfigRetriever.create(myVertx).getConfig(config -> {
            if (config.succeeded()) {
                final JsonObject jsonConfig = config.result();

                mySlackUserHandle =
                        jsonConfig.getString(Config.SLACK_TEST_USER_HANDLE).replace(Constants.AT, Constants.EMPTY);
                mySlackChannelID = jsonConfig.getString(Config.SLACK_CHANNEL_ID);
                mySlackErrorChannelID = jsonConfig.getString(Config.SLACK_ERROR_CHANNEL_ID);

                myVertx.deployVerticle(VERTICLE_NAME, options.setConfig(jsonConfig), deployment -> {
                    if (deployment.failed()) {
                        final Throwable details = deployment.cause();
                        final String message = details.getMessage();

                        LOGGER.error(details, message);
                        aContext.fail(message);
                    } else {
                        LOGGER.debug(MessageCodes.BUCKETEER_143, getClass().getName());
                        asyncTask.complete();
                    }
                });
            } else {
                aContext.fail(config.cause());
            }
        });
    }

    /**
     * The tests' tear down.
     *
     * @param aContext A test context
     * @throws Exception If there is trouble closing down the Vert.x instance
     */
    @After
    public void tearDown(final TestContext aContext) throws Exception {
        final Async async = aContext.async();

        myVertx.close(result -> {
            if (!result.succeeded()) {
                final String message = LOGGER.getMessage(MessageCodes.BUCKETEER_015);

                LOGGER.error(message);
                aContext.fail(message);
            } else {
                async.complete();
            }
        });
    }

    /**
     * Tests being able to send a Slack text message.
     *
     * @param aContext A test context
     */
    @Test
    public final void testSlackSendMessage(final TestContext aContext) {
        final String textMessage = LOGGER.getMessage(MessageCodes.BUCKETEER_116);
        final String errorMessage = LOGGER.getMessage(MessageCodes.BUCKETEER_110, textMessage);
        final JsonObject message = new JsonObject();
        final Async asyncTask = aContext.async();

        message.put(Constants.SLACK_MESSAGE_TEXT, errorMessage);
        message.put(Config.SLACK_CHANNEL_ID, mySlackErrorChannelID);

        myVertx.eventBus().request(VERTICLE_NAME, message, send -> {
            if (send.failed()) {
                final Throwable details = send.cause();

                if (details != null) {
                    LOGGER.error(details, details.getMessage());
                }

                aContext.fail(send.cause());
            } else {
                asyncTask.complete();
            }
        });
    }

    /**
     * Tests being able to upload a metadata file to Slack.
     *
     * @param aContext A test context
     */
    @Test
    public final void testSlackFileUpload(final TestContext aContext)
            throws FileNotFoundException, ProcessingException, IOException {
        final Job job = JobFactory.getInstance().createJob(TEST_JOB, LIVE_TEST_CSV);
        final String iiifURL = "unit.test.not.real.please.disregard.com";
        final String slackMessage =
                LOGGER.getMessage(MessageCodes.BUCKETEER_111, mySlackUserHandle, job.size(), iiifURL);
        final Vertx vertx = myRunTestOnContextRule.vertx();
        final JsonObject message = new JsonObject();
        final Async asyncTask = aContext.async();

        message.put(Constants.SLACK_MESSAGE_TEXT, slackMessage);
        message.put(Config.SLACK_CHANNEL_ID, mySlackChannelID);
        message.put(Constants.JOB_NAME, TEST_JOB);
        message.put(Constants.BATCH_METADATA, job.toJSON());

        vertx.eventBus().request(VERTICLE_NAME, message, send -> {
            if (send.failed()) {
                final Throwable details = send.cause();

                if (details != null) {
                    LOGGER.error(details, details.getMessage());
                }

                aContext.fail();
            } else {
                asyncTask.complete();
            }
        });
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }

}
