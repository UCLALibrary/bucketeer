
package edu.ucla.library.bucketeer.verticles;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.bucketeer.Config;
import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.MessageCodes;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;

public class LargeImageVerticle extends AbstractBucketeerVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(LargeImageVerticle.class, Constants.MESSAGES);

    @Override
    public void start() throws Exception {
        super.start();

        if (LOGGER.isDebugEnabled()) {
            final String threadName = Thread.currentThread().getName();
            final String className = S3BucketVerticle.class.getSimpleName();

            LOGGER.debug(MessageCodes.BUCKETEER_004, className, threadName);
        }

        // Initialize a consumer of large image requests
        getJsonConsumer().handler(message -> {
            final JsonObject body = message.body();
            final String jobName = body.getString(Constants.JOB_NAME);
            final String s3Bucket = body.getString(Config.S3_BUCKET);

            final String imageID = body.getString(Constants.IMAGE_ID);
            final String filePath = body.getString(Constants.FILE_PATH);

            final WebClient webClient = WebClient.create(getVertx());
            // TODO in IIIF-929 -> send the request and get a response; update the job queue based on that result

        });
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }

}
