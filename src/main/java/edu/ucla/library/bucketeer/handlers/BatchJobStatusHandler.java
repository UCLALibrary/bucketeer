
package edu.ucla.library.bucketeer.handlers;

import static edu.ucla.library.bucketeer.Constants.EMPTY;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Optional;
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
import edu.ucla.library.bucketeer.verticles.FinalizeJobVerticle;
import edu.ucla.library.bucketeer.verticles.SlackMessageVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.Lock;
import io.vertx.core.shareddata.SharedData;
import io.vertx.ext.web.RoutingContext;

public class BatchJobStatusHandler extends AbstractBucketeerHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(BatchJobStatusHandler.class, Constants.MESSAGES);

    private final JsonObject myConfig;

    private Vertx myVertx;

    /**
     * Creates a handler to ingest CSV files.
     *
     * @param aConfig An application configuration
     */
    public BatchJobStatusHandler(final JsonObject aConfig) {
        myConfig = aConfig;
    }

    @Override
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
                            getLock(sharedData, getLock -> {
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
     * @param aPromise A promise for the work being done
     * @param aHandler A lock handler
     */
    private void getLock(final SharedData aSharedData, final Handler<AsyncResult<Lock>> aHandler) {
        final Promise<Lock> promise = Promise.<Lock>promise();

        promise.future().setHandler(aHandler);

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

                            // Just confirm the config value ends with a slash
                            if (iiif.charAt(iiif.length() - 1) != Constants.SLASH) {
                                iiif.append(Constants.SLASH);
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

                    // We send the name of the job to finalize to the appropriate verticle
                    sendMessage(myVertx, message, FinalizeJobVerticle.class.getName());

                    // Let the submitter know we're done
                    returnSuccess(response, LOGGER.getMessage(MessageCodes.BUCKETEER_081, job.getName()));
                } else {
                    // If not finished, return an acknowledgement to the image processor
                    returnSuccess(response, LOGGER.getMessage(MessageCodes.BUCKETEER_081, job.getName()));
                }
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
     * @param aDetail Details about the exception
     * @param aResponse An HTTP server response
     */
    private void returnError(final Throwable aThrowable, final String aMessageCode, final String aDetails,
            final HttpServerResponse aResponse) {
        final Optional<String> errorChannel = Optional.ofNullable(myConfig.getString(Config.SLACK_ERROR_CHANNEL_ID));
        final String errorDetails = LOGGER.getMessage(aMessageCode, aDetails);

        if (aThrowable != null) {
            LOGGER.error(aThrowable, errorDetails);
        } else {
            LOGGER.error(errorDetails);
        }

        // Send notice about error to Slack channel too if we have one configured
        if (errorChannel.isPresent()) {
            final String errorMessage = LOGGER.getMessage(MessageCodes.BUCKETEER_110, errorDetails);
            final JsonObject message = new JsonObject();

            message.put(Config.SLACK_CHANNEL_ID, errorChannel.get());
            message.put(Constants.SLACK_MESSAGE_TEXT, errorMessage);

            myVertx.eventBus().send(SlackMessageVerticle.class.getName(), message);
        }

        aResponse.setStatusCode(HTTP.INTERNAL_SERVER_ERROR);
        aResponse.setStatusMessage(errorDetails);
        aResponse.end(errorDetails);
    }

    /**
     * Return a successful response to the browser.
     *
     * @param aResponse A HTTP server response
     */
    private void returnSuccess(final HttpServerResponse aResponse, final String aMessage) {
        aResponse.setStatusCode(HTTP.NO_CONTENT);
        aResponse.end(aMessage);
    }
}
