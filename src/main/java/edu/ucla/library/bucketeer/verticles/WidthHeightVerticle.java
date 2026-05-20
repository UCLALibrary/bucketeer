
package edu.ucla.library.bucketeer.verticles;

import static edu.ucla.library.bucketeer.Constants.HEIGHT;
import static edu.ucla.library.bucketeer.Constants.WIDTH;

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

import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;

/**
 * A verticle that gets the width and height from an image.
 */
public class WidthHeightVerticle extends AbstractBucketeerVerticle {

    /** This verticle's logger. */
    private static final Logger LOGGER = LoggerFactory.getLogger(WidthHeightVerticle.class, Constants.MESSAGES);

    @Override
    public void start(final Promise<Void> aPromise) throws Exception {
        super.start();

        getJsonConsumer().handler(message -> {
            final JsonObject body = message.body();
            final Path path = Paths.get(body.getString(Constants.FILE_PATH));

            System.out.println(path.toAbsolutePath());

            try (final ImageInputStream in = ImageIO.createImageInputStream(path.toFile())) {
                final Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
                final ImageReader reader;
                final JsonObject reply;

                if (!readers.hasNext()) {
                    throw new IOException("No ImageReader available for TIFF: " + path);
                }

                // If we found a reader for the type of image file we have, proceed
                reader = readers.next();

                try {
                    reader.setInput(in, true, true);
                    reply = new JsonObject().put(Constants.WIDTH, Integer.toString(reader.getWidth(0)))
                            .put(Constants.HEIGHT, Integer.toString(reader.getHeight(0)));

                    message.reply(reply);
                } finally {
                    reader.dispose();
                }
            } catch (final IOException | IllegalArgumentException details) {
                // If the file is bad, no reader will be found and an IllegalArgumentException will be thrown
                message.fail(500, details.getMessage());
            }
        });

        aPromise.complete();
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }
}
