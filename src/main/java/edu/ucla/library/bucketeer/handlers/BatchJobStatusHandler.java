
package edu.ucla.library.bucketeer.handlers;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.bucketeer.Config;
import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.HTTP;
import edu.ucla.library.bucketeer.MessageCodes;
import edu.ucla.library.bucketeer.Metadata;
import edu.ucla.library.bucketeer.Metadata.WorkflowState;
import edu.ucla.library.bucketeer.Op;
import edu.ucla.library.bucketeer.verticles.SlackMessageWorkerVerticle;
import edu.ucla.library.bucketeer.verticles.ThumbnailVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.Counter;
import io.vertx.core.shareddata.SharedData;
import io.vertx.ext.web.RoutingContext;

public class BatchJobStatusHandler extends AbstractBucketeerHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(BatchJobStatusHandler.class, Constants.MESSAGES);

    private static final String SLACK_MESSAGE_WORKER_VERTICLE = SlackMessageWorkerVerticle.class.getName();

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
        final Vertx vertx = aContext.vertx();
        final SharedData sharedData = vertx.sharedData();

        if (myVertx == null) {
            myVertx = vertx;
        }

        // grab channel IDs from the config
        final String slackChannelID = myConfig.getString(Config.SLACK_CHANNEL_ID);
        final String slackErrorChannelID = myConfig.getString(Config.SLACK_ERROR_CHANNEL_ID);

        // imageId get decoded, but we actually want the encoded version of it to compare with the jobs queue
        final String imageId = URLEncoder.encode(request.getParam(Constants.IMAGE_ID), StandardCharsets.UTF_8);
        final String jobName = request.getParam(Constants.JOB_NAME);
        final boolean success = Boolean.parseBoolean(request.getParam(Op.SUCCESS));

        sharedData.<String, List<Metadata>>getLocalAsyncMap(Constants.LAMBDA_MAP, getMap -> {
            if (getMap.succeeded()) {
                final AsyncMap<String, List<Metadata>> map = getMap.result();

                map.keys(keyCheck -> {
                    if (keyCheck.succeeded()) {
                        final Set<String> jobs = keyCheck.result();

                        if (jobs.contains(jobName)) {
                            map.get(jobName, getJob -> {
                                if (getJob.succeeded()) {
                                    final List<Metadata> metadataList = getJob.result();
                                    final ListIterator<Metadata> iterator = metadataList.listIterator();

                                    boolean finished = true;
                                    boolean found = false;

                                    while (iterator.hasNext()) {
                                        final Metadata metadata = iterator.next();
                                        final String id = metadata.getID();

                                        if (imageId.equals(id)) {
                                            if (!success) {
                                                metadata.setWorkflowState(WorkflowState.FAILED);
                                            } else {
                                                metadata.setWorkflowState(WorkflowState.SUCCEEDED);
                                            }

                                            found = true;
                                        }

                                        if (WorkflowState.EMPTY.equals(metadata.getWorkflowState())) {
                                            finished = false;
                                        }
                                    }

                                    // Could be some random submission, but it's worth flagging as suspicious
                                    if (!found) {
                                        LOGGER.warn(MessageCodes.BUCKETEER_077, jobName, imageId);
                                    }

                                    // Have we finished processing all the images in this job?
                                    if (finished) {
                                        decrementJobsCounter(sharedData, map, jobName, response, true,
                                                slackErrorChannelID, slackChannelID);
                                    } else {
                                        decrementJobsCounter(sharedData, map, jobName, response, false,
                                                slackErrorChannelID, slackChannelID);
                                    }
                                } else {
                                    returnError(getJob.cause(), MessageCodes.BUCKETEER_076, jobName, response,
                                            slackErrorChannelID);
                                }
                            });
                        } else {
                            returnError(MessageCodes.BUCKETEER_075, jobName, response, slackErrorChannelID);
                        }
                    } else {
                        returnError(keyCheck.cause(), MessageCodes.BUCKETEER_062, jobName, response,
                                slackErrorChannelID);
                    }
                });
            } else {
                returnError(getMap.cause(), MessageCodes.BUCKETEER_063, jobName, response, slackErrorChannelID);
            }
        });
    }

    private void returnError(final String aMessageCode, final String aDetail, final HttpServerResponse aResponse,
            final String aSlackErrorChannelID) {
        returnError(null, aMessageCode, aDetail, aResponse, aSlackErrorChannelID);
    }

    private void returnError(final Throwable aThrowable, final String aMessageCode, final String aDetail,
            final HttpServerResponse aResponse, final String aSlackErrorChannelID) {
        final String errorMessage = LOGGER.getMessage(aMessageCode, aDetail);

        if (aThrowable != null) {
            LOGGER.error(aThrowable, errorMessage);
        } else {
            LOGGER.error(errorMessage);
        }

        // Send notice about error to Slack channel too
        sendSlackMessage(Constants.SLACK_ERROR_MESSAGE_PREFIX + Constants.SPACE + errorMessage, aSlackErrorChannelID);

        aResponse.setStatusCode(HTTP.INTERNAL_SERVER_ERROR);
        aResponse.setStatusMessage(errorMessage);
        aResponse.end();
    }

    /**
     * Send a message to the specified Slack channel, optionally include an attachment (not yet done)
     *
     * @param aMessageText Text of the message we want to send
     * @param aChannelId ID of the channel to which we want to send this message
     */
    private void sendSlackMessage(final String aMessageText, final String aChannelId) {
        final EventBus eventBus = myVertx.eventBus();
        final JsonObject message = new JsonObject();
        final DeliveryOptions options = new DeliveryOptions();

        // build our message object
        message.put(Constants.SLACK_MESSAGE_TEXT, aMessageText);
        message.put(Config.SLACK_CHANNEL_ID, aChannelId);

        eventBus.send(SLACK_MESSAGE_WORKER_VERTICLE, message, options);
    }

    /**
     * Decrement the job counter and do a final check if it's the last job in a batch run.
     *
     * @param aSharedData Data shared across different handler instances
     * @param aJobName The name of the batch job that's currently running
     * @param aResponse An HTTP response
     * @param aCompletedRun Whether or not the batch job has completed
     */
    private void decrementJobsCounter(final SharedData aSharedData, final AsyncMap<String, List<Metadata>> aJobsMap,
            final String aJobName, final HttpServerResponse aResponse, final boolean aCompletedRun,
            final String aSlackErrorChannelID, final String aSlackChannelID) {
        aSharedData.getCounter(aJobName, getCounter -> {
            if (getCounter.succeeded()) {
                final Counter counter = getCounter.result();

                counter.decrementAndGet(decrement -> {
                    if (decrement.succeeded()) {
                        // Double check our belief that this is the last job in the batch run
                        if (aCompletedRun && !decrement.result().equals(0L)) {
                            LOGGER.error(MessageCodes.BUCKETEER_079);

                            // And post to Slack about it so we can investigate
                            sendSlackMessage(Constants.SLACK_ERROR_MESSAGE_PREFIX + Constants.SPACE +
                                    MessageCodes.BUCKETEER_079, aSlackErrorChannelID);
                        } else if (aCompletedRun) {
                            LOGGER.info(MessageCodes.BUCKETEER_081, aJobName);

                            // Remove the batch from our jobs queue
                            aJobsMap.remove(aJobName, removeJob -> {
                                if (removeJob.succeeded()) {
                                    final List<Metadata> metadataList = removeJob.result();
                                    final JsonArray thumbnails = new JsonArray();

                                    // Go through metadata and look for ones that have just succeeded being processed
                                    for (final Metadata metadata : metadataList) {
                                        if (WorkflowState.SUCCEEDED.equals(metadata.getWorkflowState())) {
                                            thumbnails.add(metadata.getID());
                                        }
                                    }

                                    // If there are recently succeeded images, send them off for thumbnail generation
                                    if (thumbnails.size() > 0) {
                                        final JsonObject thumbnailMessage = new JsonObject();

                                        thumbnailMessage.put(Constants.IMAGE_ID_ARRAY, thumbnails);
                                        sendMessage(myVertx, thumbnailMessage, ThumbnailVerticle.class.getName());
                                    }
                                    final List<Metadata> metadata = removeJob.result();
                                    final String slackHandle = metadata.get(0).getSlackHandle();
                                    sendSlackMessage("Hi, <@" + slackHandle +
                                            "> your job is done, and your CSV output file will be attached in a " +
                                            "following message... one moment, please...", aSlackChannelID);

                                    // TODO: Send metadata to Slack (and GitHub, in the future)

                                    aResponse.setStatusCode(HTTP.NO_CONTENT);
                                    aResponse.end();
                                } else {
                                    returnError(removeJob.cause(), MessageCodes.BUCKETEER_082, aJobName, aResponse,
                                            aSlackErrorChannelID);
                                }
                            });
                        } else {
                            aResponse.setStatusCode(HTTP.NO_CONTENT);
                            aResponse.end();
                        }
                    } else {
                        returnError(decrement.cause(), MessageCodes.BUCKETEER_080, aJobName, aResponse,
                                aSlackErrorChannelID);
                    }
                });
            } else {
                returnError(getCounter.cause(), MessageCodes.BUCKETEER_066, aJobName, aResponse,
                        aSlackErrorChannelID);
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

}
