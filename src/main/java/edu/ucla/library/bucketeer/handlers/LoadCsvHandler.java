
package edu.ucla.library.bucketeer.handlers;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;

import com.nike.moirai.ConfigFeatureFlagChecker;
import com.nike.moirai.FeatureFlagChecker;
import com.nike.moirai.Suppliers;
import com.nike.moirai.resource.FileResourceLoaders;
import com.nike.moirai.typesafeconfig.TypesafeConfigDecider;
import com.nike.moirai.typesafeconfig.TypesafeConfigReader;

import info.freelibrary.util.FileUtils;
import info.freelibrary.util.IOUtils;
import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;
import info.freelibrary.util.StringUtils;

import edu.ucla.library.bucketeer.Config;
import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.Features;
import edu.ucla.library.bucketeer.HTTP;
import edu.ucla.library.bucketeer.Item;
import edu.ucla.library.bucketeer.Job;
import edu.ucla.library.bucketeer.Job.WorkflowState;
import edu.ucla.library.bucketeer.JobFactory;
import edu.ucla.library.bucketeer.MessageCodes;
import edu.ucla.library.bucketeer.Op;
import edu.ucla.library.bucketeer.ProcessingException;
import edu.ucla.library.bucketeer.utils.CodeUtils;
import edu.ucla.library.bucketeer.verticles.FinalizeJobVerticle;
import edu.ucla.library.bucketeer.verticles.ItemFailureVerticle;
import edu.ucla.library.bucketeer.verticles.LargeImageVerticle;
import edu.ucla.library.bucketeer.verticles.S3BucketVerticle;

import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.SharedData;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RoutingContext;

/**
 * A handler of CSV ingest requests.
 */
@SuppressWarnings("PMD.ExcessiveImports")
public class LoadCsvHandler extends AbstractBucketeerHandler {

    /** The handler's logger. */
    private static final Logger LOGGER = LoggerFactory.getLogger(LoadCsvHandler.class, Constants.MESSAGES);

    /** The job finalizing verticle's name. */
    private static final String JOB_FINALIZER = FinalizeJobVerticle.class.getName();

    /** Default maximum number of bytes in a source image file that we can process on AWS Lambda. */
    private static final long DEFAULT_MAX_FILE_SIZE = 300_000_000L; // 300 MB

    /** Default delay for requeuing, measured in seconds. */
    private static final long DEFAULT_REQUEUE_DELAY = 1;

    /** The handler's configuration. */
    private final JsonObject myConfig;

    /** The Vert.x instance. */
    private Vertx myVertx;

    /** The exception page. */
    private final String myExceptionPage;

    /** The success page. */
    private final String mySuccessPage;

    /** The requeuing delay. */
    private final long myRequeueDelay;

    /** The Slack message retry duration. */
    private final long mySlackRetryDuration;

    /**
     * Creates a handler to ingest CSV files.
     *
     * @param aConfig An application configuration
     * @throws IOException If there is trouble reading the CSV upload
     */
    public LoadCsvHandler(final JsonObject aConfig) throws IOException {
        myExceptionPage = new String(IOUtils.readBytes(getClass().getResourceAsStream("/webroot/error.html")),
                StandardCharsets.UTF_8);
        mySuccessPage = new String(IOUtils.readBytes(getClass().getResourceAsStream("/webroot/success.html")),
                StandardCharsets.UTF_8);

        myRequeueDelay = (myConfig = aConfig).getLong(Config.S3_REQUEUE_DELAY, DEFAULT_REQUEUE_DELAY) * 1000;

        // Scale the {@link FinalizeJobVerticle} send timeout with the {@link SlackMessageVerticle} retry configuration
        if (aConfig.containsKey(Config.SLACK_MAX_RETRIES) && aConfig.containsKey(Config.SLACK_RETRY_DELAY)) {
            mySlackRetryDuration =
                    1000 * aConfig.getInteger(Config.SLACK_MAX_RETRIES) * aConfig.getInteger(Config.SLACK_RETRY_DELAY);
        } else {
            mySlackRetryDuration = 0;
        }
    }

