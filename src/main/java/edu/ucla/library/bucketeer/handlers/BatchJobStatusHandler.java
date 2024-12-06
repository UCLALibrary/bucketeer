
package edu.ucla.library.bucketeer.handlers;

import static edu.ucla.library.bucketeer.Constants.EMPTY;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Set;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.bucketeer.Config;
import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.HTTP;
import edu.ucla.library.bucketeer.Item;
import edu.ucla.library.bucketeer.Job;
import edu.ucla.library.bucketeer.Job.WorkflowState;
import edu.ucla.library.bucketeer.MessageCodes;
import edu.ucla.library.bucketeer.Op;
import edu.ucla.library.bucketeer.verticles.ClearCacheVerticle;
import edu.ucla.library.bucketeer.verticles.FinalizeJobVerticle;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.Lock;
import io.vertx.core.shareddata.SharedData;
import io.vertx.ext.web.RoutingContext;

/**
 * A handler for batch job status requests.
 */
public class BatchJobStatusHandler extends AbstractBucketeerHandler {

    /** The handler's logger. */
    private static final Logger LOGGER = LoggerFactory.getLogger(BatchJobStatusHandler.class, Constants.MESSAGES);

    /** The handler's configuration. */
    private final JsonObject myConfig;

    /** The Slack message retry duration. */
    private final long mySlackRetryDuration;

    /** The Vert.x instance. */
    private Vertx myVertx;

    /**
     * Creates a handler to ingest CSV files.
     *
     * @param aConfig An application configuration
     */
    public BatchJobStatusHandler(final JsonObject aConfig) {
        myConfig = aConfig;

        // Scale the {@link FinalizeJobVerticle} send timeout with the {@link SlackMessageVerticle} retry configuration
        if (aConfig.containsKey(Config.SLACK_MAX_RETRIES) && aConfig.containsKey(Config.SLACK_RETRY_DELAY)) {
            mySlackRetryDuration =
                    1000 * aConfig.getInteger(Config.SLACK_MAX_RETRIES) * aConfig.getInteger(Config.SLACK_RETRY_DELAY);
        } else {
            mySlackRetryDuration = 0;
        }
    }

    @Override
    @SuppressWarnings("PMD.CognitiveComplexity")
    public void handle(final RoutingContext aContext) {
        final HttpServerResponse response = aContext.response();
        final HttpServerRequest request = aContext.request();
        final String jobName = request.getParam(Constants.JOB_NAME);
        final SharedData sharedData;

        if (myVertx == null) {
            myVertx = aContext.vertx();
        }

        // Get the shared data store, where we keep our in-process jobs
        sharedData = myVertx.sharedData();

        sharedData.<String, Job>getLocalAsyncMap(Constants.LAMBDA_JOBS, getMap -> {
            if (getMap.succeeded()) {
                final AsyncMap<String, Job> map = getMap.result();

                map.keys(keyCheck -> {
                    if (keyCheck.succeeded()) {
                        final Set<String> jobs = keyCheck.result();

                        if (jobs.contains(jobName)) {
                            handleLock(sharedData, getLock -> {
                                if (getLock.succeeded()) {
                                    setJobStatus(getLock.result(), map, aContext);
                                } else {
                                    returnError(getLock.cause(), MessageCodes.BUCKETEER_132, jobName, response);
                                }
                            });
                        } else {
                            returnError(MessageCodes.BUCKETEER_075, jobName, response);
                        }
                    } else {
                        returnError(keyCheck.cause(), MessageCodes.BUCKETEER_062, jobName, response);
                    }
                });
            } else {
                returnError(getMap.cause(), MessageCodes.BUCKETEER_063, jobName, response);
            }
        });
    }

    /**
     * Return the Logger associated with this handler.
     *
     * @return The Logger associated with this handler
     */
    @Override
    protected Logger getLogger() {
        return LOGGER;
    }

    /**
     * Gets a lock for use with updates.
     *
     * @param aSharedData A reference to the application's shared data
     * @param aHandler A lock handler
     */
    private void handleLock(final SharedData aSharedData, final Handler<AsyncResult<Lock>> aHandler) {
        final Promise<Lock> promise = Promise.<Lock>promise();

        promise.future().onComplete(aHandler);

        aSharedData.getLocalLockWithTimeout(Constants.JOB_LOCK, Constants.JOB_LOCK_TIMEOUT, getLock -> {
            if (getLock.succeeded()) {
                promise.complete(getLock.result());
            } else {
                promise.fail(getLock.cause());
            }
        });
    }

