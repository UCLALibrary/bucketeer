
package edu.ucla.library.bucketeer.handlers;

import static edu.ucla.library.bucketeer.Metadata.WorkflowState.EMPTY;
import static edu.ucla.library.bucketeer.Metadata.WorkflowState.FAILED;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Set;

import com.opencsv.bean.CsvToBeanBuilder;

import info.freelibrary.util.FileUtils;
import info.freelibrary.util.IOUtils;
import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;
import info.freelibrary.util.StringUtils;

import edu.ucla.library.bucketeer.Config;
import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.CsvParsingException;
import edu.ucla.library.bucketeer.HTTP;
import edu.ucla.library.bucketeer.MessageCodes;
import edu.ucla.library.bucketeer.Metadata;
import edu.ucla.library.bucketeer.Metadata.WorkflowState;
import edu.ucla.library.bucketeer.Op;
import edu.ucla.library.bucketeer.utils.FilePathPrefixFactory;
import edu.ucla.library.bucketeer.utils.IFilePathPrefix;
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
        final String slackHandle = StringUtils.trimToNull(formAttributes.get(Constants.SLACK_HANDLE));

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
            final String failuresValue = StringUtils.trimTo(formAttributes.get(Constants.FAILURES_ONLY), "false");
            final boolean failures = Boolean.parseBoolean(failuresValue);
            final FileUpload csvFile = csvUploads.iterator().next();
            final String filePath = csvFile.uploadedFileName();
            final String fileName = csvFile.fileName();

            // Initialize our CSV reader so we can be sure to close it after we're done using it
            FileReader csvReader = null;

            LOGGER.info(MessageCodes.BUCKETEER_051, slackHandle, fileName, filePath);

            try {
                csvReader = new FileReader(filePath);

                // Get CSV metadata, which includes the IDs and file paths of the images to be processed
                final CsvToBeanBuilder<Metadata> builder = new CsvToBeanBuilder<Metadata>(csvReader);
                final List<Metadata> metadataList = builder.withType(Metadata.class).build().parse();
                final SharedData sharedData;

                for (final Metadata metadata : metadataList) {
                    metadata.setSlackHandle(slackHandle);
                }

                myVertx = aContext.vertx();
                sharedData = myVertx.sharedData();

                // Get our s3/lambda job queue and register the CSV file we've just received (and its contents)
                sharedData.<String, List<Metadata>>getLocalAsyncMap(Constants.LAMBDA_MAP, getMap -> {
                    if (getMap.succeeded()) {
                        final AsyncMap<String, List<Metadata>> map = getMap.result();

                        // Check for the pre-existence of the file name in the map (meaning a job is already running)
                        map.keys(keyCheck -> {
                            if (keyCheck.succeeded()) {
                                // If CSV file is already in the queue, let the user know to check Slack for results
                                if (keyCheck.result().contains(fileName)) {
                                    final String duplicate = LOGGER.getMessage(MessageCodes.BUCKETEER_060, fileName);

                                    LOGGER.info(duplicate);

                                    response.setStatusCode(HTTP.TOO_MANY_REQUESTS);
                                    response.setStatusMessage(duplicate);
                                    response.putHeader(Constants.CONTENT_TYPE, Constants.HTML);
                                    response.end(StringUtils.format(myExceptionPage, duplicate));
                                } else {
                                    try {
                                        checkMetadata(metadataList);
                                        processMetadata(metadataList, map, fileName, failures, response, sharedData);
                                    } catch (final CsvParsingException details) {
                                        final String statusMessage = LOGGER.getMessage(MessageCodes.BUCKETEER_072);
                                        final String delim = "<br>";

                                        response.setStatusCode(HTTP.BAD_REQUEST);
                                        response.setStatusMessage(statusMessage);
                                        response.putHeader(Constants.CONTENT_TYPE, Constants.HTML);
                                        response.end(StringUtils.format(myExceptionPage, details.getMessages(delim)));
                                    }
                                }
                            } else {
                                returnError(response, MessageCodes.BUCKETEER_062, fileName);
                            }
                        });
                    } else {
                        returnError(response, MessageCodes.BUCKETEER_063, fileName);
                    }
                });
            } catch (final FileNotFoundException details) {
                final String exceptionMessage = LOGGER.getMessage(MessageCodes.BUCKETEER_073);
                final String exceptionName = LOGGER.getMessage(MessageCodes.BUCKETEER_074);

                LOGGER.error(details, details.getMessage());

                response.setStatusCode(HTTP.INTERNAL_SERVER_ERROR);
                response.putHeader(Constants.CONTENT_TYPE, Constants.HTML);
                response.end(StringUtils.format(myExceptionPage, exceptionName + exceptionMessage));
            } finally {
                IOUtils.closeQuietly(csvReader);
            }
        }
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

    private void addMetadataToJobsQueue(final String aJobName, final List<Metadata> aMetadataList,
            final Counter aCounter, final boolean aFailuresRun) {
        final String fsMount = myConfig.getString(Config.FILESYSTEM_MOUNT);
        final String fsPrefix = myConfig.getString(Config.FILESYSTEM_PREFIX);
        final String s3Bucket = myConfig.getString(Config.LAMBDA_S3_BUCKET);
        final IFilePathPrefix filePathPrefix = FilePathPrefixFactory.getPrefix(fsPrefix, fsMount);

        // Cycle through our metadata, add each to our monitoring queue, then send off
        for (final Metadata metadata : aMetadataList) {
            final WorkflowState state = metadata.getWorkflowState();
            final String infoMessage;

            // Set the file prefix so file paths can be checked against the file system
            metadata.setFilePathPrefix(filePathPrefix);

            if (!metadata.isValid()) {
                final String values = metadata.toString();
                final String id = metadata.getID();

                if (id == null) {
                    infoMessage = LOGGER.getMessage(MessageCodes.BUCKETEER_061, values);
                } else if (metadata.isWork()) {
                    infoMessage = LOGGER.getMessage(MessageCodes.BUCKETEER_054, id);
                } else if (metadata.isCollection()) {
                    infoMessage = LOGGER.getMessage(MessageCodes.BUCKETEER_053, id);
                } else {
                    infoMessage = LOGGER.getMessage(MessageCodes.BUCKETEER_052, values);
                }

                // We're not going to treat this as an error, but will log as possibly interesting
                LOGGER.info(infoMessage);
                metadata.setWorkflowState(FAILED);
            } else {
                final JsonObject s3UploadMessage = new JsonObject();
                final File imageFile = metadata.getFile();
                final String extension = "." + FileUtils.getExt(imageFile.getName());

                // For normal runs only process empty states and for failure runs only processes failures
                if ((!aFailuresRun && state.equals(EMPTY)) || (aFailuresRun && state.equals(FAILED))) {
                    s3UploadMessage.put(Constants.IMAGE_ID, metadata.getID() + extension);
                    s3UploadMessage.put(Constants.FILE_PATH, imageFile.getAbsolutePath());
                    s3UploadMessage.put(Constants.JOB_NAME, aJobName);
                    s3UploadMessage.put(Config.S3_BUCKET, s3Bucket);

                    sendMessage(s3UploadMessage, S3BucketVerticle.class.getName(), Integer.MAX_VALUE);
                }
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

    private void checkMetadata(final List<Metadata> aMetadataList) throws CsvParsingException {
        CsvParsingException exception = null;

        // Check that none of our metadata has an invalid workflow state
        for (final Metadata metadata : aMetadataList) {
            if (!metadata.hasValidWorkflowState()) {
                if (exception == null) {
                    exception = new CsvParsingException();
                }

                exception.addMessage(MessageCodes.BUCKETEER_070, metadata.getID(), metadata.toString());
            }
        }

        // Don't proceed if one of our CSV metadata records has an invalid workflow state
        if (exception != null) {
            throw exception;
        }
    }

    private void processMetadata(final List<Metadata> aMetadataList, final AsyncMap<String, List<Metadata>> aAsyncMap,
            final String aCsvFileName, final boolean aFailuresRun, final HttpServerResponse aResponse,
            final SharedData aSharedData) {
        final String jobName = FileUtils.stripExt(aCsvFileName);

        // Put our metadata into the jobs queue map using the CSV file name as the key
        aAsyncMap.put(jobName, aMetadataList, put -> {
            if (put.succeeded()) {
                final String statusMessage = LOGGER.getMessage(MessageCodes.BUCKETEER_064);

                // Set a shared counter to the number of records in the metadata list
                aSharedData.getCounter(jobName, getCounter -> {
                    if (getCounter.succeeded()) {
                        final Counter counter = getCounter.result();

                        counter.compareAndSet(0, aMetadataList.size(), compareAndSet -> {
                            if (compareAndSet.succeeded()) {
                                LOGGER.info(statusMessage);

                                // Don't fail the submission on bad metadata; we'll note it in output CSV instead
                                aResponse.setStatusCode(HTTP.OK);
                                aResponse.putHeader(Constants.CONTENT_TYPE, Constants.HTML);
                                aResponse.end(StringUtils.format(mySuccessPage, statusMessage));

                                addMetadataToJobsQueue(jobName, aMetadataList, counter, aFailuresRun);
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

}
