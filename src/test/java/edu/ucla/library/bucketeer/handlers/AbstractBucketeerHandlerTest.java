
package edu.ucla.library.bucketeer.handlers;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;

import info.freelibrary.util.FileUtils;
import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.bucketeer.Config;
import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.MessageCodes;
import edu.ucla.library.bucketeer.verticles.MainVerticle;
import edu.ucla.library.bucketeer.utils.TestUtils;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;

/**
 * Tests related to the shared methods in the abstract Bucketeer handler.
 */
public abstract class AbstractBucketeerHandlerTest {

    protected static final String JOB_FILE_NAME = "live-test.csv";

    protected static final String JOB_NAME = FileUtils.stripExt(JOB_FILE_NAME);

    private static final Logger LOGGER =
            LoggerFactory.getLogger(AbstractBucketeerHandlerTest.class, Constants.MESSAGES);

    @Rule
    public RunTestOnContext myTestContext = new RunTestOnContext();

    protected Vertx myVertx;

    /**
     * Test set up.
     *
     * @param aContext A testing context
     * @throws Exception When a problem occurs while trying to set up the test.
     */
    @Before
    public void setUp(final TestContext aContext) throws Exception {
        myVertx = myTestContext.vertx();

        final DeploymentOptions options = new DeploymentOptions();
        final Async asyncTask = aContext.async();
        final int port = getOpenPort();

        // Set test port
        aContext.put(Config.HTTP_PORT, port);
        options.setConfig(new JsonObject().put(Config.HTTP_PORT, port));

        myVertx.deployVerticle(MainVerticle.class, options, deployment -> {
            if (deployment.succeeded()) {
                try (Socket socket = new Socket(Constants.UNSPECIFIED_HOST, port)) {
                    // This is expected... do nothing
                } catch (final IOException details) {
                    // Not necessarily an error, but a higher probability of one
                    LOGGER.warn(MessageCodes.BUCKETEER_506, port);
                }

                TestUtils.complete(asyncTask);
            } else {
                aContext.fail(deployment.cause());
            }
        });
    }

    /**
     * Clean up after the test has been run.
     *
     * @param aContext A testing context
     * @throws Exception When a problem occurs while cleaning up the test
     */
    @After
    public void tearDown(final TestContext aContext) throws Exception {
        final Async asyncTask = aContext.async();

        myVertx.close(close -> {
            if (close.succeeded()) {
                LOGGER.debug("Verticle shutdown.");
                TestUtils.complete(asyncTask);
            } else {
                aContext.fail(close.cause());
            }
        });
    }

    /**
     * Gets a port that's available for our test instance to use.
     *
     * @return An open port number
     * @throws IOException If there is trouble securing an open port
     */
    private int getOpenPort() throws IOException {
        final ServerSocket socket = new ServerSocket(0);
        final int port = socket.getLocalPort();

        socket.close();
        return port;
    }
}
