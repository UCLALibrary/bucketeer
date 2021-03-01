
package edu.ucla.library.bucketeer.verticles;

import static edu.ucla.library.bucketeer.Constants.EMPTY;
import static edu.ucla.library.bucketeer.Constants.MESSAGES;

import com.amazonaws.regions.RegionUtils;

import info.freelibrary.util.FileUtils;
import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;
import info.freelibrary.util.StringUtils;

import info.freelibrary.vertx.s3.S3Client;
import info.freelibrary.vertx.s3.UserMetadata;

import edu.ucla.library.bucketeer.Config;
import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.HTTP;
import edu.ucla.library.bucketeer.MessageCodes;
import edu.ucla.library.bucketeer.Op;
import edu.ucla.library.bucketeer.utils.CodeUtils;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.http.ConnectionPoolTooBusyException;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.Counter;

/**
 * Stores submitted images to an S3 bucket.
 */
public class S3BucketVerticle extends AbstractBucketeerVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(S3BucketVerticle.class, MESSAGES);

    private static final int DEFAULT_S3_MAX_REQUESTS = 10;

    private static final long MAX_RETRIES = 10;

    private S3Client myS3Client;

    @Override
    public void start() throws Exception {
        super.start();

        final JsonObject config = config();
        final int s3MaxRequests = config.getInteger(Config.S3_MAX_REQUESTS, DEFAULT_S3_MAX_REQUESTS);

        if (LOGGER.isDebugEnabled()) {
            final String threadName = Thread.currentThread().getName();
            final String className = S3BucketVerticle.class.getSimpleName();

            LOGGER.debug(MessageCodes.BUCKETEER_004, className, threadName);
        }

        // Initialize the S3BucketVerticle
        if (myS3Client == null) {
            final String s3AccessKey = config.getString(Config.S3_ACCESS_KEY);
            final String s3SecretKey = config.getString(Config.S3_SECRET_KEY);
            final String s3RegionName = config.getString(Config.S3_REGION);
            final String s3Region = RegionUtils.getRegion(s3RegionName).getServiceEndpoint("s3");
            final HttpClientOptions options = new HttpClientOptions();

            // Set the S3 client options
            options.setSsl(true).setDefaultPort(443).setDefaultHost(s3Region);

            myS3Client = new S3Client(getVertx(), s3AccessKey, s3SecretKey, options);
            myS3Client.useV2Signature(true);

            // Trace is only for developer use; don't turn on when running on a server
            LOGGER.trace(MessageCodes.BUCKETEER_045, s3AccessKey, s3SecretKey);

            LOGGER.debug(MessageCodes.BUCKETEER_009, s3RegionName);
        }

        // Initialize a consumer of S3 upload requests
        getJsonConsumer().handler(message -> {
            getVertx().sharedData().getLocalCounter(Constants.S3_REQUEST_COUNT, getCounter -> {
                if (getCounter.succeeded()) {
                    getCounter.result().incrementAndGet(increment -> {
                        if (increment.succeeded()) {
                            // Check that we haven't reached our maximum number of S3 uploads
                            if (increment.result() <= s3MaxRequests) {
                                upload(message, config);
                            } else {
                                // If we have reached our maximum request count, re-queue the request
                                sendReply(message, 0, Op.RETRY);
                            }
                        } else {
                            message.fail(CodeUtils.getInt(MessageCodes.BUCKETEER_138), EMPTY);
                        }
                    });
                } else {
                    message.fail(CodeUtils.getInt(MessageCodes.BUCKETEER_139), EMPTY);
                }
            });
        });

    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }

    /**
     * Upload the requested file to S3.
     *
     * @param aMessage The message containing the S3 upload request
     * @param aConfig The verticle's configuration
     */
    @SuppressWarnings("Indentation") // Checkstyle's indentation check doesn't work with multiple lambdas
    private void upload(final Message<JsonObject> aMessage, final JsonObject aConfig) {
        final boolean deletable = aMessage.headers().contains(Constants.DERIVATIVE_IMAGE);
        final JsonObject storageRequest = aMessage.body();

        // If an S3 bucket isn't being supplied to us, use the one in our application configuration
        if (!storageRequest.containsKey(Config.S3_BUCKET)) {
            storageRequest.mergeIn(aConfig);
        }

        final String filePath = storageRequest.getString(Constants.FILE_PATH);
        final String jobName = storageRequest.getString(Constants.JOB_NAME);
        final String s3Bucket = storageRequest.getString(Config.S3_BUCKET);
        final String imageID = storageRequest.getString(Constants.IMAGE_ID);
        final String imageIDSansExt = FileUtils.stripExt(imageID);

        LOGGER.debug(MessageCodes.BUCKETEER_010, imageID, filePath, s3Bucket);

        vertx.fileSystem().open(filePath, new OpenOptions().setWrite(false), open -> {
            if (open.succeeded()) {
                final AsyncFile asyncFile = open.result();
                final UserMetadata metadata = new UserMetadata(Constants.IMAGE_ID, imageIDSansExt);

                // If there is a job name, supply it in user metadata on S3 upload
                if (StringUtils.trimToNull(jobName) != null) {
                    metadata.add(Constants.JOB_NAME, jobName);
                }

                LOGGER.debug(MessageCodes.BUCKETEER_044, imageID, filePath, s3Bucket);

                // If our connection pool is full, drop back and try resubmitting the request
                try {
                    myS3Client.put(s3Bucket, imageID, asyncFile, metadata, response -> {
                        final int statusCode = response.statusCode();

                        response.exceptionHandler(exception -> {
                            final String details = exception.getMessage();

                            LOGGER.error(exception, details);

                            closeFile(asyncFile, filePath, false);
                            sendReply(aMessage, CodeUtils.getInt(MessageCodes.BUCKETEER_500), details);
                        });

                        // If we get a successful upload response code, note this in our final results map
                        if (statusCode == HTTP.OK) {
                            LOGGER.info(MessageCodes.BUCKETEER_026, imageID);

                            vertx.sharedData().getLocalMap(Constants.RESULTS_MAP).put(imageIDSansExt, true);

                            // Close the file, send the success result, and decrement the S3 request counter
                            closeFile(asyncFile, filePath, deletable);
                            sendReply(aMessage, 0, Op.SUCCESS);
                        } else {
                            LOGGER.error(MessageCodes.BUCKETEER_014, statusCode, response.statusMessage());

                            // Log the detailed reason we failed so we can track down the issue
                            response.bodyHandler(body -> {
                                LOGGER.error(MessageCodes.BUCKETEER_500, body.getString(0, body.length()));
                            });

                            // If there is some internal S3 server error, let's try again
                            if (statusCode == HTTP.INTERNAL_SERVER_ERROR) {
                                closeFile(asyncFile, filePath, false);
                                sendReply(aMessage, 0, Op.RETRY);
                            } else {
                                final String errorMessage = statusCode + " - " + response.statusMessage();

                                LOGGER.warn(MessageCodes.BUCKETEER_156, errorMessage);
                                closeFile(asyncFile, filePath, false);
                                retryUpload(imageID, aMessage);
                            }
                        }
                    }, exception -> {
                        LOGGER.warn(MessageCodes.BUCKETEER_156, exception.getMessage());
                        closeFile(asyncFile, filePath, false);
                        retryUpload(imageID, aMessage);
                    });
                } catch (final ConnectionPoolTooBusyException details) {
                    LOGGER.debug(MessageCodes.BUCKETEER_046, imageID);
                    closeFile(asyncFile, filePath, false);
                    sendReply(aMessage, 0, Op.RETRY);
                }
            } else {
                LOGGER.error(open.cause(), LOGGER.getMessage(MessageCodes.BUCKETEER_043, filePath));
                sendReply(aMessage, CodeUtils.getInt(MessageCodes.BUCKETEER_043), filePath);
            }
        });
    }

    /**
     * A more tentative retry attempt. We count the number of times we've retried and give up after a certain point.
     *
     * @param aImageID An image ID to retry
     * @param aMessage A message to respond to with the retry request (or exception if we've failed)
     */
    private void retryUpload(final String aImageID, final Message<JsonObject> aMessage) {
        shouldRetry(aImageID, retryCheck -> {
            if (retryCheck.succeeded()) {
                if (retryCheck.result()) {
                    sendReply(aMessage, 0, Op.RETRY);
                } else {
                    sendReply(aMessage, CodeUtils.getInt(MessageCodes.BUCKETEER_140), EMPTY);
                }
            } else {
                final Throwable retryException = retryCheck.cause();
                final String details = retryException.getMessage();

                LOGGER.error(retryException, MessageCodes.BUCKETEER_134, details);

                // If we have an exception, don't retry... just log the issue
                sendReply(aMessage, CodeUtils.getInt(MessageCodes.BUCKETEER_134), details);
            }
        });
    }

    /**
     * A check to see whether an errored request should be retried.
     *
     * @param aImageID An image ID
     * @param aHandler A retry handler
     */
    private void shouldRetry(final String aImageID, final Handler<AsyncResult<Boolean>> aHandler) {
        final Promise<Boolean> promise = Promise.promise();

        promise.future().onComplete(aHandler);

        vertx.sharedData().getLocalCounter(aImageID, getCounter -> {
            if (getCounter.succeeded()) {
                final Counter counter = getCounter.result();

                counter.addAndGet(1L, get -> {
                    if (get.succeeded()) {
                        if (get.result() == MAX_RETRIES) {
                            promise.complete(Boolean.FALSE);

                            // Reset the counter in case we ever need to process this item again
                            counter.compareAndSet(MAX_RETRIES, 0L, reset -> {
                                if (reset.failed()) {
                                    LOGGER.error(MessageCodes.BUCKETEER_133, aImageID);
                                }
                            });
                        } else {
                            promise.complete(Boolean.TRUE);
                        }
                    } else {
                        promise.fail(get.cause());
                    }
                });
            } else {
                promise.fail(getCounter.cause());
            }
        });
    }

    /**
     * Closes reference to the file that should have been uploaded.
     *
     * @param aAsyncFile An asynchronous file handle
     * @param aFilePath The path to the file
     * @param aDeletableImage If the file was uploaded successfully and the image is deletable
     */
    private void closeFile(final AsyncFile aAsyncFile, final String aFilePath, final boolean aDeletableImage) {
        aAsyncFile.close(closure -> {
            if (closure.failed()) {
                LOGGER.error(closure.cause(), MessageCodes.BUCKETEER_047, aFilePath);
            }

            // If the file was uploaded successfully and it's a derivative image, delete it
            if (aDeletableImage) {
                vertx.fileSystem().delete(aFilePath, handler -> {
                    if (handler.failed()) {
                        LOGGER.error(handler.cause(), MessageCodes.BUCKETEER_161, aFilePath);
                    } else {
                        LOGGER.debug(MessageCodes.BUCKETEER_167, aFilePath);
                    }
                });
            }
        });
    }

    /**
     * Sends a message reply in the case that the S3 upload succeeded (or failed after the counter had been
     * incremented).
     *
     * @param aMessage A message requesting an S3 upload
     * @param aDetails The result of the S3 upload
     */
    private void sendReply(final Message<JsonObject> aMessage, final int aCode, final String aDetails) {
        getVertx().sharedData().getLocalCounter(Constants.S3_REQUEST_COUNT, getCounter -> {
            if (getCounter.succeeded()) {
                getCounter.result().decrementAndGet(decrement -> {
                    if (decrement.failed()) {
                        LOGGER.error(MessageCodes.BUCKETEER_093);
                    }

                    if (aCode == 0) {
                        aMessage.reply(aDetails);
                    } else {
                        aMessage.fail(aCode, aDetails);
                    }
                });
            } else {
                LOGGER.error(MessageCodes.BUCKETEER_094);

                if (aCode == 0) {
                    aMessage.reply(aDetails);
                } else {
                    aMessage.fail(aCode, aDetails);
                }
            }
        });
    }
}
