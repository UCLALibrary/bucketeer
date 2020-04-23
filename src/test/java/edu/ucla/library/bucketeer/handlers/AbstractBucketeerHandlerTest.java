
package edu.ucla.library.bucketeer.handlers;

import java.net.ServerSocket;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;

import info.freelibrary.util.FileUtils;
import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.bucketeer.Config;
import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.MessageCodes;
import edu.ucla.library.bucketeer.verticles.MainVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;

public abstract class AbstractBucketeerHandlerTest {

    protected static final String JOB_FILE_NAME = "live-test.csv";

    protected static final String JOB_NAME = FileUtils.stripExt(JOB_FILE_NAME);

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractBucketeerHandlerTest.class,
            Constants.MESSAGES);

    @Rule
    public RunTestOnContext myTestContext = new RunTestOnContext();

    @Rule
    public TestName myTestName = new TestName();

    protected Vertx myVertx;

    /**
     * Test set up.
     *
     * @param aContext A testing context
     * @throws Exception When a problem occurs while trying to set up the test.
     */
    @Before
    public void setUp(final TestContext aContext) throws Exception {
        final DeploymentOptions options = new DeploymentOptions();
        final ServerSocket socket = new ServerSocket(0);
        final int port = socket.getLocalPort();

        socket.close();

        // Set test port
        aContext.put(Config.HTTP_PORT, port);
        options.setConfig(new JsonObject().put(Config.HTTP_PORT, port));

        LOGGER.debug(MessageCodes.BUCKETEER_021, myTestName.getMethodName(), port);

        myVertx = myTestContext.vertx();
        myVertx.deployVerticle(MainVerticle.class, options, aContext.asyncAssertSuccess());
    }

    /**
     * Clean up after the test has been run.
     *
     * @param aContext A testing context
     * @throws Exception When a problem occurs while cleaning up the test
     */
    @After
    public void tearDown(final TestContext aContext) throws Exception {
        myVertx.close(aContext.asyncAssertSuccess());
    }

    /**
     * Completes an asynchronous task if it isn't already.
     *
     * @param aAsyncTask An asynchronous task
     */
    protected void complete(final Async aAsyncTask) {
        if (!aAsyncTask.isCompleted()) {
            aAsyncTask.complete();
        }
    }
}
