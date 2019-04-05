
package edu.ucla.library.bucketeer.handlers;

//import info.freelibrary.util.Logger;
//import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.bucketeer.Constants;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.codehaus.plexus.util.StringUtils;

public class LoadImageHandler implements Handler<RoutingContext> {

    /* saving logging for later
     * private static final Logger LOGGER = LoggerFactory.getLogger(LoadImageHandler.class, Constants.MESSAGES);
     */

    @Override
    public void handle(final RoutingContext aContext) {
        final HttpServerResponse response = aContext.response();

        final String imageId = aContext.pathParams().get(Constants.IMAGE_ID);
        final String filePath = aContext.pathParams().get(Constants.FILE_PATH);

        /* handle common error conditions */
        if (StringUtils.isBlank(imageId) || StringUtils.isBlank(filePath) ) {
            response.setStatusCode(400);
            response.putHeader(Constants.CONTENT_TYPE, "text/plain").end("400 Bad Request: imageId/filePath required");
        }

        final JsonObject jsonConfirm = new JsonObject().put(Constants.IMAGE_ID, imageId);
        jsonConfirm.put(Constants.FILE_PATH, filePath);
        response.setStatusCode(200);
        response.putHeader(Constants.CONTENT_TYPE, "application/json").end(jsonConfirm.toBuffer());
    }

}
