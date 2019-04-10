
package edu.ucla.library.bucketeer.verticles;

import static edu.ucla.library.bucketeer.Constants.MESSAGES;
import static org.junit.Assume.assumeTrue;

import java.util.UUID;

import org.junit.After;
import org.junit.AssumptionViolatedException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.bucketeer.Config;
import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.MessageCodes;
import edu.ucla.library.bucketeer.Op;
import io.vertx.config.ConfigRetriever;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class S3BucketVerticleTest extends AbstractBucketeerVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(S3BucketVerticleTest.class, MESSAGES);

    private static final String JP2_PATH = "src/test/resources/images/test.jp2";

    private static final String VERTICLE_NAME = S3BucketVerticle.class.getName();

    private static final String DEFAULT_ACCESS_KEY = "YOUR_ACCESS_KEY";

    private static final String DEFAULT_S3_BUCKET = "cantaloupe-jp2k";

    @Rule
    public RunTestOnContext myRunTestOnContextRule = new RunTestOnContext();

    private String myImageKey;

    private String myS3Bucket;

    /** We can't, as of yet, execute these tests without a non-default S3 configuration */
    private boolean isExecutable;

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

                myImageKey = UUID.randomUUID().toString() + ".jp2";
                myS3Bucket = jsonConfig.getString(Config.S3_BUCKET, DEFAULT_S3_BUCKET);

                // We need to determine if we'll be able to run the S3 integration tests so we can skip if needed
                if (jsonConfig.containsKey(Config.S3_ACCESS_KEY) && !jsonConfig.getString(Config.S3_ACCESS_KEY,
                        DEFAULT_ACCESS_KEY).equalsIgnoreCase(DEFAULT_ACCESS_KEY)) {
                    isExecutable = true;
                }

                vertx.deployVerticle(VERTICLE_NAME, options.setConfig(jsonConfig), deployment -> {
                    if (!deployment.succeeded()) {
                        final Throwable details = deployment.cause();

                        LOGGER.error(details, details.getMessage());
                        aContext.fail(details.getMessage());
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
     * Tests being able to store to S3. This requires an actual S3 configuration. The test will be skipped if no such
     * configuration exists.
     *
     * @param aContext A test context
     */
    @Test
    public final void testS3Storage(final TestContext aContext) {
        try {
            // Skip this test if we don't have a valid S3 configuration
            assumeTrue(LOGGER.getMessage(MessageCodes.BUCKETEER_012), isExecutable);
        } catch (final AssumptionViolatedException details) {
            LOGGER.warn(details.getMessage());
            throw details;
        }

        final Vertx vertx = myRunTestOnContextRule.vertx();
        final JsonObject message = new JsonObject();
        final Async asyncTask = aContext.async();

        message.put(Constants.IMAGE_ID, myImageKey);
        message.put(Constants.FILE_PATH, JP2_PATH);

        vertx.eventBus().send(VERTICLE_NAME, message, send -> {
            if (send.succeeded()) {
                final String response = send.result().body().toString();

                if (!response.equals(Op.SUCCESS)) {
                    aContext.fail(LOGGER.getMessage(MessageCodes.BUCKETEER_011));
                }
            } else {
                final Throwable details = send.cause();

                LOGGER.error(details, details.getMessage());
                aContext.fail(details.getMessage());
            }

            asyncTask.complete();
        });
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }

}
