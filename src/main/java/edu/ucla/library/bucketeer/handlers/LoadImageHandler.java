
package edu.ucla.library.bucketeer.handlers;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.api.RequestParameter;
import io.vertx.ext.web.api.RequestParameters;

public class LoadImageHandler implements Handler<RoutingContext> {


    @Override
    public void handle(final RoutingContext aContext) {
        final HttpServerResponse response = aContext.response();

        final RequestParameters params = aContext.get("parsedParameters");
        final RequestParameter imageId = params.queryParameter("imageId");
        final RequestParameter filePath = params.queryParameter("filePath");

        response.setStatusCode(200);
        response.putHeader("imageId", imageId.toString());
        response.putHeader("filePath", filePath.toString());
        response.putHeader("content-type", "text/plain").end("SUCCESS!");
    }

}
