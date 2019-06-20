
package edu.ucla.library.bucketeer.verticles;

import static edu.ucla.library.bucketeer.Constants.MESSAGES;

import java.io.IOException;

import javax.naming.ConfigurationException;

import com.amazonaws.regions.RegionUtils;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;
import info.freelibrary.vertx.s3.S3Client;

import edu.ucla.library.bucketeer.Config;
import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.HTTP;
import edu.ucla.library.bucketeer.MessageCodes;
import edu.ucla.library.bucketeer.Op;
import io.vertx.core.Vertx;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.json.JsonObject;

/**
 * Stores submitted images to an S3 bucket.
 */
public class S3BucketVerticle extends AbstractBucketeerVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(S3BucketVerticle.class, MESSAGES);

    private S3Client myS3Client;

    @Override
    public void start() throws ConfigurationException, IOException {
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
            final String s3Region = RegionUtils.getRegion(s3RegionName).getServiceEndpoint("s3");

            myS3Client = new S3Client(getVertx(), s3AccessKey, s3SecretKey, s3Region);

            // Trace is only for developer use; don't turn on when running on a server
            LOGGER.trace(MessageCodes.BUCKETEER_045, s3AccessKey, s3SecretKey);
            LOGGER.debug(MessageCodes.BUCKETEER_009, s3RegionName);
        }

        getJsonConsumer().handler(message -> {
            final JsonObject storageRequest = message.body().mergeIn(config);
            final String imageID = storageRequest.getString(Constants.IMAGE_ID);
            final String jpxPath = storageRequest.getString(Constants.FILE_PATH);
            final String s3Bucket = storageRequest.getString(Config.S3_BUCKET);

            LOGGER.debug(MessageCodes.BUCKETEER_010, imageID, jpxPath, s3Bucket);

            vertx.fileSystem().open(jpxPath, new OpenOptions().setRead(true), open -> {
                if (open.succeeded()) {
                    final AsyncFile asyncFile = open.result();

                    LOGGER.debug(MessageCodes.BUCKETEER_044, imageID, jpxPath, s3Bucket);

                    myS3Client.put(s3Bucket, imageID, asyncFile, response -> {
                        final int statusCode = response.statusCode();

                        // If we get a successful upload response code, note this in our results map
                        if (statusCode == HTTP.OK) {
                            vertx.sharedData().getLocalMap(Constants.RESULTS_MAP).put(imageID, true);
                            LOGGER.debug(MessageCodes.BUCKETEER_026, imageID);
                            message.reply(Op.SUCCESS);
                        } else {
                            LOGGER.error(MessageCodes.BUCKETEER_014, statusCode, response.statusMessage());
                            message.reply(Op.FAILURE);
                        }

                        asyncFile.close();
                    });
                } else {
                    LOGGER.error(open.cause(), LOGGER.getMessage(MessageCodes.BUCKETEER_043, jpxPath));
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
