
package edu.ucla.library.bucketeer.handlers;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;
import info.freelibrary.util.StringUtils;

import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.HTTP;
import edu.ucla.library.bucketeer.MessageCodes;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.core.Future;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.ext.web.RoutingContext;
import io.vertx.core.eventbus.Message;

public class LoadImageHandler implements Handler<RoutingContext> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoadImageHandler.class, Constants.MESSAGES);
    private static final String IMAGEWORKERVERTICLENAME = "edu.ucla.library.bucketeer.verticles.ImageWorkerVerticle";
    private static final EventBus eventBus = aContext.vertx().eventBus();

    @Override
    public void handle(final RoutingContext aContext) {
        final HttpServerResponse response = aContext.response();
        final HttpServerRequest request = aContext.request();

        final String imageId = request.getParam(Constants.IMAGE_ID);
        final String filePath = request.getParam(Constants.FILE_PATH);

        if (StringUtils.isEmpty(imageId) || StringUtils.isEmpty(filePath)) {
            final String responseMessage = LOGGER.getMessage(MessageCodes.BUCKETEER_020);

            response.setStatusCode(HTTP.BAD_REQUEST);
            response.putHeader(Constants.CONTENT_TYPE, "text/plain").end(responseMessage);
            response.close();
        } else {
          // OK, we have a complete imageLoad request... let's do something with it.
          // We do this by passing a message to the ImageWorker Verticle
          // the message ImageWorker expects is a json object, with the following
          // parameters: filePath, imageId

          try {
              final JsonObject imageWorkJson = new JsonObject();
              imageWorkJson.put(Constants.IMAGE_ID, imageId);
              imageWorkJson.put(Constants.FILE_PATH, filePath);
              final DeliveryOptions options = new DeliveryOptions();
              //FIXME: this should be in a utility method somewhere
              // send a message to the imageworker verticle that we have work for it to do
              vertx.eventBus().send(IMAGEWORKERVERTICLENAME, imageWorkJson, options, response -> {
                  if (response.failed()) {
                      if (response.cause() != null) {
                          LOGGER.error(response.cause(), MessageCodes.BUCKETEER_005, IMAGEWORKERVERTICLENAME, imageWorkJson);
                      } else {
                          LOGGER.error(MessageCodes.BUCKETEER_005, IMAGEWORKERVERTICLENAME, imageWorkJson);
                      }
                  }
              });



          } catch (final Exception details) {
            //TODO fix the message code here
              LOGGER.error(details, MessageCodes.BUCKETEER_023, details.getMessage());
          }


            final JsonObject responseJson = new JsonObject();

            responseJson.put(Constants.IMAGE_ID, imageId);
            responseJson.put(Constants.FILE_PATH, filePath);

            response.setStatusCode(HTTP.OK);
            response.putHeader(Constants.CONTENT_TYPE, "application/json").end(responseJson.toBuffer());
            response.close();
        }
    }

// A copy and paste from the AbstractBucketeerVerticle... something to put in a utility class?

    /**
     * Sends a message to another verticle with default timeout value.
     *
     * @param aJsonObject A JSON message
     * @param aVerticleName A verticle name that will respond to the message
     */
    protected void sendMessage(final JsonObject aJsonObject, final String aVerticleName) {
        final DeliveryOptions options = new DeliveryOptions();

        eventBus().send(aVerticleName, aJsonObject, options, response -> {
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
}
