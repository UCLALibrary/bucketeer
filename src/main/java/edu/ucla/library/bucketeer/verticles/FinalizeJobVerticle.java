
package edu.ucla.library.bucketeer.verticles;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Set;

import javax.naming.ConfigurationException;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;
import info.freelibrary.util.StringUtils;
import info.freelibrary.util.warnings.JDK;

import edu.ucla.library.bucketeer.Config;
import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.Features;
import edu.ucla.library.bucketeer.Job;
import edu.ucla.library.bucketeer.JobNotFoundException;
import edu.ucla.library.bucketeer.MessageCodes;
import edu.ucla.library.bucketeer.Op;
import edu.ucla.library.bucketeer.ProcessingException;
import edu.ucla.library.bucketeer.utils.CodeUtils;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.AsyncMap;

/**
 * A verticle to wrap-up batch jobs once they've been completed.
 */
public class FinalizeJobVerticle extends AbstractBucketeerVerticle {

    /** This verticle's logger. */
    private static final Logger LOGGER = LoggerFactory.getLogger(FinalizeJobVerticle.class, Constants.MESSAGES);

    /** The configuration for this verticle. */
    private JsonObject myConfig;

    /** The URL for the IIIF server. */
    private String myIiifURL;

    /** The ID for the Slack channel this verticle reports to. */
    private String mySlackChannelID;

    /** The file system mount for the CSV. */
    private String myFilesystemCsvMount;

    /** How long Slack retries to send its message. */
    private long mySlackRetryDuration;

    @Override
    public void start() throws Exception {
        super.start();

        myConfig = config();

        myIiifURL = getSimpleURL(myConfig.getString(Config.IIIF_URL));
        mySlackChannelID = myConfig.getString(Config.SLACK_CHANNEL_ID);
        myFilesystemCsvMount = myConfig.getString(Config.FILESYSTEM_CSV_MOUNT);

        if (config().containsKey(Config.SLACK_MAX_RETRIES) && myConfig.containsKey(Config.SLACK_RETRY_DELAY)) {
            mySlackRetryDuration = 1000 * myConfig.getInteger(Config.SLACK_MAX_RETRIES) *
                    myConfig.getInteger(Config.SLACK_RETRY_DELAY);
        } else {
            mySlackRetryDuration = 0;
        }

        if (myFeatureChecker.isPresent() && myFeatureChecker.get().isFeatureEnabled(Features.FS_WRITE_CSV) &&
                myFilesystemCsvMount == null) {
            throw new ConfigurationException(LOGGER.getMessage(MessageCodes.BUCKETEER_518));
        }

        getJsonConsumer().handler(this::handleFinalizeJobMessage);
    }

    /**
     * Handle the finalization of the Job processing message.
     *
     * @param aMessage A message including information about a job
     */
    @SuppressWarnings(JDK.DEPRECATION)
    private void handleFinalizeJobMessage(final Message<JsonObject> aMessage) {
        final JsonObject json = aMessage.body();
        final String jobName = json.getString(Constants.JOB_NAME);

        LOGGER.debug(MessageCodes.BUCKETEER_131, jobName);

        removeJobFuture(jobName).compose(job -> finalizeJob(json, jobName, job)).setHandler(result -> {
            if (result.succeeded()) {
                aMessage.reply(Op.SUCCESS);
            } else {
                handleFinalizeFailure(aMessage, jobName, result.cause());
            }
        });
    }

    /**
     * Handle a failure in the finalization process.
     *
     * @param aMessage A message about the finalization process
     * @param aJobName A job name
     * @param aError A error representing the finalization failure
     */
    private void handleFinalizeFailure(final Message<JsonObject> aMessage, final String aJobName,
            final Throwable aError) {
        if (aError instanceof IOException || aError instanceof ProcessingException) {
            aMessage.fail(CodeUtils.getInt(MessageCodes.BUCKETEER_089), aError.getMessage());
        } else {
            aMessage.reply(Op.FS_WRITE_CSV_FAILURE);
        }
    }

