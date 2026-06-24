
package edu.ucla.library.bucketeer.verticles;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.MessageCodes;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;

/**
 * A verticle that gets the width and height from an image.
 */
public class WidthHeightVerticle extends AbstractBucketeerVerticle {

    /** This verticle's logger. */
    private static final Logger LOGGER = LoggerFactory.getLogger(WidthHeightVerticle.class, Constants.MESSAGES);

    @Override
    public void start(final Promise<Void> aStart) throws Exception {
        super.start();

        getJsonConsumer().handler(message -> {
            final JsonObject body = message.body();
            final Path path = Paths.get(body.getString(Constants.FILE_PATH));

            vertx.executeBlocking(new Handler<Promise<JsonObject>>() {

                @Override
                public void handle(final Promise<JsonObject> aPromise) {
                    try (ImageInputStream inStream = ImageIO.createImageInputStream(path.toFile())) {
                        if (inStream == null) {
                            throw new IOException(LOGGER.getMessage(MessageCodes.BUCKETEER_615, path));
                        }

                        final Iterator<ImageReader> readers = ImageIO.getImageReaders(inStream);
                        if (!readers.hasNext()) {
                            throw new IOException(LOGGER.getMessage(MessageCodes.BUCKETEER_613, path));
                        }

                        final ImageReader reader = readers.next();
                        try {
                            reader.setInput(inStream, true, true);
                            aPromise.complete(new JsonObject() // Prepare and send response message
                                    .put(Constants.WIDTH, Integer.toString(reader.getWidth(0)))
                                    .put(Constants.HEIGHT, Integer.toString(reader.getHeight(0))));
                        } finally {
                            reader.dispose();
                        }
                    } catch (final IOException | IllegalArgumentException details) {
                        aPromise.fail(details);
                    }
                }
            }, new Handler<AsyncResult<JsonObject>>() {

                @Override
                public void handle(final AsyncResult<JsonObject> aResult) {
                    if (aResult.succeeded()) {
                        message.reply(aResult.result());
                    } else {
                        final Throwable cause = aResult.cause();

                        LOGGER.error(cause, cause.getMessage());
                        message.fail(500, cause.getMessage() != null ? cause.getMessage() : cause.toString());
                    }
                }
            });
        });

        aStart.complete();
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }
}
