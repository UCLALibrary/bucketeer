
package edu.ucla.library.bucketeer.verticles;

import static edu.ucla.library.bucketeer.Constants.MESSAGES;

import java.util.ArrayList;
import java.util.List;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.bucketeer.Config;
import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.HTTP;
import edu.ucla.library.bucketeer.JobFactory;
import edu.ucla.library.bucketeer.MessageCodes;
import edu.ucla.library.bucketeer.Op;
import edu.ucla.library.bucketeer.handlers.BatchJobStatusHandler;
import edu.ucla.library.bucketeer.handlers.DeleteJobHandler;
import edu.ucla.library.bucketeer.handlers.FailureHandler;
import edu.ucla.library.bucketeer.handlers.GetConfigHandler;
import edu.ucla.library.bucketeer.handlers.GetJobStatusesHandler;
import edu.ucla.library.bucketeer.handlers.GetJobsHandler;
import edu.ucla.library.bucketeer.handlers.GetStatusHandler;
import edu.ucla.library.bucketeer.handlers.LoadCsvHandler;
import edu.ucla.library.bucketeer.handlers.LoadImageHandler;
import edu.ucla.library.bucketeer.handlers.MatchingOpNotFoundHandler;
import edu.ucla.library.bucketeer.utils.FilePathPrefixFactory;
import edu.ucla.library.bucketeer.utils.IFilePathPrefix;

