
package edu.ucla.library.bucketeer.verticles;

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
import edu.ucla.library.bucketeer.Op;
import io.vertx.config.ConfigRetriever;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

/**
 * A test to test thumbnail caching (including cache invalidation).
 */
@RunWith(VertxUnitRunner.class)
public class ThumbnailVerticleTest extends AbstractBucketeerVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(ThumbnailVerticleTest.class, Constants.MESSAGES);

    private static final String VERTICLE_NAME = ThumbnailVerticle.class.getName();

    private static final String IMAGE_ID = "healthcheckimage";

    /**
     * The testing context.
     */
    @Rule
    public RunTestOnContext myRunTestOnContextRule = new RunTestOnContext();

    /**
     * Set up the testing environment.
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

        configRetriever.getConfig(getConfig -> {
            if (getConfig.succeeded()) {
                final JsonObject config = getConfig.result();

                // Test if we're running as a single test instead of in the full test suite
                if (!config.containsKey(Config.IIIF_URL)) {
                    config.mergeIn(getSingleTestConfig());
                }

                vertx.deployVerticle(VERTICLE_NAME, options.setConfig(config), deployment -> {
                    if (deployment.failed()) {
                        final Throwable details = deployment.cause();
                        final String message = details.getMessage();

                        LOGGER.error(details, message);

                        aContext.fail(message);
                    }

                    asyncTask.complete();
                });
            } else {
                aContext.fail(getConfig.cause());
                asyncTask.complete();
            }
        });
    }

    /**
     * Tear down the testing environment.
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
     * Tests thumbnail caching.
     *
     * @param aContext A testing context
     * @throws Exception If there is trouble successfully completing the test
     */
    @Test
    public void testThumbnailCaching(final TestContext aContext) throws Exception {
        final Vertx vertx = myRunTestOnContextRule.vertx();
        final JsonObject message = new JsonObject();
        final Async asyncTask = aContext.async();

        message.put(Constants.IMAGE_ID_ARRAY, new JsonArray().add(IMAGE_ID));

        vertx.eventBus().send(VERTICLE_NAME, message, send -> {
            if (send.failed()) {
                final Throwable details = send.cause();

                if (details != null) {
                    LOGGER.error(details, details.getMessage());
                }

                aContext.fail();
            } else {
                if (Op.FAILURE.equals(send.result().body())) {
                    aContext.fail();
                }
            }

            asyncTask.complete();
        });
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }

    /**
     * Set config values manually when we're not running the test as a part of the Maven build.
     *
     * @return The expected configuration values (needed to run the test) wrapped in a JSON object
     */
    private JsonObject getSingleTestConfig() {
        final JsonObject config = new JsonObject();

        // If you're running as a single test in your IDE, set system properties with these values
        config.put(Config.IIIF_URL, System.getProperty(Config.IIIF_URL));
        config.put(Config.CDN_DISTRO_ID, System.getProperty(Config.CDN_DISTRO_ID));
        config.put(Config.THUMBNAIL_SIZE, System.getProperty(Config.THUMBNAIL_SIZE));
        config.put(Config.S3_ACCESS_KEY, System.getProperty(Config.S3_ACCESS_KEY));
        config.put(Config.S3_SECRET_KEY, System.getProperty(Config.S3_SECRET_KEY));

        return config;
    }

}
