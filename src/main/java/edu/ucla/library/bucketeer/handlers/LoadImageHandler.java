
package edu.ucla.library.bucketeer.handlers;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.MessageCodes;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.api.RequestParameter;
import io.vertx.ext.web.api.RequestParameters;
import org.codehaus.plexus.util.StringUtils;

public class LoadImageHandler implements Handler<RoutingContext> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoadImageHandler.class, Constants.MESSAGES);

    @Override
    public void handle(final RoutingContext aContext) {
        final HttpServerResponse response = aContext.response();

        final RequestParameters params = aContext.get("parsedParameters");
        final RequestParameter imageId = params.queryParameter(Constants.IMAGE_ID);
        final RequestParameter filePath = params.queryParameter(Constants.FILE_PATH);

        LOGGER.debug(MessageCodes.BUCKETEER_000, "imageID is null: " + (imageId == null));

        /* handle common error conditions */
        if (StringUtils.isBlank(imageId.toString()) || StringUtils.isBlank(filePath.toString()) ) {
            response.setStatusCode(400);
            response.putHeader(Constants.CONTENT_TYPE, "text/plain").end("400 Bad Request: imageId/filePath required");
        }

        final JsonObject jsonConfirm = new JsonObject().put(Constants.IMAGE_ID, imageId);
        jsonConfirm.put(Constants.FILE_PATH, filePath);
        response.setStatusCode(200);
        response.putHeader(Constants.CONTENT_TYPE, "application/json").end(jsonConfirm.toBuffer());
    }

}
