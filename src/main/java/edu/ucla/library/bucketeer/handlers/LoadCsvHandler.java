
package edu.ucla.library.bucketeer.handlers;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.Set;

import info.freelibrary.util.FileUtils;
import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;
import info.freelibrary.util.StringUtils;

import edu.ucla.library.bucketeer.Config;
import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.CsvParsingException;
import edu.ucla.library.bucketeer.HTTP;
import edu.ucla.library.bucketeer.Item;
import edu.ucla.library.bucketeer.Job;
import edu.ucla.library.bucketeer.Job.WorkflowState;
import edu.ucla.library.bucketeer.MessageCodes;
import edu.ucla.library.bucketeer.Op;
import edu.ucla.library.bucketeer.utils.JobFactory;
import edu.ucla.library.bucketeer.verticles.S3BucketVerticle;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.Counter;
import io.vertx.core.shareddata.SharedData;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RoutingContext;

public class LoadCsvHandler implements Handler<RoutingContext> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoadCsvHandler.class, Constants.MESSAGES);

    /* Default delay for requeuing, measured in seconds */
    private static final long DEFAULT_REQUEUE_DELAY = 1;

    private final JsonObject myConfig;

    private Vertx myVertx;

    private final String myExceptionPage;

    private final String mySuccessPage;

    private final long myRequeueDelay;

    /**
     * Creates a handler to ingest CSV files.
     *
     * @param aConfig An application configuration
     */
    public LoadCsvHandler(final JsonObject aConfig) throws IOException {
        final StringBuilder templateBuilder = new StringBuilder();

        // Load a template used for returning the error page
        InputStream templateStream = getClass().getResourceAsStream("/webroot/error.html");
        BufferedReader templateReader = new BufferedReader(new InputStreamReader(templateStream));
        String line;

        while ((line = templateReader.readLine()) != null) {
            templateBuilder.append(line);
        }

        templateReader.close();
        myExceptionPage = templateBuilder.toString();

        // Load a template used for returning the success page
        templateBuilder.delete(0, templateBuilder.length());
        templateStream = getClass().getResourceAsStream("/webroot/success.html");
        templateReader = new BufferedReader(new InputStreamReader(templateStream));

        while ((line = templateReader.readLine()) != null) {
            templateBuilder.append(line);
        }

        templateReader.close();
        mySuccessPage = templateBuilder.toString();
        myConfig = aConfig;
        myRequeueDelay = myConfig.getLong(Config.S3_REQUEUE_DELAY, DEFAULT_REQUEUE_DELAY) * 1000;
    }

    @Override
    public void handle(final RoutingContext aContext) {
        final HttpServerResponse response = aContext.response();
        final HttpServerRequest request = aContext.request();
        final MultiMap formAttributes = request.formAttributes();
        final Set<FileUpload> csvUploads = aContext.fileUploads();
        final String slackHandle = StringUtils.trimToNull(formAttributes.get(Constants.SLACK_HANDLE)).replace(
                Constants.AT, Constants.EMPTY_STRING);

        if (slackHandle == null) {
            final String errorMessage = LOGGER.getMessage(MessageCodes.BUCKETEER_050);
            final String extendedErrorMessage = LOGGER.getMessage(MessageCodes.BUCKETEER_071);

            // Slack handle is required -- don't accept the form without it being supplied
            response.setStatusCode(HTTP.BAD_REQUEST);
            response.setStatusMessage(errorMessage);
            response.putHeader(Constants.CONTENT_TYPE, Constants.HTML);
            response.end(StringUtils.format(myExceptionPage, errorMessage + extendedErrorMessage));
        } else if (csvUploads.size() == 0) {
            final String errorMessage = LOGGER.getMessage(MessageCodes.BUCKETEER_049);
            final String extendedErrorMessage = LOGGER.getMessage(MessageCodes.BUCKETEER_071);

            // An uploaded CSV file is required -- don't accept the form without it being supplied
            response.setStatusCode(HTTP.BAD_REQUEST);
            response.setStatusMessage(errorMessage);
            response.putHeader(Constants.CONTENT_TYPE, Constants.HTML);
            response.end(StringUtils.format(myExceptionPage, errorMessage + extendedErrorMessage));
        } else {
            final String runFailures = StringUtils.trimTo(formAttributes.get(Constants.FAILURES_ONLY), "false");
            final FileUpload csvFile = csvUploads.iterator().next();
            final String filePath = csvFile.uploadedFileName();
            final String fileName = csvFile.fileName();
            final String jobName = FileUtils.stripExt(fileName);

            LOGGER.info(MessageCodes.BUCKETEER_051, slackHandle, fileName, filePath);

            if (myVertx == null) {
                myVertx = aContext.vertx();
            }

            try {
                final Job job = JobFactory.getInstance().createJob(jobName, new File(filePath));

                // Set the Slack handle and whether the job is a subsequent or initial run
                job.setSlackHandle(slackHandle).setIsSubsequentRun(Boolean.parseBoolean(runFailures));

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
            } catch (final CsvParsingException details) {
                final String statusMessage = LOGGER.getMessage(MessageCodes.BUCKETEER_072);

                response.setStatusCode(HTTP.BAD_REQUEST);
                response.setStatusMessage(statusMessage);
                response.putHeader(Constants.CONTENT_TYPE, Constants.HTML);
                response.end(StringUtils.format(myExceptionPage, details.getMessages("<br>")));
            }
        }
    }

    private void initiateJob(final Job aJob, final HttpServerResponse aResponse) {
        final SharedData sharedData = myVertx.sharedData();
        final String jobName = aJob.getName();

        // Get our s3/lambda job queue and register the CSV file we've just received (and its contents)
        sharedData.<String, Job>getLocalAsyncMap(Constants.LAMBDA_JOBS, getMap -> {
            if (getMap.succeeded()) {
                final AsyncMap<String, Job> map = getMap.result();

                // Check for the pre-existence of the file name in the map (meaning a job is already running)
                map.keys(keyCheck -> {
                    if (keyCheck.succeeded()) {
                        // If CSV file is already in the queue, let the user know to check Slack for results
                        if (keyCheck.result().contains(jobName)) {
                            final String duplicate = LOGGER.getMessage(MessageCodes.BUCKETEER_060, jobName);

                            LOGGER.info(duplicate);

                            aResponse.setStatusCode(HTTP.TOO_MANY_REQUESTS);
                            aResponse.setStatusMessage(duplicate);
                            aResponse.putHeader(Constants.CONTENT_TYPE, Constants.HTML);
                            aResponse.end(StringUtils.format(myExceptionPage, duplicate));
                        } else {
                            registerJob(aJob, map, aResponse);
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

    private void registerJob(final Job aJob, final AsyncMap<String, Job> aAsyncMap,
            final HttpServerResponse aResponse) {
        final SharedData sharedData = myVertx.sharedData();
        final String jobName = aJob.getName();

        // Put our metadata into the jobs queue map using the CSV file name as the key
        aAsyncMap.put(jobName, aJob, put -> {
            if (put.succeeded()) {
                final String statusMessage = LOGGER.getMessage(MessageCodes.BUCKETEER_064);

                // Set a shared counter to the number of records in the metadata list
                sharedData.getCounter(jobName, getCounter -> {
                    if (getCounter.succeeded()) {
                        final Counter counter = getCounter.result();

                        counter.compareAndSet(0, aJob.remaining(), compareAndSet -> {
                            if (compareAndSet.succeeded()) {
                                LOGGER.info(statusMessage);

                                // Don't fail the submission on bad metadata; we'll note it in output CSV instead
                                aResponse.setStatusCode(HTTP.OK);
                                aResponse.putHeader(Constants.CONTENT_TYPE, Constants.HTML);
                                aResponse.end(StringUtils.format(mySuccessPage, statusMessage));

                                updateJobsQueue(aJob, counter);
                            } else {
                                returnError(aResponse, MessageCodes.BUCKETEER_067, jobName);
                            }
                        });
                    } else {
                        returnError(aResponse, MessageCodes.BUCKETEER_066, jobName);
                    }
                });
            } else {
                returnError(aResponse, MessageCodes.BUCKETEER_068, jobName);
            }
        });
    }

    private void updateJobsQueue(final Job aJob, final Counter aCounter) {
        final String s3Bucket = myConfig.getString(Config.LAMBDA_S3_BUCKET);
        final Iterator<Item> iterator = aJob.getItems().iterator();

        while (iterator.hasNext()) {
            final Item item = iterator.next();
            final File imageFile = item.getFile();
            final WorkflowState state = item.getWorkflowState();
            final JsonObject s3UploadMessage = new JsonObject();
            final boolean isOkayInitialRun = !aJob.getIsSubsequentRun() && state.equals(Job.WorkflowState.EMPTY);
            final boolean isOkaySubsequentRun = aJob.getIsSubsequentRun() && state.equals(Job.WorkflowState.FAILED);

            // For normal runs only process empty states and for failure runs only processes failures
            if (item.hasFile() && (isOkayInitialRun || isOkaySubsequentRun)) {
                s3UploadMessage.put(Constants.IMAGE_ID, item.getID() + "." + FileUtils.getExt(imageFile.getName()));
                s3UploadMessage.put(Constants.FILE_PATH, imageFile.getAbsolutePath());
                s3UploadMessage.put(Constants.JOB_NAME, aJob.getName());
                s3UploadMessage.put(Config.S3_BUCKET, s3Bucket);

                sendMessage(s3UploadMessage, S3BucketVerticle.class.getName(), Integer.MAX_VALUE);
            } else {
                final String filePath = imageFile == null ? "null" : imageFile.getAbsolutePath();

                LOGGER.debug(MessageCodes.BUCKETEER_054, item.getID(), filePath, state);
            }
        }
    }

    private void sendMessage(final JsonObject aJsonObject, final String aVerticleName, final long aTimeout) {
        final DeliveryOptions options = new DeliveryOptions().setSendTimeout(aTimeout);

        myVertx.eventBus().send(aVerticleName, aJsonObject, options, response -> {
            if (response.failed()) {
                if (response.cause() != null) {
                    LOGGER.error(response.cause(), MessageCodes.BUCKETEER_005, aVerticleName, aJsonObject);
                } else {
                    LOGGER.error(MessageCodes.BUCKETEER_005, aVerticleName, aJsonObject);
                }
            } else if (response.result().body().equals(Op.RETRY)) {
                myVertx.setTimer(myRequeueDelay, timer -> {
                    sendMessage(aJsonObject, aVerticleName, aTimeout);
                });
            }
        });
    }

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
