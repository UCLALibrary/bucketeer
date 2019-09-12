
package edu.ucla.library.bucketeer.verticles;

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
import io.vertx.core.Vertx;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.http.ConnectionPoolTooBusyException;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonObject;

/**
 * Stores submitted images to an S3 bucket.
 */
public class S3BucketVerticle extends AbstractBucketeerVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(S3BucketVerticle.class, MESSAGES);

    private static final int DEFAULT_MAX_WAIT_QUEUE_SIZE = 10;

    private S3Client myS3Client;

    @Override
    public void start() throws Exception {
        super.start();

        final JsonObject config = config();
        final Vertx vertx = getVertx();

        if (LOGGER.isDebugEnabled()) {
            final String threadName = Thread.currentThread().getName();
            final String className = S3BucketVerticle.class.getSimpleName();

            LOGGER.debug(MessageCodes.BUCKETEER_004, className, threadName);
        }

        if (myS3Client == null) {
            final String s3AccessKey = config.getString(Config.S3_ACCESS_KEY);
            final String s3SecretKey = config.getString(Config.S3_SECRET_KEY);
            final String s3RegionName = config.getString(Config.S3_REGION);
            final int maxWaitQueueSize = config.getInteger(Config.MAX_WAIT_QUEUE_SIZE, DEFAULT_MAX_WAIT_QUEUE_SIZE);
            final String s3Region = RegionUtils.getRegion(s3RegionName).getServiceEndpoint("s3");
            final HttpClientOptions options = new HttpClientOptions();

            options.setMaxWaitQueueSize(maxWaitQueueSize).setDefaultHost(s3Region);

            myS3Client = new S3Client(getVertx(), s3AccessKey, s3SecretKey, options);

            // Trace is only for developer use; don't turn on when running on a server
            LOGGER.trace(MessageCodes.BUCKETEER_045, s3AccessKey, s3SecretKey);

            LOGGER.debug(MessageCodes.BUCKETEER_009, s3RegionName);
        }

        getJsonConsumer().handler(message -> {
            final JsonObject storageRequest = message.body();

            // If an S3 bucket isn't being supplied to us, use the one in our application configuration
            if (!storageRequest.containsKey(Config.S3_BUCKET)) {
                storageRequest.mergeIn(config);
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

                            // If we get a successful upload response code, note this in our results map
                            if (statusCode == HTTP.OK) {
                                vertx.sharedData().getLocalMap(Constants.RESULTS_MAP).put(imageIDSansExt, true);

                                LOGGER.debug(MessageCodes.BUCKETEER_026, imageID);
                                message.reply(Op.SUCCESS);
                            } else {
                                response.bodyHandler(body -> {
                                    final String xmlMessage = body.getString(0, body.length());

                                    LOGGER.error(MessageCodes.BUCKETEER_014, statusCode, response.statusMessage());
                                    LOGGER.error(MessageCodes.BUCKETEER_000, xmlMessage);

                                    message.reply(Op.FAILURE);
                                });
                            }

                            asyncFile.close();
                        });
                    } catch (final ConnectionPoolTooBusyException details) {
                        asyncFile.close(closure -> {
                            if (closure.failed()) {
                                LOGGER.error(closure.cause(), MessageCodes.BUCKETEER_047, filePath);
                            }
                        });

                        LOGGER.debug(MessageCodes.BUCKETEER_046, imageID);
                        message.reply(Op.RETRY);
                    }
                } else {
                    LOGGER.error(open.cause(), LOGGER.getMessage(MessageCodes.BUCKETEER_043, filePath));
                    message.reply(Op.FAILURE);
                }
            });

        });

    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }

}
