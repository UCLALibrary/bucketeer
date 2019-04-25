
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
import io.vertx.ext.web.RoutingContext;

public class LoadImageHandler implements Handler<RoutingContext> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoadImageHandler.class, Constants.MESSAGES);

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
              // FIXME: Sending request and not waiting for response; let's get response and check for fail
              sendMessage(imageWorkJson, ImageWorkerVerticle.class.getName());
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

}
