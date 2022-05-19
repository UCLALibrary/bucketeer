
package edu.ucla.library.bucketeer.handlers;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;
import info.freelibrary.util.StringUtils;

import edu.ucla.library.bucketeer.Config;
import edu.ucla.library.bucketeer.Constants;

import io.vertx.config.ConfigRetriever;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.WebClient;

/**
 * Testing the mechanism used to clear Cantaloupe's cache.
 */
@RunWith(VertxUnitRunner.class)
public class CacheClearTest {

    static {
        // For running this test independently, we need to configure the logging config source
        System.setProperty("logback.configurationFile", "src/test/resources/logback-test.xml");
    }

    /**
     * Logger for the <code>CacheClearTest</code>.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(CacheClearTest.class, Constants.MESSAGES);

    /**
     * Cantaloupe username for testing Cantaloupe cache clearing.
     */
    private String myUsername;

    /**
     * Cantaloupe password for testing Cantaloupe cache clearing.
     */
    private String myPassword;

    @Before
    public final void setup(final TestContext aContext) {
        final ConfigRetriever configRetriever = ConfigRetriever.create(Vertx.vertx());
        final Async asyncTask = aContext.async();

        configRetriever.getConfig(configuration -> {
            if (configuration.failed()) {
                aContext.fail(configuration.cause());
            } else {
                final JsonObject config = configuration.result();

                myUsername = config.getString(Config.IIIF_CACHE_USER);
                myPassword = config.getString(Config.IIIF_CACHE_PASSWORD);

                // Gives a way to supply these manually at test time in the code; just don't check values into VC!
                if (myUsername == null || myPassword == null) {
                    myUsername = "";
                    myPassword = "";
                }

                asyncTask.complete();
            }
        });
    }

    /**
     * Tests clearing Cantaloupe's cache.
     *
     * @param aContext A testing context
     */
    @Test
    public void testCacheClear(final TestContext aContext) {
        final WebClient client = WebClient.create(Vertx.vertx());
        final Async asyncTask = aContext.async();

        // These are just the property names, not values
        LOGGER.info("Connecting with Cantaloupe user: {}", StringUtils.trimToNull(myUsername));

        client.postAbs("https://test-iiif.library.ucla.edu/tasks").basicAuthentication(myUsername, myPassword)
                .putHeader("content-type", "application/json")
                .sendJsonObject(new JsonObject().put("verb", "PurgeItemFromCache").put("identifier", "blah"), post -> {
                    if (post.succeeded()) {
                        LOGGER.info(Integer.toString(post.result().statusCode()));
                    } else {
                        LOGGER.error(post.cause(), post.cause().getMessage());
                    }

                    asyncTask.complete();
                });
    }
}
