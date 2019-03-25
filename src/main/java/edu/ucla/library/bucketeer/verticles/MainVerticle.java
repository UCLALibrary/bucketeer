
package edu.ucla.library.bucketeer.verticles;

import static edu.ucla.library.bucketeer.Constants.MESSAGES;

import java.util.ArrayList;
import java.util.List;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.bucketeer.Config;
import edu.ucla.library.bucketeer.MessageCodes;
import edu.ucla.library.bucketeer.Op;
import edu.ucla.library.bucketeer.converters.ConverterFactory;
import edu.ucla.library.bucketeer.handlers.GetPingHandler;
import io.vertx.config.ConfigRetriever;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.api.contract.openapi3.OpenAPI3RouterFactory;

/**
 * Main verticle that starts the application.
 */
public class MainVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(MainVerticle.class, MESSAGES);

    private static final String DEFAULT_SPEC = "bucketeer.yaml";

    private static final int DEFAULT_PORT = 8888;

    /**
     * Starts a Web server.
     */
    @Override
    @SuppressWarnings("rawtypes")
    public void start(final Future<Void> aFuture) {
        final ConfigRetriever configRetriever = ConfigRetriever.create(vertx);
        final HttpServer server = vertx.createHttpServer();

        // Cheat for now and assume we have Kakadu installed
        ConverterFactory.hasSystemKakadu(true);

        // We pull our application's configuration before configuring the server
        configRetriever.getConfig(configuration -> {
            if (configuration.failed()) {
                aFuture.fail(configuration.cause());
            } else {
                final JsonObject config = configuration.result();
                final String apiSpec = config.getString(Config.OPENAPI_SPEC_PATH, DEFAULT_SPEC);

                // We can use our OpenAPI specification file to configure our app's router
                OpenAPI3RouterFactory.create(vertx, apiSpec, creation -> {
                    if (creation.succeeded()) {
                        final OpenAPI3RouterFactory routerFactory = creation.result();
                        final int port = config().getInteger(Config.HTTP_PORT, DEFAULT_PORT);

                        // Next, we associate handlers with routes from our specification
                        routerFactory.addHandlerByOperationId(Op.GET_PING, new GetPingHandler());
                        server.requestHandler(routerFactory.getRouter()).listen(port);

                        // Deploy our verticles that convert and upload images to S3
                        deployVerticles(config, deployment -> {
                            if (deployment.succeeded()) {
                                aFuture.complete();
                            } else {
                                aFuture.fail(deployment.cause());
                            }
                        });
                    } else {
                        aFuture.fail(creation.cause());
                    }
                });
            }
        });
    }

    /**
     * Returns the other non-main verticles.
     *
     * @param aHandler A handler that wraps the results of our verticle deployments
     */
    @SuppressWarnings("rawtypes")
    private void deployVerticles(final JsonObject aConfig, final Handler<AsyncResult<Void>> aHandler) {
        final DeploymentOptions workerOpts = new DeploymentOptions().setWorker(true);
        final DeploymentOptions verticleOpts = new DeploymentOptions();
        final List<Future> futures = new ArrayList<>();
        final Future<Void> future = Future.future();

        // Set configuration values for our verticles to use
        verticleOpts.setConfig(aConfig);
        workerOpts.setConfig(aConfig);

        // Set the deployVerticles handler to handle our verticles deploy future
        future.setHandler(aHandler);

        // Kakadu uses all the available threads so let's let it do its work
        workerOpts.setInstances(1);
        workerOpts.setWorkerPoolSize(1);
        workerOpts.setWorkerPoolName(ImageWorkerVerticle.class.getSimpleName() + "-thread");

        futures.add(deployVerticle(S3BucketVerticle.class.getName(), verticleOpts, Future.future()));
        futures.add(deployVerticle(ImageWorkerVerticle.class.getName(), workerOpts, Future.future()));

        // Confirm all our verticles were successfully deployed
        CompositeFuture.all(futures).setHandler(handler -> {
            if (handler.succeeded()) {
                future.complete();
            } else {
                future.fail(handler.cause());
            }
        });
    }

    /**
     * Deploys a particular verticle.
     *
     * @param aVerticleName The name of the verticle to deploy
     * @param aOptions Any deployment options that should be considered
     */
    private Future<Void> deployVerticle(final String aVerticleName, final DeploymentOptions aOptions,
            final Future<Void> aFuture) {
        vertx.deployVerticle(aVerticleName, aOptions, response -> {
            try {
                final String verticleName = Class.forName(aVerticleName).getSimpleName();

                if (response.succeeded()) {
                    LOGGER.debug(MessageCodes.BUCKETEER_007, verticleName, response.result());
                    aFuture.complete();
                } else {
                    LOGGER.error(MessageCodes.BUCKETEER_008, verticleName, response.cause());
                    aFuture.fail(response.cause());
                }
            } catch (final ClassNotFoundException details) {
                aFuture.fail(details);
            }
        });

        return aFuture;
    }
}
