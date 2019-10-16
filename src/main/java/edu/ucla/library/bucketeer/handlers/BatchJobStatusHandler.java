
package edu.ucla.library.bucketeer.handlers;

import java.io.IOException;
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
import edu.ucla.library.bucketeer.verticles.SlackMessageVerticle;
import edu.ucla.library.bucketeer.verticles.ThumbnailVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.ext.web.RoutingContext;

public class BatchJobStatusHandler extends AbstractBucketeerHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(BatchJobStatusHandler.class, Constants.MESSAGES);

    private static final char SLASH = '/';

    private final JsonObject myConfig;

    private Vertx myVertx;

    /**
     * Creates a handler to ingest CSV files.
     *
     * @param aConfig An application configuration
     */
    public BatchJobStatusHandler(final JsonObject aConfig) throws IOException {
        myConfig = aConfig;
    }

    @Override
    public void handle(final RoutingContext aContext) {
        final HttpServerResponse response = aContext.response();
        final HttpServerRequest request = aContext.request();
        final String jobName = request.getParam(Constants.JOB_NAME);

        if (myVertx == null) {
            myVertx = aContext.vertx();
        }

        myVertx.sharedData().<String, Job>getLocalAsyncMap(Constants.LAMBDA_JOBS, getMap -> {
            if (getMap.succeeded()) {
                final AsyncMap<String, Job> map = getMap.result();

                map.keys(keyCheck -> {
                    if (keyCheck.succeeded()) {
                        final Set<String> jobs = keyCheck.result();

                        if (jobs.contains(jobName)) {
                            map.get(jobName, getJob -> {
                                if (getJob.succeeded()) {
                                    checkJobStatus(map, getJob.result(), aContext);
                                } else {
                                    returnError(getJob.cause(), MessageCodes.BUCKETEER_076, jobName, response);
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

    private void checkJobStatus(final AsyncMap<String, Job> aJobMap, final Job aJob, final RoutingContext aContext) {
        final HttpServerResponse response = aContext.response();
        final HttpServerRequest request = aContext.request();
        final String imageId = request.getParam(Constants.IMAGE_ID);
        final String jobName = request.getParam(Constants.JOB_NAME);
        final boolean success = Boolean.parseBoolean(request.getParam(Op.SUCCESS));
        final Iterator<Item> iterator = aJob.getItems().iterator();

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
                    final StringBuilder iiif = new StringBuilder(myConfig.getString(Config.IIIF_URL, ""));

                    // Just confirm the config value ends with a slash
                    if (iiif.charAt(iiif.length() - 1) != SLASH) {
                        iiif.append(SLASH);
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

        // Could be some random submission, but it's worth flagging as suspicious
        if (!found) {
            LOGGER.warn(MessageCodes.BUCKETEER_077, jobName, imageId);
        }

        if (finished) {
            // If finished, remove the batch from our jobs queue so that it can be submitted again if desired
            aJobMap.remove(jobName, removeJob -> {
                if (removeJob.succeeded()) {
                    finalizeJob(removeJob.result());
                    returnSuccess(response);
                } else {
                    returnError(removeJob.cause(), MessageCodes.BUCKETEER_082, jobName, response);
                }
            });
        } else {
            // If not finished, return an acknowledgement to the image processor
            returnSuccess(response);
        }
    }

    /**
     * Wrap up notification services once the batch job has completed.
     *
     * @param aMetadataList A list of metadata objects
     */
    private void finalizeJob(final Job aJob) {
        final String slackChannelID = myConfig.getString(Config.SLACK_CHANNEL_ID);
        final String slackHandle = aJob.getSlackHandle();
        final String slackMessage = LOGGER.getMessage(MessageCodes.BUCKETEER_111, slackHandle);
        final JsonArray thumbnails = new JsonArray();

        // Go through job items and look for ones that have just succeeded being processed
        for (final Item item : aJob.getItems()) {
            if (WorkflowState.SUCCEEDED.equals(item.getWorkflowState())) {
                thumbnails.add(item.getID());
            }
        }

        // If there are recently succeeded images, send them off for thumbnail generation
        if (thumbnails.size() > 0) {
            final JsonObject thumbnailMessage = new JsonObject();

            thumbnailMessage.put(Constants.IMAGE_ID_ARRAY, thumbnails);
            sendMessage(myVertx, thumbnailMessage, ThumbnailVerticle.class.getName());
        }

        // Once that's completed, we can let the user know their job is ready
        sendSlackMessage(slackChannelID, slackMessage, aJob);
    }

    /**
     * Send a message to the specified Slack channel.
     *
     * @param aMessageText Text of the message we want to send
     * @param aChannelId ID of the channel to which we want to send this message
     */
    private void sendSlackMessage(final String aChannelId, final String aMessageText) {
        sendSlackMessage(aChannelId, aMessageText, null);
    }

    /**
     * Send a message to the specified Slack channel with a list of metadata records.
     *
     * @param aMessageText Text of the message we want to send
     * @param aChannelId ID of the channel to which we want to send this message
     */
    private void sendSlackMessage(final String aChannelId, final String aMessageText, final Job aJob) {
        final JsonObject message = new JsonObject();

        message.put(Config.SLACK_CHANNEL_ID, aChannelId);
        message.put(Constants.SLACK_MESSAGE_TEXT, aMessageText);

        if (aJob != null) {
            message.put(Constants.JOB_NAME, aJob.getName());
            message.put(Constants.BATCH_METADATA, JsonObject.mapFrom(aJob));
        }

        myVertx.eventBus().send(SlackMessageVerticle.class.getName(), message);
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
    private void returnError(final Throwable aThrowable, final String aMessageCode, final String aDetail,
            final HttpServerResponse aResponse) {
        final String errorChannel = myConfig.getString(Config.SLACK_ERROR_CHANNEL_ID);
        final String errorMessage = LOGGER.getMessage(aMessageCode, aDetail);

        if (aThrowable != null) {
            LOGGER.error(aThrowable, errorMessage);
        } else {
            LOGGER.error(errorMessage);
        }

        // Send notice about error to Slack channel too
        sendSlackMessage(errorChannel, LOGGER.getMessage(MessageCodes.BUCKETEER_110, errorMessage));

        aResponse.setStatusCode(HTTP.INTERNAL_SERVER_ERROR);
        aResponse.setStatusMessage(errorMessage);
        aResponse.end();
    }

    /**
     * Return a successful response to the browser.
     *
     * @param aResponse A HTTP server response
     */
    private void returnSuccess(final HttpServerResponse aResponse) {
        aResponse.setStatusCode(HTTP.NO_CONTENT);
        aResponse.end();
    }
}
