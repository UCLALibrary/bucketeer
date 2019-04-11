
package edu.ucla.library.bucketeer.handlers;

import static edu.ucla.library.bucketeer.Constants.MESSAGES;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.bucketeer.HTTP;
import edu.ucla.library.bucketeer.MessageCodes;
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

        LOGGER.error(failure, failure.getMessage());

        if (failure instanceof ValidationException) {
            final String errorType = ((ValidationException) failure).type().name();
            final String errorMessage = LOGGER.getMessage(MessageCodes.BUCKETEER_018, errorType);

            response.setStatusCode(HTTP.BAD_REQUEST).setStatusMessage(errorMessage).end();
            response.close();
        } else {
            final String errorMessage = LOGGER.getMessage(MessageCodes.BUCKETEER_019, failure.getClass().getName());

            response.setStatusCode(HTTP.INTERNAL_SERVER_ERROR).setStatusMessage(errorMessage).end();
            response.close();
        }
    }
}
