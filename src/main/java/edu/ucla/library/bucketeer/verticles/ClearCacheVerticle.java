
package edu.ucla.library.bucketeer.verticles;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import javax.net.ssl.SSLHandshakeException;

import info.freelibrary.util.HTTP;
import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.bucketeer.Config;
import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.MessageCodes;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;

/**
 * A verticle to clear cantaloupe cache
 */
public class ClearCacheVerticle extends AbstractVerticle {

    /** This verticle's logger. */
    private static final Logger LOGGER = LoggerFactory.getLogger(ClearCacheVerticle.class, Constants.MESSAGES);

    /** The Cantaloupe API username. */
    private String myUsername;

    /** The Cantaloupe API password. */
    private String myPassword;

    /**
     * Clears cantaloupe cache and sends message if failure
     */
    @Override
    @SuppressWarnings("PMD.CognitiveComplexity")
    public void start(final Promise<Void> aPromise) throws Exception {
        super.start();

        final WebClient client = WebClient.create(Vertx.vertx());
        final JsonObject config = config();
        final String iiifURL = config.getString(Config.IIIF_URL);

        myUsername = config.getString(Config.IIIF_CACHE_USER);
        myPassword = config.getString(Config.IIIF_CACHE_PASSWORD);

        // Verify credentials are valid
        if (myUsername == null || myPassword == null) {
            aPromise.fail(LOGGER.getMessage(MessageCodes.BUCKETEER_603));
        } else {
            // We hit an endpoint that's access restricted to test/confirm our configured username and password
            client.getAbs(iiifURL + "/configuration").basicAuthentication(myUsername, myPassword).send(get -> {
                final String statusMessage;
                final String error;
                final int status;

                if (get.failed()) {
                    final Throwable cause = get.cause();

                    if (hasCause(cause, SSLHandshakeException.class)) {
                        error = LOGGER.getMessage(MessageCodes.BUCKETEER_616, iiifURL);

                        LOGGER.error(error, cause);
                        aPromise.fail(error);

                        return;
                    }

                    if (hasCause(cause, UnknownHostException.class)) {
                        error = LOGGER.getMessage(MessageCodes.BUCKETEER_617, iiifURL);

                        LOGGER.error(error, cause);
                        aPromise.fail(error);

                        return;
                    }

                    if (hasCause(cause, ConnectException.class)) {
                        error = LOGGER.getMessage(MessageCodes.BUCKETEER_618, iiifURL);

                        LOGGER.error(error, cause);
                        aPromise.fail(error);

                        return;
                    }

                    if (hasCause(cause, SocketTimeoutException.class)) {
                        error = LOGGER.getMessage(MessageCodes.BUCKETEER_619, iiifURL);

                        LOGGER.error(error, cause);
                        aPromise.fail(error);

                        return;
                    }

                    error = LOGGER.getMessage(MessageCodes.BUCKETEER_620, iiifURL);

                    LOGGER.error(error, cause);
                    aPromise.fail(error);

                    return;
                }

                status = get.result().statusCode();

                if (status == HTTP.OK) {
                    aPromise.complete();
                    return;
                }

                statusMessage = get.result().statusMessage();

                if (status == HTTP.UNAUTHORIZED || status == HTTP.FORBIDDEN) {
                    LOGGER.warn(MessageCodes.BUCKETEER_621, iiifURL, status, statusMessage);
                    aPromise.fail(LOGGER.getMessage(MessageCodes.BUCKETEER_609, myUsername));

                    return;
                }

                error = LOGGER.getMessage(MessageCodes.BUCKETEER_622, iiifURL, statusMessage);
                LOGGER.error(error);
                aPromise.fail(error);
            });
        }

        getJsonConsumer().handler(message -> {
            final String imageID = message.body().getString(Constants.IMAGE_ID);

            // This will eventually be a feature flag but this solves the issue for now
            if (imageID == null) {
                message.fail(HTTP.INTERNAL_SERVER_ERROR, LOGGER.getMessage(MessageCodes.BUCKETEER_604));
            } else {
                client.postAbs(iiifURL + "/tasks").basicAuthentication(myUsername, myPassword)
                        .putHeader(Constants.CONTENT_TYPE, "application/json").sendJsonObject(
                                new JsonObject().put("verb", "PurgeItemFromCache").put("identifier", imageID), post -> {
                                    if (post.succeeded()) {
                                        if (post.result().statusCode() == HTTP.ACCEPTED) {
                                            message.reply(message.body());
                                        } else {
                                            message.fail(post.result().statusCode(),
                                                    LOGGER.getMessage(MessageCodes.BUCKETEER_608));
                                        }
                                    } else {
                                        LOGGER.error(post.cause(), post.cause().getMessage());
                                        message.fail(post.result().statusCode(), post.cause().getMessage());
                                    }
                                });
            }
        });
    }

    protected Logger getLogger() {
        return LOGGER;
    }

    /**
     * Processes message from handler and event bus.
     *
     * @return A JSON message consumer
     */
    protected MessageConsumer<JsonObject> getJsonConsumer() {
        getLogger().debug(MessageCodes.BUCKETEER_025, ClearCacheVerticle.class.getName());
        return vertx.eventBus().<JsonObject>consumer(ClearCacheVerticle.class.getName());
    }

    /**
     * Determines if the exception is of an expected type.
     */
    private static boolean hasCause(final Throwable aThrowable, final Class<? extends Throwable> aExpectedType) {
        for (Throwable throwable = aThrowable; throwable != null; throwable = throwable.getCause()) {
            if (aExpectedType.isInstance(throwable)) {
                return true;
            }
        }

        return false;
    }
}
