
package edu.ucla.library.bucketeer.handlers;

import java.util.Optional;
import java.util.Set;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.HTTP;
import edu.ucla.library.bucketeer.Item;
import edu.ucla.library.bucketeer.Job;
import edu.ucla.library.bucketeer.MessageCodes;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.ext.web.RoutingContext;

/**
 * Gets the statuses of in-progress jobs
 */
public class GetJobStatusesHandler extends AbstractBucketeerHandler {

    /** The handler's logger. */
    private static final Logger LOGGER = LoggerFactory.getLogger(GetJobStatusesHandler.class, Constants.MESSAGES);

    /** The Vert.x instance. */
    private Vertx myVertx;

    @Override
    @SuppressWarnings("PMD.CognitiveComplexity")
    public void handle(final RoutingContext aContext) {
        final HttpServerResponse response = aContext.response();
        final String jobName = aContext.request().getParam(Constants.JOB_NAME);

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
                            final JsonObject result = new JsonObject();

                            LOGGER.debug(MessageCodes.BUCKETEER_148, jobName);

                            map.get(jobName, getJob -> {
                                if (getJob.succeeded()) {
                                    final Job job = getJob.result();
                                    final JsonArray images = new JsonArray();
                                    final String slackHandle = job.getSlackHandle();
                                    final int count = job.size();

                                    result.put(Constants.COUNT, count);
                                    result.put(Constants.SLACK_HANDLE, slackHandle);
                                    result.put(Constants.REMAINING, job.remaining());

                                    LOGGER.debug(MessageCodes.BUCKETEER_150, jobName, slackHandle, count);

                                    for (final Item item : job.getItems()) {
                                        final Optional<String> filePath = item.getPrefixedFilePath();
                                        final JsonObject image = new JsonObject();

                                        image.put(Constants.IMAGE_ID, item.getID());
                                        image.put(Constants.STATUS, item.getWorkflowState().toString());

                                        if (item.hasFile() && filePath.isPresent()) {
                                            image.put(Constants.FILE_PATH, filePath.get());
                                        } else {
                                            image.put(Constants.FILE_PATH, Constants.EMPTY);
                                        }

                                        images.add(image);
                                    }

                                    result.put(Constants.JOBS, images);

                                    response.setStatusCode(HTTP.OK);
                                    response.end(result.encodePrettily());
                                } else {
                                    returnError(response, MessageCodes.BUCKETEER_096);
                                }
                            });
                        } else {
                            final String notFound = LOGGER.getMessage(MessageCodes.BUCKETEER_098, jobName);
                            LOGGER.debug(MessageCodes.BUCKETEER_149, jobName);

                            response.setStatusCode(HTTP.NOT_FOUND);
                            response.setStatusMessage(notFound);
                            response.end(notFound);
                        }
                    } else {
                        returnError(response, MessageCodes.BUCKETEER_113);
                    }
                });
            } else {
                returnError(response, MessageCodes.BUCKETEER_095);
            }
        });
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }

}