    /**
     * Handles the CSV upload request.
     *
     * @param aContext A routing context
     */
    @Override
    @SuppressWarnings({ "PMD.CognitiveComplexity" })
    public void handle(final RoutingContext aContext) {
        final HttpServerResponse response = aContext.response();
        final HttpServerRequest request = aContext.request();
        final MultiMap formAttributes = request.formAttributes();
        final Set<FileUpload> csvUploads = aContext.fileUploads();
        final String slackHandle = StringUtils.trimToNull(formAttributes.get(Constants.SLACK_HANDLE));

        if (slackHandle == null) {
            final String errorMessage = LOGGER.getMessage(MessageCodes.BUCKETEER_050);
            final String extendedErrorMessage = LOGGER.getMessage(MessageCodes.BUCKETEER_071);

            // Slack handle is required -- don't accept the form without it being supplied
            response.setStatusCode(HTTP.BAD_REQUEST);
            response.setStatusMessage(errorMessage);
            response.putHeader(Constants.CONTENT_TYPE, Constants.HTML);
            response.end(StringUtils.format(myExceptionPage, errorMessage + extendedErrorMessage));
        } else if (csvUploads.isEmpty()) {
            final String errorMessage = LOGGER.getMessage(MessageCodes.BUCKETEER_049);
            final String extendedErrorMessage = LOGGER.getMessage(MessageCodes.BUCKETEER_071);

            // An uploaded CSV file is required -- don't accept the form without it being supplied
            response.setStatusCode(HTTP.BAD_REQUEST);
            response.setStatusMessage(errorMessage);
            response.putHeader(Constants.CONTENT_TYPE, Constants.HTML);
            response.end(StringUtils.format(myExceptionPage, errorMessage + extendedErrorMessage));
        } else {
            final String cleanSlackHandle = slackHandle.replace(Constants.AT, Constants.EMPTY);
            final String runFailures = StringUtils.trimTo(formAttributes.get(Constants.FAILURES_ONLY), "false");
            final boolean subsequentRun = Boolean.parseBoolean(runFailures);
            final FileUpload csvFile = csvUploads.iterator().next();
            final String filePath = csvFile.uploadedFileName();
            final String fileName = csvFile.fileName();
            final String jobName = FileUtils.stripExt(fileName);

            LOGGER.info(MessageCodes.BUCKETEER_051, cleanSlackHandle, fileName, filePath);

            if (myVertx == null) {
                myVertx = aContext.vertx();
            }

            try {
                final Job job = JobFactory.getInstance().createJob(jobName, new File(filePath), subsequentRun);

                // Set the Slack handle
                job.setSlackHandle(cleanSlackHandle);

                initiateJob(job, response);
            } catch (final FileNotFoundException details) {
                final String exceptionMessage = LOGGER.getMessage(MessageCodes.BUCKETEER_073);
                final String exceptionName = LOGGER.getMessage(MessageCodes.BUCKETEER_074);

                LOGGER.error(details, details.getMessage());

                response.setStatusCode(HTTP.INTERNAL_SERVER_ERROR);
                response.putHeader(Constants.CONTENT_TYPE, Constants.HTML);
                response.end(StringUtils.format(myExceptionPage, exceptionName + exceptionMessage));
            } catch (final IOException details) {
                final String exceptionName = LOGGER.getMessage(MessageCodes.BUCKETEER_074);

                LOGGER.error(details, details.getMessage());

                response.setStatusCode(HTTP.INTERNAL_SERVER_ERROR);
                response.putHeader(Constants.CONTENT_TYPE, Constants.HTML);
                response.end(StringUtils.format(myExceptionPage, exceptionName + details.getMessage()));
            } catch (final ProcessingException details) {
                final String statusMessage = LOGGER.getMessage(MessageCodes.BUCKETEER_072);

                response.setStatusCode(HTTP.BAD_REQUEST);
                response.setStatusMessage(statusMessage);
                response.putHeader(Constants.CONTENT_TYPE, Constants.HTML);
                response.end(StringUtils.format(myExceptionPage, details.getMessages("<br>")));
            }
        }
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }

    private void initiateJob(final Job aJob, final HttpServerResponse aResponse) {
        final SharedData sharedData = myVertx.sharedData();
        final String jobName = aJob.getName();

        // Get our job queue
        sharedData.<String, Job>getLocalAsyncMap(Constants.LAMBDA_JOBS, getMap -> {
            if (getMap.succeeded()) {
                final AsyncMap<String, Job> map = getMap.result();

                // Check for the pre-existence of the job name in the map (meaning a job is already running)
                map.keys(keyCheck -> {
                    if (keyCheck.succeeded()) {
                        // If job name is already in the queue, let the user know to check Slack for results
                        if (keyCheck.result().contains(jobName)) {
                            final String duplicate = LOGGER.getMessage(MessageCodes.BUCKETEER_060, jobName);

                            LOGGER.info(duplicate);

                            aResponse.setStatusCode(HTTP.TOO_MANY_REQUESTS);
                            aResponse.setStatusMessage(duplicate);
                            aResponse.putHeader(Constants.CONTENT_TYPE, Constants.HTML);
                            aResponse.end(StringUtils.format(myExceptionPage, duplicate));
                        } else {
                            queueJob(aJob, map, aResponse);
                        }
                    } else {
                        returnError(aResponse, MessageCodes.BUCKETEER_062, jobName);
                    }
                });
            } else {
                returnError(aResponse, MessageCodes.BUCKETEER_063, jobName);
            }
        });
    }

