
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
import io.vertx.core.file.OpenOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.AsyncMap;

/**
 * A verticle to wrap-up batch jobs once they've been completed.
 */
public class FinalizeJobVerticle extends AbstractBucketeerVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(FinalizeJobVerticle.class, Constants.MESSAGES);

    private JsonObject myConfig;

    private String myIiifURL;

    private String mySlackChannelID;

    private String myFilesystemCsvMount;

    @Override
    public void start() throws Exception {
        super.start();

        myConfig = config();

        myIiifURL = getSimpleURL(myConfig.getString(Config.IIIF_URL));
        mySlackChannelID = myConfig.getString(Config.SLACK_CHANNEL_ID);
        myFilesystemCsvMount = myConfig.getString(Config.FILESYSTEM_CSV_MOUNT);

        // Throw an error if the CSV filesystem mount feature is turned on but we don't have the path configured
        if (myFeatureChecker.isPresent() && myFeatureChecker.get().isFeatureEnabled(Features.FS_WRITE_CSV)
                && myFilesystemCsvMount == null) {
            throw new ConfigurationException(LOGGER.getMessage(MessageCodes.BUCKETEER_518));
        }

        getJsonConsumer().handler(message -> {
            final JsonObject json = message.body();
            final String jobName = json.getString(Constants.JOB_NAME);

            // Announce the finalization of the supplied batch job
            LOGGER.debug(MessageCodes.BUCKETEER_131, jobName);

            removeJob(jobName, removeJob -> {
                if (removeJob.succeeded()) {
                    final Job job = removeJob.result();
                    final String fileName = jobName + ".csv";
                    final Optional<String> slackHandle = Optional.ofNullable(job.getSlackHandle());
                    final String csvData;

                    try {
                        // Update the job's metadata and serialize it to CSV format
                        csvData = job.updateMetadata().toCSV();

                        Future.<Boolean>future(writeAttempt -> {
                            // Determine if we should try to write the CSV to the local filesystem
                            if (myFeatureChecker.isPresent()
                                    && myFeatureChecker.get().isFeatureEnabled(Features.FS_WRITE_CSV)) {
                                // Open the file for writing; create it if it doesn't exist, overwrite it if it does
                                final String filePath = Paths
                                        .get(myFilesystemCsvMount, fileName).toString();
                                final OpenOptions options = new OpenOptions().setWrite(true).setCreate(true)
                                        .setTruncateExisting(true);

                                // If the destination directory doesn't exist, create it
                                if (!vertx.fileSystem().existsBlocking(myFilesystemCsvMount)) {
                                    vertx.fileSystem().mkdirBlocking(myFilesystemCsvMount);
                                }

                                vertx.fileSystem().open(filePath, options, open -> {
                                    // Complete the promise with whether or not the attempted write succeeded
                                    if (open.succeeded()) {
                                        open.result().write(Buffer.buffer(csvData), write -> {
                                            if (write.succeeded()) {
                                                writeAttempt.complete(true);
                                            } else {
                                                LOGGER.error(MessageCodes.BUCKETEER_520, filePath,
                                                        "cannot write: " + write.cause().getMessage());
                                                writeAttempt.complete(false);
                                            }
                                        }).close();
                                    } else {
                                        LOGGER.error(MessageCodes.BUCKETEER_520, filePath,
                                                "cannot open: " + open.cause().getMessage());
                                        // Complete the promise rather than fail in order to send a Slack message
                                        writeAttempt.complete(false);
                                    }
                                });
                            } else {
                                // No write was attempted, so complete the promise with null
                                writeAttempt.complete();
                            }
                        }).compose(attemptedCsvWriteSucceeded -> {
                            return Future.<String>future(writeAttempt -> {
                                // We still want to send a Slack message even if the CSV write failed
                                // Determine what (if anything) to tell the Slack user about it
                                final String csvWriteStatusMsg;
                                final boolean shouldFailPromise;

                                if (attemptedCsvWriteSucceeded != null) {
                                    if (attemptedCsvWriteSucceeded) {
                                        csvWriteStatusMsg = LOGGER.getMessage(MessageCodes.BUCKETEER_519, fileName);
                                    } else {
                                        csvWriteStatusMsg = LOGGER.getMessage(MessageCodes.BUCKETEER_520, fileName,
                                                "see error log");
                                    }
                                    shouldFailPromise = !attemptedCsvWriteSucceeded;
                                } else {
                                    // We didn't try to write the CSV, so don't mention it to the user
                                    csvWriteStatusMsg = "";
                                    shouldFailPromise = false;
                                }

                                // If we have someone waiting on this result, let them know via Slack
                                if (slackHandle.isPresent()) {
                                    final String jobResultMsg;
                                    final String slackMessage;

                                    if (json.containsKey(Constants.NOTHING_PROCESSED)) {
                                        jobResultMsg = LOGGER.getMessage(MessageCodes.BUCKETEER_510, slackHandle.get(),
                                                job.getName());
                                    } else {
                                        jobResultMsg = LOGGER.getMessage(MessageCodes.BUCKETEER_111, slackHandle.get(),
                                                job.size(), job.failedItems(), job.missingItems(), myIiifURL);
                                    }
                                    slackMessage = StringUtils.format("{} {}", jobResultMsg, csvWriteStatusMsg);

                                    sendSlackMessage(mySlackChannelID, slackMessage, job, csvData);
                                }

                                if (shouldFailPromise) {
                                    // If we get here, that means csvWriteStatusMsg contains an error message related to
                                    // the CSV write attempt
                                    // TODO: factor in the result of the Slack message send
                                    writeAttempt.fail(csvWriteStatusMsg);
                                } else {
                                    writeAttempt.complete();
                                }
                            });
                        }).onSuccess(unused -> {
                            message.reply(Op.SUCCESS);
                        }).onFailure(failure -> {
                            // TODO: factor in the result of the Slack message send
                            message.reply(Op.FS_WRITE_CSV_FAILURE);
                        });
                    } catch (final IOException | ProcessingException details) {
                        message.fail(CodeUtils.getInt(MessageCodes.BUCKETEER_089), details.getMessage());
                    }
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
        final StringBuilder builder = new StringBuilder().append(uri.getScheme()).append(colon).append(slash)
                .append(slash).append(uri.getHost()).append(slash);
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
        final JsonObject message = new JsonObject();

        message.put(Config.SLACK_CHANNEL_ID, aChannelId);
        message.put(Constants.SLACK_MESSAGE_TEXT, aMessageText);

        if (aJob != null && aCsvData != null) {
            message.put(Constants.JOB_NAME, aJob.getName());
            message.put(Constants.CSV_DATA, aCsvData);
        }

        sendMessage(message, SlackMessageVerticle.class.getName());
    }
}
