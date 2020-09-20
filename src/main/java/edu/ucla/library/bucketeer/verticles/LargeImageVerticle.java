
package edu.ucla.library.bucketeer.verticles;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import javax.naming.ConfigurationException;

import com.nike.moirai.ConfigFeatureFlagChecker;
import com.nike.moirai.FeatureFlagChecker;
import com.nike.moirai.Suppliers;
import com.nike.moirai.resource.FileResourceLoaders;
import com.nike.moirai.typesafeconfig.TypesafeConfigDecider;
import com.nike.moirai.typesafeconfig.TypesafeConfigReader;

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

    private static final Logger LOGGER = LoggerFactory.getLogger(LargeImageVerticle.class, Constants.MESSAGES);

    private static final String LARGE_IMAGE_ENDPOINT = "/images/{}/{}"; // image-id and file-path

    private static final String BATCH_UPDATE_ENDPOINT = "/batch/jobs/{}/{}/true"; // job-name and image-id

    private String myLargeImageServer;

    @Override
    public void start() throws Exception {
        final Optional<FeatureFlagChecker> featureChecker = getFeatureFlagChecker();

        super.start();

        if (LOGGER.isDebugEnabled()) {
            final String threadName = Thread.currentThread().getName();
            final String className = S3BucketVerticle.class.getSimpleName();

            LOGGER.debug(MessageCodes.BUCKETEER_004, className, threadName);
        }

        // Get the location to which we're sending large images for processing
        myLargeImageServer = config().getString(Config.LARGE_IMAGE_URL);

        // We should have a configuration value for our large image server if the feature is turned on
        if (featureChecker.isPresent() && featureChecker.get().isFeatureEnabled(Features.LARGE_IMAGE_ROUTING) &&
                myLargeImageServer == null) {
            throw new ConfigurationException(LOGGER.getMessage(MessageCodes.BUCKETEER_512));
        }

        // Initialize a consumer of large image requests
        getJsonConsumer().handler(message -> {
            final JsonObject body = message.body();
            final String imageID = URLEncoder.encode(body.getString(Constants.IMAGE_ID), StandardCharsets.UTF_8);
            final String filePath = URLEncoder.encode(body.getString(Constants.FILE_PATH), StandardCharsets.UTF_8);
            final int port = config().getInteger(Config.HTTP_PORT, MainVerticle.DEFAULT_PORT);
            final String endpoint = myLargeImageServer + StringUtils.format(LARGE_IMAGE_ENDPOINT, imageID, filePath);
            final WebClient webClient = WebClient.create(getVertx());

            LOGGER.debug(MessageCodes.BUCKETEER_513, endpoint);

            webClient.getAbs(endpoint).send(get -> {
                if (get.succeeded()) {
                    final HttpResponse<Buffer> response = get.result();

                    if (response.statusCode() == HTTP.CREATED) {
                        // Moving handler logic to verticles would be a good goal for one of our refactor sprints;
                        // this way we could go through the message bus instead of through the API endpoint
                        webClient.get(port, "0.0.0.0", BATCH_UPDATE_ENDPOINT).send(update -> {
                            if (update.succeeded()) {
                                message.reply(Op.SUCCESS);
                            } else {
                                final Throwable throwable = update.cause();
                                final String errorMessage = throwable.getMessage();

                                message.fail(HTTP.INTERNAL_SERVER_ERROR, errorMessage);
                                LOGGER.error(throwable, errorMessage);
                            }

                            webClient.close();
                        });
                    } else {
                        final String errorMessage = response.statusMessage();

                        message.fail(HTTP.INTERNAL_SERVER_ERROR, errorMessage);
                        LOGGER.error(errorMessage);
                        webClient.close();
                    }
                } else {
                    final Throwable throwable = get.cause();
                    final String errorMessage = throwable.getMessage();

                    message.fail(HTTP.INTERNAL_SERVER_ERROR, errorMessage);
                    LOGGER.error(throwable, errorMessage);
                    webClient.close();
                }
            });
        });
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }

    /**
     * Gets a feature flag checker.
     *
     * @return An optional feature flag checker
     */
    private Optional<FeatureFlagChecker> getFeatureFlagChecker() {
        if (vertx.fileSystem().existsBlocking(Features.FEATURE_FLAGS_FILE)) {
            return Optional.of(ConfigFeatureFlagChecker.forConfigSupplier(
                    Suppliers.supplierAndThen(FileResourceLoaders.forFile(new File(Features.FEATURE_FLAGS_FILE)),
                            TypesafeConfigReader.FROM_STRING),
                    TypesafeConfigDecider.FEATURE_ENABLED));
        } else {
            return Optional.empty();
        }
    }
}
