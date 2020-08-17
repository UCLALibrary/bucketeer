
package edu.ucla.library.bucketeer.verticles;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;
import info.freelibrary.util.StringUtils;

import edu.ucla.library.bucketeer.AbstractBucketeerTest;
import edu.ucla.library.bucketeer.Config;
import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.MessageCodes;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

@RunWith(VertxUnitRunner.class)
public class FesterVerticleTest extends AbstractBucketeerTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(FesterVerticleTest.class, Constants.MESSAGES);

    private static final String FESTER_URL = "http://localhost:{}/collections";

    private static final String TEST_JOB = "src/test/resources/json/job.json";

    @Rule
    public RunTestOnContext myTestContext = new RunTestOnContext();

    private Vertx myVertx;

    private int myPort;

    private HttpServer myServer;

    private JsonObject myConfig;

    @Before
    public void setUp(final TestContext aContext) throws Exception {
        myPort = getFreePort();
        myVertx = myTestContext.vertx();
        myConfig = new JsonObject().put(Config.FESTER_URL, StringUtils.format(FESTER_URL, myPort));
    }

    @After
    public void tearDown(final TestContext aContext) throws Exception {
        if (myServer != null) {
            LOGGER.debug(LOGGER.getMessage(MessageCodes.BUCKETEER_160));
            myServer.close();
        }
    }

    @Test
    public final void testPost(final TestContext aContext) throws IOException {
        final Async asyncTask = aContext.async();
        final Promise<Void> promise = Promise.promise();

        // The handler that runs our test-specific checks
        final Handler<RoutingContext> handler = routingContext -> {
            final Set<FileUpload> uploads = routingContext.fileUploads();
            final FileUpload upload;

            aContext.assertEquals(1, uploads.size());
            upload = uploads.iterator().next();
            aContext.assertEquals("test-job", upload.fileName());

            if (!asyncTask.isCompleted()) {
                asyncTask.complete();
            }
        };

        // Configure our promise to send the Fester message when server has started
        configurePromise(promise, aContext);

        // Start the test server this test uses
        startTestServer(handler, promise);
    }

    /**
     * This sends a test message to our FesterVerticle if the server has been started successfully.
     *
     * @param aPromise A promise that sends the test message
     * @param aContext A testing context
     */
    private void configurePromise(final Promise<Void> aPromise, final TestContext aContext) {
        aPromise.future().onComplete(future -> {
            try {
                final JsonObject job = new JsonObject(StringUtils.read(new File(TEST_JOB)));
                final JsonObject message = new JsonObject().put(Constants.BATCH_METADATA, job);

                if (future.succeeded()) {
                    myVertx.eventBus().send(FesterVerticle.class.getName(), message);
                } else {
                    aContext.fail(future.cause());
                }
            } catch (final IOException details) {
                aContext.fail(details);
            }
        });
    }

    /**
     * Configure a test server for running our test against.
     *
     * @param aHandler An HttpServerRequest handler
     * @param aPromise A promise to complete after the server has started
     */
    private void startTestServer(final Handler<RoutingContext> aHandler, final Promise<Void> aPromise) {
        final Router router = Router.router(myVertx);

        // We have to have a body handler or our router handling will not work
        router.route().handler(BodyHandler.create());

        // Create a route that handles POSTs from our FesterVerticle
        router.post("/collections").handler(aHandler);

        // Start the test server that's now configured with the test handler
        myServer = myVertx.createHttpServer().requestHandler(router).listen(myPort, listen -> {
            if (listen.succeeded()) {
                // Configure our FesterVerticle to POST to our test server's port
                final DeploymentOptions options = new DeploymentOptions().setConfig(myConfig);

                // Deploy the verticle we want to test
                myVertx.deployVerticle(FesterVerticle.class.getName(), options, deployment -> {
                    if (deployment.succeeded()) {
                        aPromise.complete();
                    } else {
                        aPromise.fail(deployment.cause());
                    }
                });
            } else {
                aPromise.fail(listen.cause());
            }
        });
    }

    /**
     * Gets a port that's available on our machine.
     *
     * @return An open port number
     * @throws IOException If there is trouble getting an open port
     */
    private int getFreePort() throws IOException {
        final ServerSocket socket = new ServerSocket(0);
        final int port = socket.getLocalPort();

        socket.close();

        return port;
    }
}
