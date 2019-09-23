
package edu.ucla.library.bucketeer.verticles;

import static edu.ucla.library.bucketeer.Constants.MESSAGES;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.bucketeer.Config;
import edu.ucla.library.bucketeer.MessageCodes;
import edu.ucla.library.bucketeer.Op;
import edu.ucla.library.bucketeer.handlers.BatchJobStatusHandler;
import edu.ucla.library.bucketeer.handlers.DeleteJobHandler;
import edu.ucla.library.bucketeer.handlers.FailureHandler;
import edu.ucla.library.bucketeer.handlers.GetJobStatusesHandler;
import edu.ucla.library.bucketeer.handlers.GetJobsHandler;
import edu.ucla.library.bucketeer.handlers.GetStatusHandler;
import edu.ucla.library.bucketeer.handlers.LoadCsvHandler;
import edu.ucla.library.bucketeer.handlers.LoadImageHandler;
import edu.ucla.library.bucketeer.utils.FilePathPrefixFactory;
import edu.ucla.library.bucketeer.utils.IFilePathPrefix;
import edu.ucla.library.bucketeer.utils.JobFactory;
import io.vertx.config.ConfigRetriever;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.api.contract.openapi3.OpenAPI3RouterFactory;
import io.vertx.ext.web.handler.StaticHandler;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.HardwareAbstractionLayer;

/**
 * Main verticle that starts the application.
 */