    private void queueJob(final Job aJob, final AsyncMap<String, Job> aAsyncMap, final HttpServerResponse aResponse) {
        final String jobName = aJob.getName();

        // Put our metadata into the jobs queue map using the CSV file name as the key
        aAsyncMap.put(jobName, aJob, put -> {
            if (put.succeeded()) {
                final String statusMessage = LOGGER.getMessage(MessageCodes.BUCKETEER_064);

                LOGGER.info(statusMessage);

                // Don't fail the submission on bad metadata; we'll note it in output CSV instead
                aResponse.setStatusCode(HTTP.OK);
                aResponse.putHeader(Constants.CONTENT_TYPE, Constants.HTML);
                aResponse.end(StringUtils.format(mySuccessPage, statusMessage));

                startJob(aJob);
            } else {
                returnError(aResponse, MessageCodes.BUCKETEER_068, jobName);
            }
        });
    }

    @SuppressWarnings({ "PMD.CyclomaticComplexity", "PMD.CognitiveComplexity" })
    private void startJob(final Job aJob) {
        final String s3Bucket = myConfig.getString(Config.LAMBDA_S3_BUCKET);
        final Iterator<Item> iterator = aJob.getItems().iterator();

        boolean processing = false;

        while (iterator.hasNext()) {
            final Item item = iterator.next();
            final Optional<File> file = item.getFile();
            final WorkflowState state = item.getWorkflowState();

            // Check that file is present so it can be processed
            if (item.hasFile() && file.isPresent() && WorkflowState.EMPTY.equals(state)) {
                final long maxFileSize = myConfig.getLong(Config.MAX_SOURCE_SIZE, DEFAULT_MAX_FILE_SIZE);
                final JsonObject imageRequest = new JsonObject();
                final File source = file.get();
                final long sourceFileSize = source.length();
                final String ext = FileUtils.getExt(source.getName());

                imageRequest.put(Constants.FILE_PATH, source.getAbsolutePath());
                imageRequest.put(Constants.JOB_NAME, aJob.getName());
                imageRequest.put(Config.S3_BUCKET, s3Bucket);

                // Check that the image is not too large for us to process on AWS Lambda
                if (sourceFileSize < maxFileSize) {
                    imageRequest.put(Constants.IMAGE_ID, item.getID() + "." + ext);
                    sendImageRequest(S3BucketVerticle.class.getName(), imageRequest);

                    // If we've sent a upload message, note that we're processing files
                    if (!processing) {
                        processing = true;
                    }
                } else {
                    final Optional<FeatureFlagChecker> featureFlagChecker = getFeatureFlagChecker();

                    // Check if we have a feature flag for processing large images
                    // If we do, check that it's enabled...
                    if (featureFlagChecker.isPresent() &&
                            featureFlagChecker.get().isFeatureEnabled(Features.LARGE_IMAGE_ROUTING)) {
                        imageRequest.put(Constants.IMAGE_ID, item.getID());
                        sendImageRequest(LargeImageVerticle.class.getName(), imageRequest);

                        // If we've send a large image, note that we're processing files
                        if (!processing) {
                            processing = true;
                        }
                    } else { // Feature isn't enabled
                        markItemFailed(item, source);
                    }
                }
            } else {
                final String missingFileMessage = LOGGER.getMessage(MessageCodes.BUCKETEER_147);
                final String filePath = file.isEmpty() ? missingFileMessage : file.get().getAbsolutePath();

                // We might be skipping because there is no file to process or because it's already been done
                LOGGER.debug(MessageCodes.BUCKETEER_054, item.getID(), aJob.getName(), filePath, state);

                // We can't let an empty state go through without an S3 upload attempt
                if (WorkflowState.EMPTY.equals(state)) {
                    if (filePath.equals(missingFileMessage)) {
                        item.setWorkflowState(WorkflowState.MISSING);
                    } else {
                        item.setWorkflowState(WorkflowState.FAILED);
                    }
                }
            }
        }

        // If we don't have any records to process, let's finalize the job and send the CSV back as submitted
        if (!processing) {
            sendMessage(myVertx,
                    new JsonObject().put(Constants.JOB_NAME, aJob.getName()).put(Constants.NOTHING_PROCESSED, true),
                    JOB_FINALIZER, Math.max(mySlackRetryDuration, DeliveryOptions.DEFAULT_TIMEOUT));
        }
    }

