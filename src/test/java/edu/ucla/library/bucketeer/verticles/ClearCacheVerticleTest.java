
package edu.ucla.library.bucketeer.verticles;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.Rule;
import org.junit.runner.RunWith;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;
import info.freelibrary.util.StringUtils;

import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.utils.TestUtils;

import io.vertx.config.ConfigRetriever;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.DeploymentOptions;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.unit.junit.RunTestOnContext;

/**
 * Testing the mechanism used to clear Cantaloupe's cache.
 */
@RunWith(VertxUnitRunner.class)
public class ClearCacheVerticleTest {

    static {
        // For running this test independently, we need to configure the logging config source
        System.setProperty("logback.configurationFile", "src/test/resources/logback-test.xml");
    }

    /**
     * Logger for the <code>ClearCacheVerticleTest</code>.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(ClearCacheVerticleTest.class, Constants.MESSAGES);

    /**
    * A test context from which references to Vert.x can be retrieved.
    */
    @Rule
    public RunTestOnContext myRunTestOnContextRule = new RunTestOnContext();

    /**
     * Cantaloupe username for testing Cantaloupe cache clearing.
     */
    private String myUsername;

    /**
     * Cantaloupe password for testing Cantaloupe cache clearing.
     */
    private String myPassword;

    /**
     * Verticle ID for undeploying ClearCacheVerticle
     */
    private String myVertID;

    /**
     * Sets up the tests.
     *
     * @param aContext A testing environment
     */
    @Before
    public final void setup(final TestContext aContext) {
        final ConfigRetriever configRetriever = ConfigRetriever.create(Vertx.vertx());
        final Async asyncTask = aContext.async();

        configRetriever.getConfig(configuration -> {
            if (configuration.failed()) {
                aContext.fail(configuration.cause());
            } else {
                final JsonObject config = configuration.result();

                final DeploymentOptions options = new DeploymentOptions().setConfig(config);
                //use this in actual test to deploy
                myRunTestOnContextRule.vertx()
                    .deployVerticle(ClearCacheVerticle.class.getName(), options, deployment -> {
                        if (deployment.succeeded()) {
                            myVertID = deployment.result();
                            LOGGER.info(myVertID);
                            TestUtils.complete(asyncTask);
                        } else {
                            LOGGER.error(deployment.cause(), "Deployment failure");
                            aContext.fail(deployment.cause());
                        }

                    });
            }
        });


    }


    /**
     * Tear down the testing environment.
     *
     * @param aContext A test context
     */
    @After
    public void tearDown(final TestContext aContext) {

        if (myVertID != null) {
            final Async async = aContext.async();
            LOGGER.info(myVertID);
            LOGGER.info("Entered teardown");
            myRunTestOnContextRule.vertx().undeploy(myVertID, undeployment -> {
                if (undeployment.failed()) {
                    aContext.fail(undeployment.cause());
                } else {
                    async.complete();
                }
            });
        } else {
            LOGGER.info("Verticle id was null");
        }



       //in tear down get verticle id and shut down the verticle by referencing the ID
    }

    /**
     * Tests clearing Cantaloupe's cache.
     *
     * @param aContext A testing context
     */
    @Test
    public void testCacheClear(final TestContext aContext) {

        final WebClient client = WebClient.create(Vertx.vertx());

        // These are just the property names, not values
        LOGGER.info("Connecting with Cantaloupe user: {}", StringUtils.trimToNull(myUsername));

        //Sends multiple messges over eventBus to see if correct response is recieved each time
        for (int i = 0; i < 500; i++) {
            final Async asyncTask = aContext.async();
            myRunTestOnContextRule.vertx()
                .eventBus().<JsonObject>send(ClearCacheVerticle.class.getName(), new JsonObject()
                .put("imageID", "ARK-12345678"), response -> {
                    if (response.failed()) {
                        aContext.fail(response.cause());
                    } else {
                        TestUtils.complete(asyncTask);
                    }
                });
            i++;
        }
    }
}
