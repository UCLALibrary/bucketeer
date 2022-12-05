
package edu.ucla.library.bucketeer.handlers;

import info.freelibrary.util.Logger;

import edu.ucla.library.bucketeer.HTTP;
import edu.ucla.library.bucketeer.MessageCodes;
import edu.ucla.library.bucketeer.Op;
import edu.ucla.library.bucketeer.utils.CodeUtils;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * An abstract handler that implements sending messages on the event bus.
 */
public abstract class AbstractBucketeerHandler implements Handler<RoutingContext> {

    /**
     * Gets the logger for the instantiated handler.
     *
     * @return The logger for the instantiated handler
     */
    protected abstract Logger getLogger();

    /**
     * Sends a message to another verticle with a supplied timeout value.
     *
     * @param aVertx A reference to the Vert.x instance
     * @param aJsonObject A JSON message
     * @param aVerticleName A verticle name that will respond to the message
     * @param aTimeout A timeout measured in milliseconds
     */
    @SuppressWarnings("PMD.CognitiveComplexity")
    protected void sendMessage(final Vertx aVertx, final JsonObject aJsonObject, final String aVerticleName,
            final long aTimeout) {
        final DeliveryOptions options = new DeliveryOptions().setSendTimeout(aTimeout);

        aVertx.eventBus().request(aVerticleName, aJsonObject, options, response -> {
            if (response.failed()) {
                final Throwable exception = response.cause();
                final Logger log = getLogger();

                if (exception != null) {
                    if (exception instanceof ReplyException) {
                        final ReplyException replyException = (ReplyException) exception;
                        final String messageCode = CodeUtils.getCode(replyException.failureCode());
                        final String messageDetails = replyException.getMessage();
                        final String failureReason;

                        try {
                            failureReason = log.getMessage(messageCode, messageDetails);
                            log.error(MessageCodes.BUCKETEER_005, aVerticleName, failureReason);
                        } catch (final IndexOutOfBoundsException details) {
                            // Different number of slots and values
                            log.error(MessageCodes.BUCKETEER_005, aVerticleName, messageCode);
                            log.error(MessageCodes.BUCKETEER_154, messageCode, messageDetails, details.getMessage());
                        }
                    } else {
                        log.error(exception, MessageCodes.BUCKETEER_005, aVerticleName, exception.getMessage());
                    }
                } else {
                    log.error(MessageCodes.BUCKETEER_005, aVerticleName, log.getMessage(MessageCodes.BUCKETEER_136));
                }
            } else if (Op.RETRY.equals(response.result().body())) {
                getLogger().debug(MessageCodes.BUCKETEER_048, aVerticleName);
                sendMessage(aVertx, aJsonObject, aVerticleName, aTimeout);
            }

            // Do nothing if the response is an Op.SUCCESS
        });
    }

    /**
     * Send a message to another verticle.
     *
     * @param aVertx A reference to the Vert.x instance
     * @param aJsonObject A JSON message
     * @param aVerticleName A verticle name that will respond to the message
     */
    protected void sendMessage(final Vertx aVertx, final JsonObject aJsonObject, final String aVerticleName) {
        sendMessage(aVertx, aJsonObject, aVerticleName, DeliveryOptions.DEFAULT_TIMEOUT);
    }

    /**
     * Returns a 500 error.
     *
     * @param aResponse A response to return
     * @param aMessageCode A message code corresponding to the error message
     */
    protected void returnError(final HttpServerResponse aResponse, final String aMessageCode) {
        final Logger logger = getLogger();
        final String errorMessage = logger.getMessage(aMessageCode);

        logger.error(errorMessage);

        aResponse.setStatusCode(HTTP.INTERNAL_SERVER_ERROR);
        aResponse.setStatusMessage(errorMessage);
        aResponse.end();
    }
}