    /**
     * Marks an item as failed because we do not have enough information to do something with it.
     *
     * @param aItem An item in the job being processed
     * @param aSource A source file that's being converted
     */
    private void markItemFailed(final Item aItem, final File aSource) {
        if (LOGGER.isWarnEnabled()) {
            final long maxFileSize = myConfig.getLong(Config.MAX_SOURCE_SIZE, DEFAULT_MAX_FILE_SIZE);
            final String fileSize = FileUtils.sizeFromBytes(aSource.length(), true);
            final String maxSize = FileUtils.sizeFromBytes(maxFileSize, true);

            LOGGER.warn(MessageCodes.BUCKETEER_501, aSource, fileSize, maxSize);
        }

        aItem.setWorkflowState(WorkflowState.FAILED);
    }

    /**
     * This message sender is specifically for item processing requests; it behaves differently from the default message
     * sender that our other handlers use.
     *
     * @param aJsonObject A message
     * @param aListener The destination of the message
     */
    private void sendImageRequest(final String aListener, final JsonObject aJsonObject) {
        final DeliveryOptions options = new DeliveryOptions().setSendTimeout(Integer.MAX_VALUE);

        myVertx.eventBus().request(aListener, aJsonObject, options, response -> {
            if (response.failed()) {
                final Throwable exception = response.cause();

                if (exception != null) {
                    if (exception instanceof ReplyException) {
                        final ReplyException replyException = (ReplyException) exception;
                        final String messageCode = CodeUtils.getCode(replyException.failureCode());
                        final String details = replyException.getMessage();

                        LOGGER.error(MessageCodes.BUCKETEER_005, aListener, LOGGER.getMessage(messageCode, details));
                    } else {
                        LOGGER.error(MessageCodes.BUCKETEER_005, aListener, exception.getMessage());
                    }
                } else {
                    LOGGER.error(MessageCodes.BUCKETEER_005, aListener, LOGGER.getMessage(MessageCodes.BUCKETEER_136));
                }

                // If we have a failure in processing this item, mark it as a failure
                // {@link ItemFailureVerticle} sends a message to {@link FinalizeJobVerticle}, so use that timeout
                sendMessage(myVertx, aJsonObject, ItemFailureVerticle.class.getName(),
                        Math.max(mySlackRetryDuration, DeliveryOptions.DEFAULT_TIMEOUT));
            } else if (Op.RETRY.equals(response.result().body())) {
                myVertx.setTimer(myRequeueDelay, timer -> {
                    sendImageRequest(aListener, aJsonObject);
                });
            }
        });
    }

    /**
     * Gets a feature flag checker.
     *
     * @return An optional feature flag checker
     */
    private Optional<FeatureFlagChecker> getFeatureFlagChecker() {
        if (myVertx.fileSystem().existsBlocking(Features.FEATURE_FLAGS_FILE)) {
            return Optional.of(ConfigFeatureFlagChecker.forConfigSupplier(
                    Suppliers.supplierAndThen(FileResourceLoaders.forFile(new File(Features.FEATURE_FLAGS_FILE)),
                            TypesafeConfigReader.FROM_STRING),
                    TypesafeConfigDecider.FEATURE_ENABLED));
        }
        return Optional.empty();
    }

    /**
     * Returns an error page to the browser.
     *
     * @param aResponse An HTTP response
     * @param aMessage An error message
     * @param aDetail Additional details to send along with the error message
     */
    private void returnError(final HttpServerResponse aResponse, final String aMessage, final String aDetail) {
        final String errorName = LOGGER.getMessage(MessageCodes.BUCKETEER_074);
        final String errorMessage = LOGGER.getMessage(aMessage, aDetail);

        LOGGER.error(errorMessage);

        aResponse.setStatusCode(HTTP.INTERNAL_SERVER_ERROR);
        aResponse.setStatusMessage(errorMessage);
        aResponse.putHeader(Constants.CONTENT_TYPE, Constants.HTML);
        aResponse.end(StringUtils.format(myExceptionPage, errorName + errorMessage));
    }
}
