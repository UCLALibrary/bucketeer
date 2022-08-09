package edu.ucla.library.bucketeer.verticles;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;
import info.freelibrary.util.HTTP;

import edu.ucla.library.bucketeer.Config;
import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.MessageCodes;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import io.vertx.core.VertxException;
import io.vertx.core.Promise;



/**
 * A verticle to clear cantaloupe cache
 */
public class ClearCacheVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClearCacheVerticle.class, Constants.MESSAGES);

    private String myUsername;

    private String myPassword;

    /**
     * Clears cantaloupe cache and sends message if failure
     */
    @Override
    public void start(final Promise<Void> aPromise) throws Exception {
        super.start();

        final WebClient client = WebClient.create(Vertx.vertx());
        final JsonObject config = config();

        myUsername = config.getString(Config.IIIF_CACHE_USER);
        myPassword = config.getString(Config.IIIF_CACHE_PASSWORD);

        //check values aren't null and if they aren't null then request to status endpoint of cantaloupe
        if (myUsername == null || myPassword == null) {
            aPromise.fail(LOGGER.getMessage(MessageCodes.BUCKETEER_603));
        } else {
            client.postAbs("https://test.iiif.library.ucla.edu/tasks")
                  .basicAuthentication(myUsername, myPassword)
                  .send(post -> {
                      if(post.failed()) {
                        aPromise.fail(post.cause());
                      }
                  });
        }

        getJsonConsumer().handler(message -> {
            final String imageID = message.body().getString("imageID");
            LOGGER.info(imageID);

            // This will eventually be a feature flag but this solves the issue for now
            if (imageID == null) {
                message.fail(HTTP.INTERNAL_SERVER_ERROR, LOGGER.getMessage(MessageCodes.BUCKETEER_604));
            } else {
                client.postAbs("https://test.iiif.library.ucla.edu/tasks")
                    .basicAuthentication(myUsername, myPassword)
                    .putHeader("content-type", "application/json")
                    .sendJsonObject(new JsonObject()
                    .put("verb", "PurgeItemFromCache").put("identifier", imageID), post -> {
                        if (post.succeeded()) {
                            if(post.result().statusCode() == HTTP.ACCEPTED){
                                LOGGER.info(Integer.toString(post.result().statusCode()));
                                message.reply(message.body());
                            } else {
                                message.fail(post.result().statusCode(), LOGGER.getMessage(MessageCodes.BUCKETEER_608));
                            }
                        } else {
                            LOGGER.error(post.cause(), post.cause().getMessage());
                            message.fail(post.result().statusCode(), post.cause().getMessage());
                        }
                    });
            }
        });
        aPromise.complete();
    }

    protected Logger getLogger() {
        return LOGGER;
    }

    /**
     * Processes message from handler and event bus
     */
    protected MessageConsumer<JsonObject> getJsonConsumer() {
        getLogger().debug(MessageCodes.BUCKETEER_025, ClearCacheVerticle.class.getName());
        return vertx.eventBus().<JsonObject>consumer(ClearCacheVerticle.class.getName());
    }
}