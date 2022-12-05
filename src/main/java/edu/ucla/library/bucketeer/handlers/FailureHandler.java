
package edu.ucla.library.bucketeer.handlers;

import static edu.ucla.library.bucketeer.Constants.MESSAGES;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;
import info.freelibrary.util.StringUtils;

import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.HTTP;
import edu.ucla.library.bucketeer.MessageCodes;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.api.validation.ValidationException;

/**
 * A handler for failures not automatically handled by the OpenAPI routing.
 */
public final class FailureHandler implements Handler<RoutingContext> {

    /** The handler's logger. */
    private static final Logger LOGGER = LoggerFactory.getLogger(FailureHandler.class, MESSAGES);

    /** A delimiter. */
    private static final String COLON_DELIM = ": ";

    /** The exception page. */
    private final String myExceptionPage;

    /**
     * Constructs a failure handler for the image load handler.
     *
     * @throws IOException If the error template cannot be read
     */
    public FailureHandler() throws IOException {
        final StringBuilder templateBuilder = new StringBuilder();

        // Load a template used for returning the error page
        try (InputStream templateStream = getClass().getResourceAsStream("/webroot/error.html");
                BufferedReader templateReader = new BufferedReader(new InputStreamReader(templateStream))) {
            String line;

            while ((line = templateReader.readLine()) != null) {
                templateBuilder.append(line);
            }

            myExceptionPage = templateBuilder.toString();
        }
    }

    @Override
    public void handle(final RoutingContext aContext) {
        final HttpServerResponse response = aContext.response();
        final String errorMessage;
        final String errorType;

        Throwable failure = aContext.failure();

        LOGGER.error(failure, failure.getMessage());

        if (failure instanceof ValidationException) {
            final ValidationException error = (ValidationException) failure;

            errorType = error.type().name() + COLON_DELIM + error.getMessage();
            errorMessage = LOGGER.getMessage(MessageCodes.BUCKETEER_018, errorType);

            response.setStatusCode(HTTP.BAD_REQUEST).setStatusMessage(errorMessage);
        } else {
            // If we get a runtime exception, get what its cause was for more details
            if (failure instanceof RuntimeException) {
                failure = failure.getCause();
            }

            errorMessage = failure.getClass().getSimpleName() + COLON_DELIM + failure.getMessage();

            response.setStatusCode(HTTP.INTERNAL_SERVER_ERROR).setStatusMessage(errorMessage);
        }
        response.putHeader(Constants.CONTENT_TYPE, Constants.HTML);
        response.end(StringUtils.format(myExceptionPage, errorMessage));
        response.close();
    }
}
