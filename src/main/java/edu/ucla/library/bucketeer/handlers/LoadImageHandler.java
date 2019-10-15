
package edu.ucla.library.bucketeer.handlers;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;
import info.freelibrary.util.StringUtils;

import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.HTTP;
import edu.ucla.library.bucketeer.MessageCodes;
import edu.ucla.library.bucketeer.verticles.ImageWorkerVerticle;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class LoadImageHandler implements Handler<RoutingContext> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoadImageHandler.class, Constants.MESSAGES);

    private static final String IMAGE_WORKER_VERTICLE = ImageWorkerVerticle.class.getName();

    @Override
    public void handle(final RoutingContext aContext) {
        final HttpServerResponse response = aContext.response();
        final HttpServerRequest request = aContext.request();
        final Vertx vertx = aContext.vertx();
        final EventBus eventBus = vertx.eventBus();

        final String imageId = request.getParam(Constants.IMAGE_ID);
        final String filePath = request.getParam(Constants.FILE_PATH);

        if (StringUtils.isEmpty(imageId) || StringUtils.isEmpty(filePath)) {
            final String responseMessage = LOGGER.getMessage(MessageCodes.BUCKETEER_020);

            response.setStatusCode(HTTP.BAD_REQUEST);
            response.putHeader(Constants.CONTENT_TYPE, Constants.TEXT).end(responseMessage);
            response.close();
        } else if (!vertx.fileSystem().existsBlocking(filePath)) {
            final String responseMessage = LOGGER.getMessage(MessageCodes.BUCKETEER_129, filePath);

            response.setStatusCode(HTTP.BAD_REQUEST);
            response.putHeader(Constants.CONTENT_TYPE, Constants.TEXT).end(responseMessage);
            response.close();
        } else {
            // On receiving a valid request, we put the request info in JSON and send it to the ImageWorkerVerticle
            final JsonObject imageWorkJson = new JsonObject();
            final DeliveryOptions options = new DeliveryOptions();

            imageWorkJson.put(Constants.IMAGE_ID, imageId);
            imageWorkJson.put(Constants.FILE_PATH, filePath);

            eventBus.send(IMAGE_WORKER_VERTICLE, imageWorkJson, options);

            // We also want to acknowledge that we've received the request
            final JsonObject responseJson = new JsonObject();

            responseJson.put(Constants.IMAGE_ID, imageId);
            responseJson.put(Constants.FILE_PATH, filePath);

            response.setStatusCode(HTTP.OK);
            response.putHeader(Constants.CONTENT_TYPE, Constants.JSON).end(responseJson.toBuffer());
            response.close();
        }
    }
}
