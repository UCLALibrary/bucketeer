
package edu.ucla.library.bucketeer.handlers;

import static edu.ucla.library.bucketeer.Constants.MESSAGES;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.api.validation.ValidationException;

public class LoadImageFailureHandler implements Handler<RoutingContext> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoadImageFailureHandler.class, MESSAGES);

    @Override
    public void handle(final RoutingContext aContext) {
        final HttpServerResponse response = aContext.response();
        final Throwable failure = aContext.failure();

        if (failure instanceof ValidationException) {
            response.setStatusCode(400).setStatusMessage("ValidationException thrown! " +
                    ((ValidationException) failure).type().name()).end();
            response.close();
        } else {
            final String exceptionName = failure.getClass().getName();

            LOGGER.error(failure, failure.getMessage());

            response.setStatusCode(500).setStatusMessage("Exception thrown! " + exceptionName).end();
            response.close();
        }
    }
}