import io.vertx.config.ConfigRetriever;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
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

    public static final int DEFAULT_PORT = 8888;

    private static final Logger LOGGER = LoggerFactory.getLogger(MainVerticle.class, MESSAGES);

    private static final String DEFAULT_SPEC = "bucketeer.yaml";

    private static final String THREAD_NAME = "-thread";

    private static final int S3_UPLOADER_INSTANCES = 1;

    private static final int S3_UPLOADER_THREADS = getAvailableProcessors();

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
    public void start(final Promise<Void> aPromise) {
        final ConfigRetriever configRetriever = ConfigRetriever.create(vertx);

        // We pull our application's configuration before configuring the server
        configRetriever.getConfig(configuration -> {
            if (configuration.failed()) {
                aPromise.fail(configuration.cause());
            } else {
                final JsonObject config = configuration.result();
                final String fsMount = config.getString(Config.FILESYSTEM_IMAGE_MOUNT);
                final String fsPrefix = config.getString(Config.FILESYSTEM_IMAGE_PREFIX);

                if (fsMount != null && fsPrefix != null) {
                    final IFilePathPrefix filePathPrefix = FilePathPrefixFactory.getPrefix(fsPrefix, fsMount);

                    // Initialize our JobFactory with our pre-configured IFilePathPrefix implementation
                    JobFactory.getInstance().setPathPrefix(filePathPrefix);
                } else {
                    LOGGER.warn(MessageCodes.BUCKETEER_128);
                }

                // Build the router that will respond to all traffic
                buildRouter(config, aPromise);
            }
        });
    }

    private void buildRouter(final JsonObject aConfig, final Promise<Void> aPromise) {
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
                    final StaticHandler staticHandler = StaticHandler.create();

                    // Next, we associate handlers with routes from our specification
                    routerFactory.addHandlerByOperationId(Op.GET_STATUS, new GetStatusHandler());
                    routerFactory.addHandlerByOperationId(Op.GET_CONFIG, new GetConfigHandler(aConfig));
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

                    // Match all GETs, except GETs for paths that are API endpoints
                    router.getWithRegex("^/(?!(batch|status|config|images)).*").order(0).handler(event -> {
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug(MessageCodes.BUCKETEER_503, event.request().path());
                        }

                        staticHandler.setWebRoot("webroot").setIndexPage("index.html").handle(event);
                    });

                    // Add an /upload redirect; we may want different options in the future, but not now
                    router.routeWithRegex("/upload(/?)").order(1).handler(event -> {
                        event.response().putHeader("location", "/upload/csv").setStatusCode(HTTP.TEMP_REDIRECT).end();
                    });

                    // If an incoming request doesn't match one of our spec operations, it's treated as a 404;
                    // catch these generic 404s with the handler below and return more specific response codes
                    router.errorHandler(HTTP.NOT_FOUND, new MatchingOpNotFoundHandler());

                    // Start our server
                    server.requestHandler(router).listen(port, listening -> {
                        if (listening.succeeded()) {
                            LOGGER.info(MessageCodes.BUCKETEER_502, port);

                            // Deploy our verticles that convert and upload images to S3
                            deployVerticles(aConfig, deployment -> {
                                if (deployment.succeeded()) {
                                    aPromise.complete();
                                } else {
                                    aPromise.fail(deployment.cause());
                                }
                            });
                        } else {
                            aPromise.fail(listening.cause());
                        }
                    });
                } catch (final Exception details) { // turn back to IOException
                    aPromise.fail(details);
                }
            } else {
                aPromise.fail(creation.cause());
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
        final String osName = System.getProperty("os.name", Constants.EMPTY);
        final String osArch = System.getProperty("os.arch", Constants.EMPTY);

        // Set Linux options if we're running on a Linux machine
        if (("unix".equalsIgnoreCase(osName) || "linux".equalsIgnoreCase(osName)) && "amd64".equals(osArch)) {
            options.setTcpFastOpen(true).setTcpCork(true).setTcpQuickAck(true).setReusePort(true);
        } else {
            LOGGER.warn(MessageCodes.BUCKETEER_035, osName, osArch);
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
        final int uploaderInstances = aConfig.getInteger(Config.S3_UPLOADER_INSTANCES, S3_UPLOADER_INSTANCES);
        final int uploaderThreads = aConfig.getInteger(Config.S3_UPLOADER_THREADS, S3_UPLOADER_THREADS);
        final DeploymentOptions uploaderOpts = new DeploymentOptions().setWorker(true);
        final DeploymentOptions workerOpts = new DeploymentOptions().setWorker(true);
        final DeploymentOptions basicOpts = new DeploymentOptions();
        final List<Future> futures = new ArrayList<>();
        final Promise<Void> promise = Promise.promise();

        // Set configuration values for our verticles to use
        uploaderOpts.setConfig(aConfig);
        workerOpts.setConfig(aConfig);
        basicOpts.setConfig(aConfig);

        // Set the deployVerticles handler to handle our verticles deploy future
        promise.future().onComplete(aHandler);

        workerOpts.setInstances(1);
        workerOpts.setWorkerPoolSize(1);
        workerOpts.setWorkerPoolName(ImageWorkerVerticle.class.getSimpleName() + THREAD_NAME);

        uploaderOpts.setInstances(uploaderInstances);

        // If we've set threads to <= 0, we want to use whatever cores are available on the system
        if (uploaderThreads <= 0) {
            uploaderOpts.setWorkerPoolSize(S3_UPLOADER_THREADS);
        } else {
            uploaderOpts.setWorkerPoolSize(uploaderThreads);
        }

        uploaderOpts.setWorkerPoolName(S3BucketVerticle.class.getSimpleName() + THREAD_NAME);

        LOGGER.debug(MessageCodes.BUCKETEER_112, uploaderInstances, uploaderOpts.getWorkerPoolSize());

        // We initiate the various verticles that Bucketeer uses
        futures.add(deployVerticle(ImageWorkerVerticle.class.getName(), workerOpts, Promise.promise()));
        futures.add(deployVerticle(S3BucketVerticle.class.getName(), uploaderOpts, Promise.promise()));
        futures.add(deployVerticle(SlackMessageVerticle.class.getName(), basicOpts, Promise.promise()));
        futures.add(deployVerticle(ItemFailureVerticle.class.getName(), basicOpts, Promise.promise()));
        futures.add(deployVerticle(FinalizeJobVerticle.class.getName(), basicOpts, Promise.promise()));
        futures.add(deployVerticle(LargeImageVerticle.class.getName(), basicOpts, Promise.promise()));
        futures.add(deployVerticle(FesterVerticle.class.getName(), basicOpts, Promise.promise()));
        futures.add(deployVerticle(ClearCacheVerticle.class.getName(), basicOpts, Promise.promise()));

        // Confirm all our verticles were successfully deployed
        CompositeFuture.all(futures).onComplete(handler -> {
            if (handler.succeeded()) {
                promise.complete();
            } else {
                promise.fail(handler.cause());
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
            final Promise<Void> aPromise) {
        vertx.deployVerticle(aVerticleName, aOptions, response -> {
            try {
                final String verticleName = Class.forName(aVerticleName).getSimpleName();

                if (response.succeeded()) {
                    LOGGER.debug(MessageCodes.BUCKETEER_007, verticleName, response.result());
                    aPromise.complete();
                } else {
                    LOGGER.error(MessageCodes.BUCKETEER_008, verticleName, response.cause());
                    aPromise.fail(response.cause());
                }
            } catch (final ClassNotFoundException details) {
                aPromise.fail(details);
            }
        });

        return aPromise.future();
    }
}