    /**
     * Sets the status of the submitted job and sends to the job finalizer if the job is done.
     *
     * @param aLock A lock
     * @param aJobsMap The jobs map
     * @param aContext A routing context
     */
    @SuppressWarnings({ "deprecation", "PMD.CognitiveComplexity" })
    private void setJobStatus(final Lock aLock, final AsyncMap<String, Job> aJobsMap, final RoutingContext aContext) {
        final HttpServerResponse response = aContext.response();
        final HttpServerRequest request = aContext.request();
        final String imageId = request.getParam(Constants.IMAGE_ID);
        final String jobName = request.getParam(Constants.JOB_NAME);
        final boolean success = Boolean.parseBoolean(request.getParam(Op.SUCCESS));

        aJobsMap.get(jobName, getJob -> {
            if (getJob.succeeded()) {
                final Job job = getJob.result();
                final Iterator<Item> iterator = job.getItems().iterator();

                boolean finished = true;
                boolean found = false;

                while (iterator.hasNext()) {
                    final Item item = iterator.next();
                    final String id = item.getID();

                    // Check to see if this is the item we're getting a status report for
                    if (imageId.equals(id)) {
                        LOGGER.debug(MessageCodes.BUCKETEER_115, id, success);

                        if (!success) {
                            item.setWorkflowState(WorkflowState.FAILED);
                        } else if (item.hasFile()) {
                            final StringBuilder iiif = new StringBuilder(myConfig.getString(Config.IIIF_URL, EMPTY));
                            final String prefix = myConfig.getString(Config.IIIF_PREFIX, EMPTY);

                            // Just confirm the config value ends with a slash
                            if (iiif.charAt(iiif.length() - 1) != Constants.SLASH) {
                                iiif.append(Constants.SLASH);
                            }

                            if (!EMPTY.equals(prefix)) {
                                final StringBuilder iiifPrefix = new StringBuilder(prefix);

                                // We ensure the IIIF URL ends with a slash
                                if (iiifPrefix.charAt(0) == Constants.SLASH) {
                                    iiifPrefix.deleteCharAt(0);
                                }

                                // Make sure the prefix ends with a slash if there is one
                                if (iiifPrefix.charAt(iiifPrefix.length() - 1) != Constants.SLASH) {
                                    iiifPrefix.append(Constants.SLASH);
                                }

                                iiif.append(iiifPrefix);
                            }

                            item.setWorkflowState(WorkflowState.SUCCEEDED);
                            item.setAccessURL(iiif.append(URLEncoder.encode(id, StandardCharsets.UTF_8)).toString());
                        }

                        found = true;
                    }

                    // If any workflow object still has an empty state, the job isn't done
                    if (WorkflowState.EMPTY.equals(item.getWorkflowState())) {
                        finished = false;
                    }
                }

                // We're done with any updates we're going to do, we can release the lock
                aLock.release();

                // Could be some random submission, but it's worth flagging as suspicious
                if (!found) {
                    LOGGER.warn(MessageCodes.BUCKETEER_077, jobName, imageId);
                }

                if (finished) {
                    final JsonObject message = new JsonObject().put(Constants.JOB_NAME, job.getName());

                    // Clear Cantaloupe cache of already processed images
                    myVertx.eventBus().<JsonObject>send(ClearCacheVerticle.class.getName(),
                            new JsonObject().put(Constants.IMAGE_ID, imageId), reply -> {
                                if (reply.failed()) {
                                    LOGGER.error(MessageCodes.BUCKETEER_607, reply.cause());
                                }
                            });

                    sendMessage(myVertx, message, FinalizeJobVerticle.class.getName(),
                            Math.max(mySlackRetryDuration, DeliveryOptions.DEFAULT_TIMEOUT));
                }

                returnSuccess(response, LOGGER.getMessage(MessageCodes.BUCKETEER_081, job.getName()));
            } else {
                aLock.release();
                returnError(getJob.cause(), MessageCodes.BUCKETEER_076, jobName, response);
            }
        });
    }

    /**
     * Return an error message to the browser.
     *
     * @param aMessageCode A MessageCode string
     * @param aDetail Details about the exception
     * @param aResponse An HTTP server response
     */
    private void returnError(final String aMessageCode, final String aDetail, final HttpServerResponse aResponse) {
        returnError(null, aMessageCode, aDetail, aResponse);
    }

    /**
     * Return an error message to the browser.
     *
     * @param aThrowable An exception with a stack trace
     * @param aMessageCode A MessageCode string
     * @param aDetailsMessage Details about the exception
     * @param aResponse An HTTP server response
     */
    private void returnError(final Throwable aThrowable, final String aMessageCode, final String aDetailsMessage,
            final HttpServerResponse aResponse) {
        final String errorDetails = LOGGER.getMessage(aMessageCode, aDetailsMessage);

        if (aThrowable != null) {
            LOGGER.error(aThrowable, errorDetails);
        } else {
            LOGGER.error(errorDetails);
        }

        aResponse.setStatusCode(HTTP.INTERNAL_SERVER_ERROR);
        aResponse.setStatusMessage(errorDetails);
        aResponse.end(errorDetails);
    }

    /**
     * Return a successful response to the browser.
     *
     * @param aResponse A HTTP server response
     * @param aMessage A response message
     */
    private void returnSuccess(final HttpServerResponse aResponse, final String aMessage) {
        aResponse.setStatusCode(HTTP.OK);
        aResponse.end(aMessage);
    }
}
