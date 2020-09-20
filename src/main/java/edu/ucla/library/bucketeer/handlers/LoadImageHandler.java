
package edu.ucla.library.bucketeer.handlers;

import java.io.FileNotFoundException;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;
import info.freelibrary.util.StringUtils;

import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.HTTP;
import edu.ucla.library.bucketeer.MessageCodes;
import edu.ucla.library.bucketeer.ProcessingException;
import edu.ucla.library.bucketeer.verticles.ImageWorkerVerticle;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * A handler that accepts requests to convert and load individual images.
 */
public class LoadImageHandler implements Handler<RoutingContext> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoadImageHandler.class, Constants.MESSAGES);

    @Override
    public void handle(final RoutingContext aContext) {
        final HttpServerResponse response = aContext.response();
        final HttpServerRequest request = aContext.request();
        final Vertx vertx = aContext.vertx();
        final EventBus eventBus = vertx.eventBus();

        final String imageId = request.getParam(Constants.IMAGE_ID);
        final String filePath = request.getParam(Constants.FILE_PATH);

        LOGGER.debug(MessageCodes.BUCKETEER_511, imageId, filePath);

        if (StringUtils.isEmpty(imageId) || StringUtils.isEmpty(filePath)) {
            returnError(response, HTTP.BAD_REQUEST, new ProcessingException(MessageCodes.BUCKETEER_020));
        } else if (!vertx.fileSystem().existsBlocking(filePath)) {
            returnError(response, HTTP.NOT_FOUND, new FileNotFoundException(filePath));
        } else {
            // On receiving a valid request, we put the request info in JSON and send it to the ImageWorkerVerticle
            final JsonObject imageWorkJson = new JsonObject();
            final DeliveryOptions options = new DeliveryOptions().setSendTimeout(Long.MAX_VALUE);

            imageWorkJson.put(Constants.IMAGE_ID, imageId);
            imageWorkJson.put(Constants.FILE_PATH, filePath);

            eventBus.request(ImageWorkerVerticle.class.getName(), imageWorkJson, options, imageLoad -> {
                if (imageLoad.succeeded()) {
                    final JsonObject responseJson = new JsonObject();

                    responseJson.put(Constants.IMAGE_ID, imageId);
                    responseJson.put(Constants.FILE_PATH, filePath);

                    response.setStatusCode(HTTP.CREATED);
                    response.putHeader(Constants.CONTENT_TYPE, Constants.JSON).end(responseJson.toBuffer());
                    response.close();
                } else {
                    returnError(response, HTTP.INTERNAL_SERVER_ERROR, imageLoad.cause());
                }
            });
        }
    }

    /**
     * Return an error to the HTTP requester.
     *
     * @param aResponse An HTTP response
     * @param aThrowable An exception
     */
    private void returnError(final HttpServerResponse aResponse, final int aErrorCode, final Throwable aThrowable) {
        LOGGER.error(aThrowable, aThrowable.getMessage());

        aResponse.setStatusCode(aErrorCode);
        aResponse.putHeader(Constants.CONTENT_TYPE, Constants.TEXT).end(aThrowable.getMessage());
        aResponse.close();
    }
}
