
package edu.ucla.library.bucketeer.handlers;


import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.api.validation.ValidationException;

public class LoadImageFailureHandler implements Handler<RoutingContext> {

    @Override
    public void handle(final RoutingContext aContext) {
        final Throwable failure = aContext.failure();
        if (failure instanceof ValidationException) {
            // Handle Validation Exception
            aContext.response()
            .setStatusCode(400)
            .setStatusMessage("ValidationException thrown! "
              + ((ValidationException) failure).type().name())
                .end();
        }
    }

}
