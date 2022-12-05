
package edu.ucla.library.bucketeer.verticles;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import javax.naming.ConfigurationException;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;
import info.freelibrary.util.StringUtils;

import edu.ucla.library.bucketeer.Config;
import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.Features;
import edu.ucla.library.bucketeer.HTTP;
import edu.ucla.library.bucketeer.MessageCodes;
import edu.ucla.library.bucketeer.Op;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

/**
 * A verticle that handles the processing of large images. We have a limit on the size of images that AWS Lambda can
 * handle, so we can use this to pass the image conversion off to another instance of Bucketeer that's set up to handle
 * large image conversion and upload.
 */
public class LargeImageVerticle extends AbstractBucketeerVerticle {

    /** The LargeImageVerticle logger. */
    private static final Logger LOGGER = LoggerFactory.getLogger(LargeImageVerticle.class, Constants.MESSAGES);

    /** The endpoint for loading images. */
    private static final String LOAD_IMAGE_ENDPOINT = "/images/{}/{}"; // image-id and file-path

    /** An update status callback URL. */
    private Optional<String> myCallbackURL;

    /** The URL for the large image Bucketeer. */
    private String myLargeImageBucketeer;

    @Override
    public void start() throws Exception {
        final JsonObject config = config();

        super.start();

        if (LOGGER.isDebugEnabled()) {
            final String threadName = Thread.currentThread().getName();
            final String className = S3BucketVerticle.class.getSimpleName();

            LOGGER.debug(MessageCodes.BUCKETEER_004, className, threadName);
        }

        // Get the location of the Bucketeer to which we're sending large images
        myLargeImageBucketeer = config.getString(Config.LARGE_IMAGE_URL);
        myCallbackURL = Optional.ofNullable(config.getString(Config.BATCH_CALLBACK_URL));

        // We should have a configuration value for our large image server if the feature is turned on
        if (myFeatureChecker.isPresent() && myFeatureChecker.get().isFeatureEnabled(Features.LARGE_IMAGE_ROUTING)
                && myLargeImageBucketeer == null) {
            throw new ConfigurationException(LOGGER.getMessage(MessageCodes.BUCKETEER_512));
        }

        // Initialize a consumer of large image requests
        getJsonConsumer().handler(message -> {
            final JsonObject body = message.body();
            final String jobName = URLEncoder.encode(body.getString(Constants.JOB_NAME), StandardCharsets.UTF_8);
            final String imageID = URLEncoder.encode(body.getString(Constants.IMAGE_ID), StandardCharsets.UTF_8);
            final String filePath = URLEncoder.encode(body.getString(Constants.FILE_PATH), StandardCharsets.UTF_8);
            final WebClient webClient = WebClient.create(getVertx());

            // We want to send large images to the individual image load endpoint instead of to AWS Lambda
            String loadImageEndpoint =
                    myLargeImageBucketeer + StringUtils.format(LOAD_IMAGE_ENDPOINT, imageID, filePath);

            if (myCallbackURL.isPresent()) {
                final String callbackURL = StringUtils.format(myCallbackURL.get(), jobName, imageID);
                final String encodedCallbackURL = URLEncoder.encode(callbackURL, StandardCharsets.UTF_8);

                // The callback URL has double encoded variables in the request path
                loadImageEndpoint += "?" + Constants.CALLBACK_URL + "=" + encodedCallbackURL;
            }

            LOGGER.debug(MessageCodes.BUCKETEER_513, loadImageEndpoint);

            webClient.getAbs(loadImageEndpoint).send(get -> {
                if (get.succeeded()) {
                    final HttpResponse<Buffer> response = get.result();

                    if (response.statusCode() == HTTP.CREATED) {
                        message.reply(Op.SUCCESS);
                    } else {
                        final String errorMessage = response.statusMessage();

                        message.fail(HTTP.INTERNAL_SERVER_ERROR, errorMessage);
                        LOGGER.error(errorMessage);
                    }
                } else {
                    final Throwable throwable = get.cause();
                    final String errorMessage = throwable.getMessage();

                    message.fail(HTTP.INTERNAL_SERVER_ERROR, errorMessage);
                    LOGGER.error(throwable, errorMessage);
                }

                webClient.close();
            });
        });
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }
}
