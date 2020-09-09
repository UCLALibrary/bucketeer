
package edu.ucla.library.bucketeer.verticles;

import java.net.URI;
import java.util.Optional;
import java.util.Set;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.bucketeer.Config;
import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.Job;
import edu.ucla.library.bucketeer.JobNotFoundException;
import edu.ucla.library.bucketeer.MessageCodes;
import edu.ucla.library.bucketeer.Op;
import edu.ucla.library.bucketeer.utils.CodeUtils;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.AsyncMap;

public class FinalizeJobVerticle extends AbstractBucketeerVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(FinalizeJobVerticle.class, Constants.MESSAGES);

    private JsonObject myConfig;

    @Override
    public void start() throws Exception {
        super.start();

        myConfig = config();

        getJsonConsumer().handler(message -> {
            final JsonObject json = message.body();
            final String jobName = json.getString(Constants.JOB_NAME);

            // Announce the finalization of the supplied batch job
            LOGGER.debug(MessageCodes.BUCKETEER_131, jobName);

            removeJob(jobName, removeJob -> {
                if (removeJob.succeeded()) {
                    final Job job = removeJob.result();
                    final Optional<String> slackHandle = Optional.ofNullable(job.getSlackHandle());
                    final String slackChannelID = myConfig.getString(Config.SLACK_CHANNEL_ID);

                    // If we have someone waiting on this result, let them know
                    if (slackHandle.isPresent()) {
                        final String iiifURL = getSimpleURL(myConfig.getString(Config.IIIF_URL));
                        final String slackMessage = LOGGER.getMessage(MessageCodes.BUCKETEER_111, slackHandle.get(),
                                job.size(), iiifURL);

                        sendSlackMessage(slackChannelID, slackMessage, job);
                    }

                    message.reply(Op.SUCCESS);
                } else {
                    message.fail(CodeUtils.getInt(MessageCodes.BUCKETEER_137), jobName);
                }
            });
        });
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }

    /**
     * Extract a simple URL, throwing out extra path/query/etc elements.
     *
     * @param aLongURL The source URL to be stripped dowwn
     */
    private String getSimpleURL(final String aLongURL) {
        final String colon = ":";
        final String slash = "/";
        final URI uri = URI.create(aLongURL);
        final StringBuilder builder = new StringBuilder().append(uri.getScheme()).append(colon)
            .append(slash).append(slash).append(uri.getHost()).append(slash);
        return builder.toString();
    }

    /**
     * The dirty work of actually getting the job from the shared data cache.
     *
     * @param aJobName The name of a job we want to retrieve
     * @param aPromise A promise for the work being done
     * @param aHandler A handler to handle the result of the promise
     */
    private void removeJob(final String aJobName, final Handler<AsyncResult<Job>> aHandler) {
        final Promise<Job> promise = Promise.<Job>promise();

        promise.future().setHandler(aHandler);

        vertx.sharedData().<String, Job>getLocalAsyncMap(Constants.LAMBDA_JOBS, getMap -> {
            if (getMap.succeeded()) {
                final AsyncMap<String, Job> map = getMap.result();

                map.keys(keyCheck -> {
                    if (keyCheck.succeeded()) {
                        final Set<String> jobs = keyCheck.result();

                        if (jobs.contains(aJobName)) {
                            map.get(aJobName, getJob -> {
                                if (getJob.succeeded()) {
                                    map.remove(aJobName, removeJob -> {
                                        if (removeJob.succeeded()) {
                                            promise.complete(removeJob.result());
                                        } else {
                                            failPromise(getMap.cause(), MessageCodes.BUCKETEER_082, aJobName,
                                                    promise);
                                        }
                                    });
                                } else {
                                    failPromise(getJob.cause(), MessageCodes.BUCKETEER_076, aJobName, promise);
                                }
                            });
                        } else {
                            failPromise(new JobNotFoundException(MessageCodes.BUCKETEER_075, aJobName),
                                    MessageCodes.BUCKETEER_075, aJobName, promise);
                        }
                    } else {
                        failPromise(keyCheck.cause(), MessageCodes.BUCKETEER_062, aJobName, promise);
                    }
                });
            } else {
                failPromise(getMap.cause(), MessageCodes.BUCKETEER_063, Constants.LAMBDA_JOBS, promise);
            }
        });
    }

    /**
     * Log failures and fail the promise.
     *
     * @param aException An exception indicating the type of failure
     * @param aMessageCode A message code for I18N messages
     * @param aMessage Additional details to add to the message
     * @param aPromise A promise for the work being done
     */
    private void failPromise(final Throwable aException, final String aMessageCode, final String aMessage,
            final Promise<Job> aPromise) {
        final Optional<String> errorChannel = Optional.ofNullable(myConfig.getString(Config.SLACK_ERROR_CHANNEL_ID));

        // If we have a Slack channel configured, we can send an error message to it
        if (errorChannel.isPresent()) {
            final String errorMessage = LOGGER.getMessage(MessageCodes.BUCKETEER_110, aException.getMessage());

            sendSlackMessage(errorChannel.get(), errorMessage);
        }

        LOGGER.error(aException, aMessageCode, aMessage);
        aPromise.fail(aException);
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

        sendMessage(message, SlackMessageVerticle.class.getName());
    }
}
