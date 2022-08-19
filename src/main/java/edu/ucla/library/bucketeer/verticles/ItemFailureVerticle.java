
package edu.ucla.library.bucketeer.verticles;

import java.util.Iterator;
import java.util.Set;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.bucketeer.Config;
import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.Item;
import edu.ucla.library.bucketeer.Job;
import edu.ucla.library.bucketeer.Job.WorkflowState;
import edu.ucla.library.bucketeer.MessageCodes;
import edu.ucla.library.bucketeer.Op;
import edu.ucla.library.bucketeer.utils.CodeUtils;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.Lock;
import io.vertx.core.shareddata.SharedData;

/**
 * A verticle that deals with image conversion failures.
 */
public class ItemFailureVerticle extends AbstractBucketeerVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(ItemFailureVerticle.class, Constants.MESSAGES);

    private static final String FINALIZER = FinalizeJobVerticle.class.getName();

    private long myFinalizeJobVerticleSendTimeout;

    @Override
    public void start() throws Exception {
        super.start();

        // Scale the {@link FinalizeJobVerticle} send timeout with the {@link SlackMessageVerticle} retry configuration
        if (config().containsKey(Config.SLACK_MAX_RETRIES) && config().containsKey(Config.SLACK_RETRY_DELAY)) {
            myFinalizeJobVerticleSendTimeout = 1000 * config().getInteger(Config.SLACK_MAX_RETRIES) *
                    config().getInteger(Config.SLACK_RETRY_DELAY);
        } else {
            myFinalizeJobVerticleSendTimeout = DeliveryOptions.DEFAULT_TIMEOUT;
        }

        getJsonConsumer().handler(message -> {
            final JsonObject json = message.body();
            final SharedData sharedData = vertx.sharedData();
            final String jobName = json.getString(Constants.JOB_NAME);

            sharedData.<String, Job>getLocalAsyncMap(Constants.LAMBDA_JOBS, getMap -> {
                if (getMap.succeeded()) {
                    final AsyncMap<String, Job> jobsMap = getMap.result();

                    jobsMap.keys(keyCheck -> {
                        if (keyCheck.succeeded()) {
                            final Set<String> jobs = keyCheck.result();

                            if (jobs.contains(jobName)) {
                                getLock(sharedData, getLock -> {
                                    if (getLock.succeeded()) {
                                        setItemStatus(getLock.result(), jobsMap, message);
                                    } else {
                                        LOGGER.error(getLock.cause(), MessageCodes.BUCKETEER_132, jobName);
                                        message.fail(CodeUtils.getInt(MessageCodes.BUCKETEER_132), jobName);
                                    }
                                });
                            } else {
                                message.fail(CodeUtils.getInt(MessageCodes.BUCKETEER_075), jobName);
                            }
                        } else {
                            LOGGER.error(keyCheck.cause(), MessageCodes.BUCKETEER_062, jobName);
                            message.fail(CodeUtils.getInt(MessageCodes.BUCKETEER_062), jobName);
                        }
                    });
                } else {
                    LOGGER.error(getMap.cause(), MessageCodes.BUCKETEER_063, jobName);
                    message.fail(CodeUtils.getInt(MessageCodes.BUCKETEER_063), jobName);
                }
            });
        });
    }

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
    private void setItemStatus(final Lock aLock, final AsyncMap<String, Job> aJobsMap,
            final Message<JsonObject> aMessage) {
        final JsonObject json = aMessage.body();
        final String jobName = json.getString(Constants.JOB_NAME);
        final String imageID = json.getString(Constants.IMAGE_ID);

        aJobsMap.get(jobName, getJob -> {
            if (getJob.succeeded()) {
                final Job job = getJob.result();
                final Iterator<Item> iterator = job.getItems().iterator();

                boolean finished = true;
                boolean found = false;

                while (iterator.hasNext()) {
                    final Item item = iterator.next();
                    final String id = item.getID();

                    // Check to see if this is the item we're marking as failed
                    if (imageID.equals(id)) {
                        item.setWorkflowState(WorkflowState.FAILED);
                        found = true;

                        LOGGER.debug(MessageCodes.BUCKETEER_141, id);
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
                    LOGGER.warn(MessageCodes.BUCKETEER_077, jobName, imageID);
                }

                if (finished) {
                    sendMessage(Promise.promise(), new JsonObject().put(Constants.JOB_NAME, job.getName()), FINALIZER,
                            myFinalizeJobVerticleSendTimeout);
                }

                aMessage.reply(Op.SUCCESS);
            } else {
                aLock.release();
                LOGGER.error(getJob.cause(), MessageCodes.BUCKETEER_076, jobName);
                aMessage.fail(CodeUtils.getInt(MessageCodes.BUCKETEER_076), jobName);
            }
        });
    }
}
