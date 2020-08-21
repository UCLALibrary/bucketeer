
package edu.ucla.library.bucketeer.handlers;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.HTTP;
import edu.ucla.library.bucketeer.MessageCodes;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;

/**
 * A handler for requests that do not match the operations defined in the Bucketeer OpenAPI specification.
 * <p>
 * This handler can fail the context with any response code other than a 404. Because it's set up to catch 404s, failing
 * the routing context with a 404 will create a loop. To return a legitimate 404 from this handler, use
 * HttpServerReponse and set 404 as the response code, remembering to call <code>.end()</code> on the response.
 */
public class MatchingOpNotFoundHandler implements Handler<RoutingContext> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MatchingOpNotFoundHandler.class, Constants.MESSAGES);

    private static final String STATUS_UPDATE_RE = "/batch/jobs/[^\\/]+/[^\\/]+/(true|false)";

    @Override
    public void handle(final RoutingContext aContext) {
        if (aContext.failed()) {
            final HttpServerRequest request = aContext.request();
            final String method = request.rawMethod();

            // If someone hits the job status update URL using a method other than PATCH, return an error
            if (request.path().matches(STATUS_UPDATE_RE) && !HttpMethod.PATCH.name().equals(method)) {
                final HttpServerResponse response = aContext.response();

                // Set a better response code and message
                response.setStatusCode(HTTP.METHOD_NOT_ALLOWED);
                response.setStatusMessage(LOGGER.getMessage(MessageCodes.BUCKETEER_034, method));

                // We also need to change the fail code on the routing event
                aContext.fail(HTTP.METHOD_NOT_ALLOWED);
            }
        }
    }
}
