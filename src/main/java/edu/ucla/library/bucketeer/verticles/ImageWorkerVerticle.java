
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
import edu.ucla.library.bucketeer.converters.KakaduConverter;
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
            final Converter converter = ConverterFactory.getConverter(KakaduConverter.class);

            LOGGER.debug(MessageCodes.BUCKETEER_024, imageID, json.getString(Constants.FILE_PATH));

            try {
                final File jpx = converter.convert(imageID, tiffFile, Conversion.LOSSLESS);
                final JsonObject message = new JsonObject();

                message.put(Constants.FILE_PATH, jpx.getAbsolutePath());
                message.put(Constants.IMAGE_ID, jpx.getName());

                sendMessage(message, S3BucketVerticle.class.getName(), Integer.MAX_VALUE);
            } catch (final Exception details) {
                final String message = details.getMessage() != null ? details.getMessage() : LOGGER.getMessage(
                        MessageCodes.BUCKETEER_030);

                LOGGER.error(details, MessageCodes.BUCKETEER_006, message);
            }
        });
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }

}
