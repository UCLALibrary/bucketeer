
package edu.ucla.library.bucketeer.handlers;

import java.util.Set;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.HTTP;
import edu.ucla.library.bucketeer.Job;
import edu.ucla.library.bucketeer.MessageCodes;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.ext.web.RoutingContext;

/**
 * Deletes a hung job (one that has stopped processing images but hasn't yet finished).
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

        myVertx.sharedData().<String, Job>getLocalAsyncMap(Constants.LAMBDA_JOBS, getMap -> {
            if (getMap.succeeded()) {
                final AsyncMap<String, Job> map = getMap.result();

                map.keys(keyCheck -> {
                    if (keyCheck.succeeded()) {
                        final Set<String> jobs = keyCheck.result();

                        if (jobs.contains(jobName)) {
                            removeJob(jobName, map, removal -> {
                                if (removal.succeeded()) {
                                    final String success = LOGGER.getMessage(MessageCodes.BUCKETEER_144, jobName);
                                    response.setStatusCode(HTTP.OK);
                                    response.setStatusMessage(success);
                                    response.end(success);
                                } else {
                                    final String badRequest = LOGGER.getMessage(MessageCodes.BUCKETEER_142, jobName);
                                    response.setStatusCode(HTTP.BAD_REQUEST);
                                    response.setStatusMessage(badRequest);
                                    response.end(badRequest);
                                }
                            });
                        } else {
                            final String notFound = LOGGER.getMessage(MessageCodes.BUCKETEER_098, jobName);
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

    /**
     * Remove job but only if it's not still actively processing images.
     *
     * @param aJobName The name of the job to remove
     * @param aJobsMap The map of jobs in which the job is found
     * @param aHandler A handler for the result of the removal
     */
    private void removeJob(final String aJobName, final AsyncMap<String, Job> aJobsMap,
            final Handler<AsyncResult<Job>> aHandler) {
        final Promise<Job> promise = Promise.<Job>promise();

        promise.future().setHandler(aHandler);

        aJobsMap.get(aJobName, getJob -> {
            if (getJob.succeeded()) {
                final Job job = getJob.result();
                final int remaining = job.remaining();

                // Wait and see if the number of remaining items has changed
                myVertx.setTimer(Constants.JOB_DELETE_TIMEOUT, timer -> {
                    if (remaining == job.remaining()) {
                        aJobsMap.remove(aJobName, deleteJob -> {
                            if (deleteJob.succeeded()) {
                                promise.complete(job);
                            } else {
                                promise.fail(LOGGER.getMessage(MessageCodes.BUCKETEER_097));
                            }
                        });
                    } else {
                        promise.fail(LOGGER.getMessage(MessageCodes.BUCKETEER_142, aJobName));
                    }
                });
            } else {
                final Throwable exception = getJob.cause();
                final String message = exception.getMessage();

                LOGGER.error(exception, message);
                promise.fail(message);
            }
        });
    }
}
