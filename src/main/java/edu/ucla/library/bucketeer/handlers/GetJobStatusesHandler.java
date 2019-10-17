
package edu.ucla.library.bucketeer.handlers;

import java.io.IOException;
import java.util.List;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(GetJobStatusesHandler.class, Constants.MESSAGES);

    private Vertx myVertx;

    @Override
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

                            map.get(jobName, getJob -> {
                                if (getJob.succeeded()) {
                                    final Job job = getJob.result();
                                    final JsonArray images = new JsonArray();

                                    result.put(Constants.COUNT, job.size());
                                    result.put(Constants.SLACK_HANDLE, job.getSlackHandle());

                                    try {
                                        final List<Item> items = job.getItems();

                                        for (final Item item : items) {
                                            final JsonObject image = new JsonObject();
                                            final String filePath;

                                            if (item.hasFile()) {
                                                filePath = item.getFile().getCanonicalPath();
                                            } else {
                                                filePath = "";
                                            }

                                            image.put(Constants.IMAGE_ID, item.getID());
                                            image.put(Constants.STATUS, item.getWorkflowState().toString());
                                            image.put(Constants.FILE_PATH, filePath);

                                            images.add(image);
                                        }

                                        result.put(Constants.JOBS, images);

                                        response.setStatusCode(HTTP.OK);
                                        response.end(result.encodePrettily());
                                    } catch (final IOException details) {
                                        returnError(response, details.getMessage());
                                    }
                                } else {
                                    returnError(response, MessageCodes.BUCKETEER_096);
                                }
                            });
                        } else {
                            response.setStatusCode(HTTP.NOT_FOUND);
                            response.setStatusMessage(LOGGER.getMessage(MessageCodes.BUCKETEER_098, jobName));
                            response.end();
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
