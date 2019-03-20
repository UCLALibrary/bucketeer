
package edu.ucla.library.bucketeer;

import edu.ucla.library.bucketeer.handlers.GetPingHandler;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.api.contract.openapi3.OpenAPI3RouterFactory;

/**
 * Main verticle that starts the application.
 */
public class MainVerticle extends AbstractVerticle {

    private static final String DEFAULT_SPEC_PATH = "src/main/resources/bucketeer.yaml";

    /**
     * Starts a Web server.
     */
    @Override
    @SuppressWarnings("rawtypes")
    public void start(final Future<Void> aFuture) {
        final HttpServer server = vertx.createHttpServer();
        final String apiSpec = config().getString(Constants.OPENAPI_SPEC_PATH, DEFAULT_SPEC_PATH);

        OpenAPI3RouterFactory.create(vertx, apiSpec, creation -> {
            if (creation.succeeded()) {
                final OpenAPI3RouterFactory routerFactory = creation.result();
                final int port = config().getInteger(Constants.HTTP_PORT, Constants.DEFAULT_PORT);

                routerFactory.addHandlerByOperationId(Op.GET_PING, new GetPingHandler());
                server.requestHandler(routerFactory.getRouter()).listen(port);

                aFuture.complete();
            } else {
                aFuture.fail(creation.cause());
            }
        });
    }

}
