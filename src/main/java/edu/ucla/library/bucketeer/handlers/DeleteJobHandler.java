
package edu.ucla.library.bucketeer.handlers;

import java.util.List;
import java.util.Set;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.HTTP;
import edu.ucla.library.bucketeer.MessageCodes;
import edu.ucla.library.bucketeer.Metadata;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.ext.web.RoutingContext;

/**
 * Deletes an in-progress job.
 */
public class DeleteJobHandler extends AbstractBucketeerHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeleteJobHandler.class, Constants.MESSAGES);

    private Vertx myVertx;

    @Override
    public void handle(final RoutingContext aContext) {
        final HttpServerResponse response = aContext.response();
        final String jobName = aContext.request().getParam(Constants.JOB_NAME);

        if (myVertx == null) {
            myVertx = aContext.vertx();
        }

        myVertx.sharedData().<String, List<Metadata>>getLocalAsyncMap(Constants.LAMBDA_JOBS, getMap -> {
            if (getMap.succeeded()) {
                final AsyncMap<String, List<Metadata>> map = getMap.result();

                map.keys(keyCheck -> {
                    if (keyCheck.succeeded()) {
                        final Set<String> jobs = keyCheck.result();

                        if (jobs.contains(jobName)) {
                            map.remove(jobName, deleteJob -> {
                                if (deleteJob.succeeded()) {
                                    response.setStatusCode(HTTP.NO_CONTENT);
                                    response.setStatusMessage(LOGGER.getMessage(MessageCodes.BUCKETEER_098, jobName));
                                    response.end();
                                } else {
                                    returnError(response, MessageCodes.BUCKETEER_097);
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
