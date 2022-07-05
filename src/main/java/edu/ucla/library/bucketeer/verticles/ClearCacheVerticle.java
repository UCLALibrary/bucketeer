package edu.ucla.library.bucketeer.verticles;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.bucketeer.Config;
import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.MessageCodes;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;



/**
 * A verticle to clear cantaloupe cache
 */

public class ClearCacheVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClearCacheVerticle.class, Constants.MESSAGES);

    private String myUsername;

    private String myPassword;

     /**
     * Clears cantaloupe cache and sends message if failure
     *
     */
    @Override
    public void start() throws Exception {
        super.start();

        final WebClient client = WebClient.create(Vertx.vertx());
        final JsonObject config = config();

        myUsername = config.getString(Config.IIIF_CACHE_USER);
        myPassword = config.getString(Config.IIIF_CACHE_PASSWORD);

        getJsonConsumer().handler(response -> {
            final String imageID = response.body().getString("imageID");
            LOGGER.info(imageID);

            //This will eventually be a feature flag but this solves the issue for now
            if (myUsername == null && myPassword == null) {
                response.reply("Username and Password are purposefully not set.");
            } else if (imageID == null) {
                response.reply("imageID was null");
            } else {
                client.postAbs("https://test.iiif.library.ucla.edu/tasks")
                    .basicAuthentication(myUsername, myPassword)
                    .putHeader("content-type", "application/json")
                    .sendJsonObject(new JsonObject()
                    .put("verb", "PurgeItemFromCache").put("identifier", imageID), post -> {
                        if (post.succeeded()) {
                            LOGGER.info(Integer.toString(post.result().statusCode()));
                            response.reply(response.body());
                        } else {
                            LOGGER.error(post.cause(), post.cause().getMessage());
                            response.fail(post.result().statusCode(), post.cause().getMessage());
                        }
                    });
            }

        });

        LOGGER.info("Verticle finished");

    }


    //processes message from handler and event bus
    protected MessageConsumer<JsonObject> getJsonConsumer() {
        getLogger().debug(MessageCodes.BUCKETEER_025, ClearCacheVerticle.class.getName());
        return vertx.eventBus().<JsonObject>consumer(ClearCacheVerticle.class.getName());
    }

    protected Logger getLogger() {
        return LOGGER;
    }
}
