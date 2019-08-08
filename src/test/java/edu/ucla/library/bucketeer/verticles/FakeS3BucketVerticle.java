
package edu.ucla.library.bucketeer.verticles;

import static edu.ucla.library.bucketeer.Constants.MESSAGES;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.bucketeer.MessageCodes;
import edu.ucla.library.bucketeer.Op;
import io.vertx.core.json.JsonObject;

public class FakeS3BucketVerticle extends AbstractBucketeerVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(FakeS3BucketVerticle.class, MESSAGES);

    @Override
    public void start() throws Exception {
        vertx.eventBus().consumer(S3BucketVerticle.class.getName()).handler(message -> {
            // All messages passed to the S3BucketVerticle _should_ be JsonObjects
            LOGGER.debug(MessageCodes.BUCKETEER_084, ((JsonObject) message.body()).encodePrettily());
            message.reply(Op.SUCCESS);
        });
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }
}
