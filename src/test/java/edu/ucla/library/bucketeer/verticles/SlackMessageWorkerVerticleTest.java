
package edu.ucla.library.bucketeer.verticles;

import static edu.ucla.library.bucketeer.Constants.MESSAGES;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.bucketeer.Config;
import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.MessageCodes;
import io.vertx.config.ConfigRetriever;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class SlackMessageWorkerVerticleTest extends AbstractBucketeerVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(SlackMessageWorkerVerticleTest.class, MESSAGES);

    private static final String VERTICLE_NAME = SlackMessageWorkerVerticle.class.getName();

    private static final String SLACK_TEST_USER_HANDLE_DEFAULT = "hpottinger";

    private static final String SLACK_TEST_CHANNEL_ID_DEFAULT = "dev-null";

    private static final String SLACK_ERROR_CHANNEL_ID_DEFAULT = SLACK_TEST_CHANNEL_ID_DEFAULT;

    @Rule
    public RunTestOnContext myRunTestOnContextRule = new RunTestOnContext();

    private String mySlackTestUserHandle;

    private String mySlackTestChannelID;

    private String mySlackErrorChannelID;

    /**
     * The tests' set up.
     *
     * @param aContext A test context
     * @throws Exception If there is trouble starting Vert.x or configuring the tests
     */
    @Before
    public void setUp(final TestContext aContext) throws Exception {
        final Vertx vertx = myRunTestOnContextRule.vertx();
        final ConfigRetriever configRetriever = ConfigRetriever.create(vertx);
        final DeploymentOptions options = new DeploymentOptions();
        final Async asyncTask = aContext.async();

        configRetriever.getConfig(config -> {
            if (config.succeeded()) {
                final JsonObject jsonConfig = config.result();

                // some configs we'll need in a bit
                mySlackTestUserHandle = jsonConfig.getString(Config.SLACK_TEST_USER_HANDLE,
                        SLACK_TEST_USER_HANDLE_DEFAULT);
                mySlackTestChannelID = jsonConfig.getString(Config.SLACK_TEST_CHANNEL_ID); // dev-null
                mySlackErrorChannelID = jsonConfig.getString(Config.SLACK_ERROR_CHANNEL_ID); // dev-null

                vertx.deployVerticle(VERTICLE_NAME, options.setConfig(jsonConfig), deployment -> {
                    if (deployment.failed()) {
                        final Throwable details = deployment.cause();
                        final String message = details.getMessage();

                        LOGGER.error(details, message);
                        aContext.fail(message);
                    }

                    asyncTask.complete();
                });
            } else {
                aContext.fail(config.cause());
                asyncTask.complete();
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

        myRunTestOnContextRule.vertx().close(result -> {
            if (!result.succeeded()) {
                final String message = LOGGER.getMessage(MessageCodes.BUCKETEER_015);

                LOGGER.error(message);
                aContext.fail(message);
            }

            async.complete();
        });
    }

    /**
     * Tests being able to send Slack messages.
     *
     * @param aContext A test context
     */
    @Test
    public final void testSlackSendMessage(final TestContext aContext) {

        final Vertx vertx = myRunTestOnContextRule.vertx();
        final JsonObject message = new JsonObject();
        final Async asyncTask = aContext.async();

        final String slackMessageText = "whirr, click, buzz... " + UUID.randomUUID().toString();

        final String slackChannelId = mySlackTestChannelID;

        // send these 2 vars: slackMessageText, slackChannelId,
        // NOTE: the WebhookURL will be inferred from the slackChannelId
        // NOTE: we do not need to send any tokens, the verticle gets them
        // from the configuration.

        message.put(Constants.SLACK_MESSAGE_TEXT, slackMessageText);
        message.put(Config.SLACK_CHANNEL_ID, slackChannelId);

        vertx.eventBus().send(VERTICLE_NAME, message, send -> {
            if (send.failed()) {
                final Throwable details = send.cause();

                if (details != null) {
                    LOGGER.error(details, details.getMessage());
                }

                aContext.fail();
            }

            asyncTask.complete();
        });
    }

    /**
     * Tests being able to upload a metadata file to Slack.
     *
     * @param aContext A test context
     */
    @Test
    public final void testSlackFileUpload(final TestContext aContext) {

        final Vertx vertx = myRunTestOnContextRule.vertx();
        final JsonObject message = new JsonObject();
        final Async asyncTask = aContext.async();

        final String slackMessageText = "<@" + mySlackTestUserHandle + "> here's a file... " + UUID.randomUUID()
                .toString();

        final String slackChannelId = mySlackTestChannelID;

        // TODO: replace this temporary placeholder with actual data
        final String placeholderString = "placeholder, data, for freshness, do, not, eat";
        final List<String> placeholderList = Arrays.asList(placeholderString.split("\\s*,\\s*"));
        final JsonArray placeholderJsonArray = new JsonArray(placeholderList);

        // send these 3 vars: slackMessageText, slackChannelId, BatchMetadata
        // NOTE: the WebhookURL will be inferred from the slackChannelId
        // NOTE: we do not need to send slackVerificationToken, the verticle gets that
        // from the configuration.

        message.put(Constants.SLACK_MESSAGE_TEXT, slackMessageText);
        message.put(Config.SLACK_CHANNEL_ID, slackChannelId);
        message.put(Constants.BATCH_METADATA, placeholderJsonArray);

        vertx.eventBus().send(VERTICLE_NAME, message, send -> {
            if (send.failed()) {
                final Throwable details = send.cause();

                if (details != null) {
                    LOGGER.error(details, details.getMessage());
                }

                aContext.fail();
            }

            asyncTask.complete();
        });
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }

}
