
package edu.ucla.library.bucketeer.verticles;

import static edu.ucla.library.bucketeer.Constants.MESSAGES;

import java.io.File;
import java.io.IOException;

import javax.naming.ConfigurationException;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.MessageCodes;
import edu.ucla.library.bucketeer.converters.Conversion;
import edu.ucla.library.bucketeer.converters.Converter;
import edu.ucla.library.bucketeer.converters.ConverterFactory;
import io.vertx.core.json.JsonObject;

public class ImageWorkerVerticle extends AbstractBucketeerVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImageWorkerVerticle.class, MESSAGES);

    @Override
    public void start() throws ConfigurationException, IOException {
        if (LOGGER.isDebugEnabled()) {
            final String className = ImageWorkerVerticle.class.getSimpleName();
            final String threadName = Thread.currentThread().getName();

            LOGGER.debug(MessageCodes.BUCKETEER_004, className, threadName);
        }

        getJsonConsumer().handler(aMessage -> {
            final JsonObject json = aMessage.body();
            final File tiffFile = new File(json.getString(Constants.FILE_PATH));
            final String imageID = json.getString(Constants.IMAGE_ID);
            final Converter converter = ConverterFactory.getConverter();

            try {
                final File jp2 = converter.convert(imageID, tiffFile, Conversion.LOSSLESS);
                final JsonObject message = new JsonObject().put(Constants.FILE_PATH, jp2.getAbsolutePath());

                // FIXME: Sending request and not waiting for response; let's get response and check for fail
                sendMessage(message, S3BucketVerticle.class.getName());
            } catch (final Exception details) {
                LOGGER.error(details, MessageCodes.BUCKETEER_006, details.getMessage());

                // Reply to Samvera callback
            }
        });
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }

}