public class MainVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(MainVerticle.class, MESSAGES);

    private static final String DEFAULT_SPEC = "bucketeer.yaml";

    private static final int DEFAULT_PORT = 8888;

    private static final String THREAD_NAME = "-thread";

    private static final int DEFAULT_S3_UPLOADER_INSTANCES = 1;

    private static final int DEFAULT_S3_UPLOADER_THREADS = getAvailableProcessors();

    private static final String EMPTY = "";

    /**
     * Gets the available logical processors on the system minus one.
     *
     * @return
     */
    private static int getAvailableProcessors() {
        final SystemInfo systemInfo = new SystemInfo();
        final HardwareAbstractionLayer hardwareAbstractionLayer = systemInfo.getHardware();
        final CentralProcessor centralProcessor = hardwareAbstractionLayer.getProcessor();

        return centralProcessor.getLogicalProcessorCount() - 1;
    }

    /**
     * Starts a Web server.
     */
    @Override
    public void start(final Future<Void> aFuture) {
        final ConfigRetriever configRetriever = ConfigRetriever.create(vertx);

        // We pull our application's configuration before configuring the server
        configRetriever.getConfig(configuration -> {
            if (configuration.failed()) {
                aFuture.fail(configuration.cause());
            } else {
                final JsonObject config = configuration.result();
                final String fsMount = config.getString(Config.FILESYSTEM_MOUNT);
                final String fsPrefix = config.getString(Config.FILESYSTEM_PREFIX);

                if (fsMount != null && fsPrefix != null) {
                    final IFilePathPrefix filePathPrefix = FilePathPrefixFactory.getPrefix(fsPrefix, fsMount);

                    // Initialize our JobFactory with our pre-configured IFilePathPrefix implementation
                    JobFactory.getInstance().setPathPrefix(filePathPrefix);
                } else {
                    LOGGER.warn(MessageCodes.BUCKETEER_128);
                }

                // Build the router that will respond to all traffic
                buildRouter(config, aFuture);
            }
        });
    }

    private void buildRouter(final JsonObject aConfig, final Future<Void> aFuture) {
        final String apiSpec = aConfig.getString(Config.OPENAPI_SPEC_PATH, DEFAULT_SPEC);
        final HttpServer server = vertx.createHttpServer(getHttpServerOptions());

        // We can use our OpenAPI specification file to configure our app's router
        OpenAPI3RouterFactory.create(vertx, apiSpec, creation -> {
            if (creation.succeeded()) {
                final OpenAPI3RouterFactory routerFactory = creation.result();
                final int port = aConfig.getInteger(Config.HTTP_PORT, DEFAULT_PORT);
                final Router router;

                try {
                    final FailureHandler failureHandler = new FailureHandler();
                    final LoadCsvHandler loadCsvHandler = new LoadCsvHandler(aConfig);
                    final BatchJobStatusHandler batchJobStatusHandler = new BatchJobStatusHandler(aConfig);

                    // Next, we associate handlers with routes from our specification
                    routerFactory.addHandlerByOperationId(Op.GET_STATUS, new GetStatusHandler());
                    routerFactory.addHandlerByOperationId(Op.GET_JOBS, new GetJobsHandler());
                    routerFactory.addHandlerByOperationId(Op.GET_JOB_STATUSES, new GetJobStatusesHandler());
                    routerFactory.addHandlerByOperationId(Op.DELETE_JOB, new DeleteJobHandler());
                    routerFactory.addHandlerByOperationId(Op.LOAD_IMAGE, new LoadImageHandler());
                    routerFactory.addFailureHandlerByOperationId(Op.LOAD_IMAGE, failureHandler);
                    routerFactory.addHandlerByOperationId(Op.LOAD_IMAGES_FROM_CSV, loadCsvHandler);
                    routerFactory.addFailureHandlerByOperationId(Op.LOAD_IMAGES_FROM_CSV, failureHandler);
                    routerFactory.addHandlerByOperationId(Op.UPDATE_BATCH_JOB, batchJobStatusHandler);

                    // After that, we can get a router that's been configured by our OpenAPI spec
                    router = routerFactory.getRouter();

                    // Serve our static pages (docs, CSV submission form, etc.)
                    router.get("/*").order(0).blockingHandler(StaticHandler.create("webroot"));

                    // Add an /upload redirect; we may want different options in the future, but not now
                    router.routeWithRegex("/upload(/?)").order(1).handler(context -> {
                        context.response().putHeader("location", "/upload/csv").setStatusCode(307).end();
                    });

                    // Start our server
                    server.requestHandler(router).listen(port);

                    // Deploy our verticles that convert and upload images to S3
                    deployVerticles(aConfig, deployment -> {
                        if (deployment.succeeded()) {
                            aFuture.complete();
                        } else {
                            aFuture.fail(deployment.cause());
                        }
                    });
                } catch (final IOException details) {
                    aFuture.fail(details);
                }
            } else {
                aFuture.fail(creation.cause());
            }
        });
    }

    /**
     * We can take advantage of some native functions if we're running on the right kind of machine.
     *
     * @return The HTTP server options
     */
    private HttpServerOptions getHttpServerOptions() {
        final HttpServerOptions options = new HttpServerOptions();
        final String osName = System.getProperty("os.name", EMPTY);
        final String osArch = System.getProperty("os.arch", EMPTY);

        // Set Linux options if we're running on a Linux machine
        if ("unix".equals(osName) && "amd64".equals(osArch)) {
            options.setTcpFastOpen(true).setTcpCork(true).setTcpQuickAck(true).setReusePort(true);
        }

        return options;
    }

    /**
     * Returns the other non-main verticles.
     *
     * @param aHandler A handler that wraps the results of our verticle deployments
     */
    @SuppressWarnings("rawtypes")
    private void deployVerticles(final JsonObject aConfig, final Handler<AsyncResult<Void>> aHandler) {
        final int uploaderInstances = aConfig.getInteger(Config.S3_UPLOADER_INSTANCES, DEFAULT_S3_UPLOADER_INSTANCES);
        final int uploaderThreads = aConfig.getInteger(Config.S3_UPLOADER_THREADS, DEFAULT_S3_UPLOADER_THREADS);
        final DeploymentOptions uploaderOpts = new DeploymentOptions().setWorker(true);
        final DeploymentOptions workerOpts = new DeploymentOptions().setWorker(true);
        final DeploymentOptions thumbnailOpts = new DeploymentOptions();
        final DeploymentOptions slackOpts = new DeploymentOptions();
        final List<Future> futures = new ArrayList<>();
        final Future<Void> future = Future.future();

        // Set configuration values for our verticles to use
        uploaderOpts.setConfig(aConfig);
        workerOpts.setConfig(aConfig);
        thumbnailOpts.setConfig(aConfig);
        slackOpts.setConfig(aConfig);

        // Set the deployVerticles handler to handle our verticles deploy future
        future.setHandler(aHandler);

        workerOpts.setInstances(1);
        workerOpts.setWorkerPoolSize(1);
        workerOpts.setWorkerPoolName(ImageWorkerVerticle.class.getSimpleName() + THREAD_NAME);

        uploaderOpts.setInstances(uploaderInstances);

        // If we've set threads to <= 0, we want to use whatever cores are available on the system
        if (uploaderThreads <= 0) {
            uploaderOpts.setWorkerPoolSize(DEFAULT_S3_UPLOADER_THREADS);
        } else {
            uploaderOpts.setWorkerPoolSize(uploaderThreads);
        }

        uploaderOpts.setWorkerPoolName(S3BucketVerticle.class.getSimpleName() + THREAD_NAME);

        LOGGER.debug(MessageCodes.BUCKETEER_112, uploaderInstances, uploaderOpts.getWorkerPoolSize());

        // We initiate the various verticles that Bucketeer uses
        futures.add(deployVerticle(ImageWorkerVerticle.class.getName(), workerOpts, Future.future()));
        futures.add(deployVerticle(S3BucketVerticle.class.getName(), uploaderOpts, Future.future()));
        futures.add(deployVerticle(ThumbnailVerticle.class.getName(), thumbnailOpts, Future.future()));
        futures.add(deployVerticle(SlackMessageVerticle.class.getName(), slackOpts, Future.future()));

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