    /**
     * Remove the job from the processing queue.
     *
     * @param aJobName A job name
     * @return A future for the removal of the job associated with the supplied name
     */
    @SuppressWarnings(JDK.DEPRECATION)
    private Future<Job> removeJobFuture(final String aJobName) {
        final Future<Job> future = Future.future();

        removeJob(aJobName, ar -> {
            if (ar.succeeded()) {
                future.complete(ar.result());
            } else {
                future.fail(ar.cause());
            }
        });

        return future;
    }

    /**
     * Finalizes a processing job.
     *
     * @param aJsonObj
     * @param aJobName
     * @param job
     * @return
     */
    @SuppressWarnings(JDK.DEPRECATION)
    private Future<Void> finalizeJob(final JsonObject aJsonObj, final String aJobName, final Job job) {
        final Future<Void> future = Future.future();
        final String fileName = aJobName + ".csv";
        final Optional<String> slackHandle = Optional.ofNullable(job.getSlackHandle());

        // @formatter:off
        job.updateMetadata(vertx).compose(this::toCsvFuture) //
            .compose((String csvData) -> writeCsvIfEnabled(fileName, csvData).compose(writeResult -> {
                return notifySlackAndFinish(aJsonObj, job, slackHandle, fileName, csvData, writeResult);
            }))
            .onComplete(result -> {
                if (result.succeeded()) {
                    future.complete();
                } else {
                    future.fail(result.cause());
                }
            });
        // @formatter:on

        return future;
    }

    @SuppressWarnings(JDK.DEPRECATION)
    private Future<Void> notifySlackAndFinish(final JsonObject json, final Job job, final Optional<String> slackHandle,
            final String fileName, final String csvData, final Boolean attemptedCsvWriteSucceeded) {
        final Future<Void> future = Future.future();
        final String csvWriteStatusMsg;
        final boolean shouldFailFuture;

        if (attemptedCsvWriteSucceeded != null) {
            if (attemptedCsvWriteSucceeded) {
                csvWriteStatusMsg = LOGGER.getMessage(MessageCodes.BUCKETEER_519, fileName);
            } else {
                csvWriteStatusMsg = LOGGER.getMessage(MessageCodes.BUCKETEER_520, fileName, "see error log");
            }

            shouldFailFuture = !attemptedCsvWriteSucceeded;
        } else {
            csvWriteStatusMsg = Constants.EMPTY;
            shouldFailFuture = false;
        }

        if (slackHandle.isPresent()) {
            final String jobResultMsg;
            final String slackMessage;

            if (json.containsKey(Constants.NOTHING_PROCESSED)) {
                jobResultMsg = LOGGER.getMessage(MessageCodes.BUCKETEER_510, slackHandle.get(), job.getName());
            } else {
                jobResultMsg = LOGGER.getMessage(MessageCodes.BUCKETEER_111, slackHandle.get(), job.size(),
                        job.failedItems(), job.missingItems(), myIiifURL);
            }

            slackMessage = StringUtils.format("{} {}", jobResultMsg, csvWriteStatusMsg);
            sendSlackMessage(mySlackChannelID, slackMessage, job, csvData);
        }

        if (shouldFailFuture) {
            future.fail(csvWriteStatusMsg);
        } else {
            future.complete();
        }

        return future;
    }

    /**
     * Serializes the Job as CSV.
     *
     * @param aJob A Job to serialize
     * @return
     */
    @SuppressWarnings(JDK.DEPRECATION)
    private Future<String> toCsvFuture(final Job aJob) {
        final Future<String> future = Future.future();

        try {
            future.complete(aJob.toCSV()); // IOException possible
        } catch (final IOException details) {
            future.fail(details);
        }

        return future;
    }

