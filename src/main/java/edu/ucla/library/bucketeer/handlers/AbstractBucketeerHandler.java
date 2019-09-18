
package edu.ucla.library.bucketeer.handlers;

import info.freelibrary.util.Logger;

import edu.ucla.library.bucketeer.HTTP;
import edu.ucla.library.bucketeer.MessageCodes;
import edu.ucla.library.bucketeer.Op;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
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
    protected void sendMessage(final Vertx aVertx, final JsonObject aJsonObject, final String aVerticleName,
            final long aTimeout) {
        final DeliveryOptions options = new DeliveryOptions().setSendTimeout(aTimeout);

        aVertx.eventBus().send(aVerticleName, aJsonObject, options, response -> {
            if (response.failed()) {
                if (response.cause() != null) {
                    getLogger().error(response.cause(), MessageCodes.BUCKETEER_005, aVerticleName, aJsonObject);
                } else {
                    getLogger().error(MessageCodes.BUCKETEER_005, aVerticleName, aJsonObject);
                }
            } else if (response.result().body().equals(Op.RETRY)) {
                getLogger().debug(MessageCodes.BUCKETEER_048, aVerticleName);
                sendMessage(aVertx, aJsonObject, aVerticleName, aTimeout);
            }
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
