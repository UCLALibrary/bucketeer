
package edu.ucla.library.bucketeer.verticles;

import info.freelibrary.util.Logger;

import edu.ucla.library.bucketeer.MessageCodes;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;

public abstract class AbstractBucketeerVerticle extends AbstractVerticle {

    @Override
    public void stop(final Future<Void> aFuture) {
        final Logger logger = getLogger();

        if (logger.isDebugEnabled()) {
            logger.debug(MessageCodes.BUCKETEER_028, getClass().getName(), deploymentID());
        }

        aFuture.complete();
    }

    protected MessageConsumer<JsonObject> getJsonConsumer() {
        getLogger().debug(MessageCodes.BUCKETEER_025, getClass().getName());
        return vertx.eventBus().consumer(getClass().getName());
    }

    /**
     * Sends a message to another verticle with a supplied timeout value.
     *
     * @param aJsonObject A JSON message
     * @param aVerticleName A verticle name that will respond to the message
     * @param aTimeout A timeout measured in milliseconds
     */
    protected void sendMessage(final JsonObject aJsonObject, final String aVerticleName, final long aTimeout) {
        final DeliveryOptions options = new DeliveryOptions().setSendTimeout(aTimeout);

        vertx.eventBus().send(aVerticleName, aJsonObject, options, response -> {
            final Logger logger = getLogger();

            if (response.failed()) {
                if (response.cause() != null) {
                    logger.error(response.cause(), MessageCodes.BUCKETEER_005, aVerticleName, aJsonObject);
                } else {
                    logger.error(MessageCodes.BUCKETEER_005, aVerticleName, aJsonObject);
                }
            }
        });
    }

    /**
     * Send a message to another verticle.
     *
     * @param aJsonObject A JSON message
     * @param aVerticleName A verticle name that will respond to the message
     */
    protected void sendMessage(final JsonObject aJsonObject, final String aVerticleName) {
        sendMessage(aJsonObject, aVerticleName, DeliveryOptions.DEFAULT_TIMEOUT);
    }

    /**
     * Gets the logger created in the subclass.
     *
     * @return A configured logger that is ready to use
     */
    protected abstract Logger getLogger();

}
