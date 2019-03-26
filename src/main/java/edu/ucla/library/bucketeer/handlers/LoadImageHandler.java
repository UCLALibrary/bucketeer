
package edu.ucla.library.bucketeer.handlers;

import edu.ucla.library.bucketeer.Op;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.api.RequestParameter;
import io.vertx.ext.web.api.RequestParameters;

public class LoadImageHandler implements Handler<RoutingContext> {


    @Override
    public void handle(final RoutingContext aContext) {
        final HttpServerResponse response = aContext.response();

        final RequestParameters params = aContext.get("parsedParameters");
        final RequestParameter imageId = params.queryParameter(Op.IMAGE_ID);
        final RequestParameter filePath = params.queryParameter(Op.FILE_PATH);

        final JsonObject json = new JsonObject().put(Op.IMAGE_ID, imageId);
        json.put(Op.FILE_PATH, filePath);
        response.setStatusCode(200);
        response.putHeader("content-type", "application/json").end(json.toBuffer());
    }

}