    @SuppressWarnings(JDK.DEPRECATION)
    private Future<Boolean> writeCsvIfEnabled(final String fileName, final String csvData) {
        final Future<Boolean> future = Future.future();

        if (!(myFeatureChecker.isPresent() && myFeatureChecker.get().isFeatureEnabled(Features.FS_WRITE_CSV))) {
            future.complete();
            return future;
        }

        final String dirPath = myFilesystemCsvMount;
        final String filePath = Paths.get(dirPath, fileName).toString();
        final OpenOptions options = new OpenOptions().setWrite(true).setCreate(true).setTruncateExisting(true);

        ensureDirectoryExists(dirPath).setHandler(dirResult -> {
            if (dirResult.failed()) {
                LOGGER.error(MessageCodes.BUCKETEER_520, filePath,
                        "cannot prepare directory: " + dirResult.cause().getMessage());
                future.complete(false);
                return;
            }

            vertx.fileSystem().open(filePath, options, openResult -> {
                if (openResult.failed()) {
                    LOGGER.error(MessageCodes.BUCKETEER_520, filePath,
                            "cannot open: " + openResult.cause().getMessage());
                    future.complete(false);
                    return;
                }

                final AsyncFile file = openResult.result();

                file.write(Buffer.buffer(csvData), writeResult -> {
                    if (writeResult.succeeded()) {
                        file.close(closeResult -> future.complete(true));
                    } else {
                        LOGGER.error(MessageCodes.BUCKETEER_520, filePath,
                                "cannot write: " + writeResult.cause().getMessage());

                        file.close(closeResult -> future.complete(false));
                    }
                });
            });
        });

        return future;
    }

    @SuppressWarnings(JDK.DEPRECATION)
    private Future<Void> ensureDirectoryExists(final String dirPath) {
        final Future<Void> future = Future.future();

        vertx.fileSystem().exists(dirPath, existsResult -> {
            if (existsResult.failed()) {
                future.fail(existsResult.cause());
                return;
            }

            if (existsResult.result()) {
                future.complete();
                return;
            }

            vertx.fileSystem().mkdirs(dirPath, mkdirsResult -> {
                if (mkdirsResult.succeeded()) {
                    future.complete();
                } else {
                    future.fail(mkdirsResult.cause());
                }
            });
        });

        return future;
    }

    /**
     * Extract a simple URL, throwing out extra path/query/etc elements.
     *
     * @param aLongURL The source URL to be stripped down
     * @return A simple URL
     */
    private String getSimpleURL(final String aLongURL) {
        final String colon = ":";
        final String slash = "/";
        final URI uri = URI.create(aLongURL);
        final StringBuilder builder = new StringBuilder().append(uri.getScheme()).append(colon).append(slash)
                .append(slash).append(uri.getHost()).append(slash);
        return builder.toString();
    }

    /**
     * The dirty work of actually getting the job from the shared data cache.
     *
     * @param aJobName The name of a job we want to retrieve
     * @param aHandler A handler to handle the result of the promise
     */
    @SuppressWarnings("PMD.CognitiveComplexity")
    private void removeJob(final String aJobName, final Handler<AsyncResult<Job>> aHandler) {
        final Promise<Job> promise = Promise.<Job>promise();

        promise.future().onComplete(aHandler);

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
                                            failPromise(getMap.cause(), MessageCodes.BUCKETEER_082, aJobName, promise);
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
        sendSlackMessage(aChannelId, aMessageText, null, null);
    }

    /**
     * Send a message to the specified Slack channel with a list of metadata records.
     *
     * @param aMessageText Text of the message we want to send
     * @param aChannelId ID of the channel to which we want to send this message
     * @param aJob A job to notify a Slack user of the completion of
     * @param aCsvData The CSV data to send as a file attachment in the Slack message
     */
    private void sendSlackMessage(final String aChannelId, final String aMessageText, final Job aJob,
            final String aCsvData) {
        final Promise<Void> promise = Promise.promise();
        final JsonObject message = new JsonObject();

        message.put(Config.SLACK_CHANNEL_ID, aChannelId);
        message.put(Constants.SLACK_MESSAGE_TEXT, aMessageText);

        if (aJob != null && aCsvData != null) {
            message.put(Constants.JOB_NAME, aJob.getName());
            message.put(Constants.CSV_DATA, aCsvData);
        }

        // We cannot communicate with Slack so we can't send the expected CSV or notify of this error
        // Instead, we just log the error so that there is some record of this problem.
        promise.future().onFailure(handler -> {
            LOGGER.error(MessageCodes.BUCKETEER_522);
        });

        sendMessage(promise, message, SlackMessageVerticle.class.getName(),
                Math.max(mySlackRetryDuration, DeliveryOptions.DEFAULT_TIMEOUT));
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }
}
