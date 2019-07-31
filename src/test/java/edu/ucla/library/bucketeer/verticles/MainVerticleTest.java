
package edu.ucla.library.bucketeer.verticles;

import java.io.IOException;
import java.net.ServerSocket;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.bucketeer.Config;
import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.MessageCodes;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class MainVerticleTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(MainVerticleTest.class, Constants.MESSAGES);

    private Vertx myVertx;

    /**
     * Test set up.
     *
     * @param aContext A testing context
     */
    @Before
    public void setUp(final TestContext aContext) throws IOException {
        final DeploymentOptions options = new DeploymentOptions();
        final ServerSocket socket = new ServerSocket(0);
        final int port = socket.getLocalPort();
        final Async asyncTask = aContext.async();

        aContext.put(Config.HTTP_PORT, port);
        options.setConfig(new JsonObject().put(Config.HTTP_PORT, port));
        socket.close();

        // Initialize the Vert.x environment and start our main verticle
        myVertx = Vertx.vertx();

        // Confirm our verticle has loaded before we attempt to test it
        myVertx.deployVerticle(MainVerticle.class.getName(), options, deployment -> {
            if (deployment.succeeded()) {
                asyncTask.complete();
            } else {
                aContext.fail();
            }
        });
    }

    /**
     * Test tear down.
     *
     * @param aContext A testing context
     */
    @After
    public void tearDown(final TestContext aContext) {
        myVertx.close(aContext.asyncAssertSuccess());
    }

    /**
     * Our 'hello world' test.
     *
     * @param aContext A testing context
     */
    @Test
    @SuppressWarnings("deprecation")
    public void testThatTheServerIsStarted(final TestContext aContext) {
        final Async async = aContext.async();
        final int port = aContext.get(Config.HTTP_PORT);

        // Testing the path defined in our OpenAPI YAML file
        myVertx.createHttpClient().getNow(port, Constants.UNSPECIFIED_HOST, "/status", response -> {
            final int statusCode = response.statusCode();

            if (statusCode == 200) {
                response.bodyHandler(body -> {
                    aContext.assertEquals("Hello", body.getString(0, body.length()));
                    async.complete();
                });
            } else {
                aContext.fail(LOGGER.getMessage(MessageCodes.BUCKETEER_069, statusCode));
                async.complete();
            }
        });
    }

}
