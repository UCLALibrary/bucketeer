
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

    /** The handler's logger. */
    private static final Logger LOGGER = LoggerFactory.getLogger(MatchingOpNotFoundHandler.class, Constants.MESSAGES);

    /**
     * The generic pattern for batch job status requests.
     */
    private static final String STATUS_UPDATE_RE = "/batch/jobs/[^\\/]*/[^\\/]*/(true|false)";

    /**
     * A pattern for a batch job status update request that's missing the required item ID.
     */
    private static final String MISSING_ID_RE = "/batch/jobs/[^\\/]+//(true|false)";

    /**
     * A pattern for a batch job status update request that's missing the required job name.
     */
    private static final String MISSING_JOB_RE = "/batch/jobs//[^\\/]+/(true|false)";

    /**
     * A pattern for a batch job status update request that's missing the update status.
     */
    private static final String MISSING_STATUS_RE = "/batch/jobs/[^\\/]+/[^\\/]+";

    @Override
    public void handle(final RoutingContext aContext) {
        final HttpServerRequest request = aContext.request();
        final String method = request.rawMethod();
        final String path = request.path();

        if (path.matches(STATUS_UPDATE_RE)) { // Check batch job status updates
            if (!HttpMethod.PATCH.name().equals(method)) {
                error(aContext, HTTP.METHOD_NOT_ALLOWED, LOGGER.getMessage(MessageCodes.BUCKETEER_034, method));
            } else if (path.matches(MISSING_ID_RE)) {
                error(aContext, HTTP.BAD_REQUEST, LOGGER.getMessage(MessageCodes.BUCKETEER_600));
            } else if (path.matches(MISSING_JOB_RE)) {
                error(aContext, HTTP.BAD_REQUEST, LOGGER.getMessage(MessageCodes.BUCKETEER_601));
            }
        } else if (path.matches(MISSING_STATUS_RE)) { // Check that updates without a status are caught
            error(aContext, HTTP.BAD_REQUEST, LOGGER.getMessage(MessageCodes.BUCKETEER_602));
        } else if (HttpMethod.PATCH.name().equals(method)) { // Handle other random PATCH API requests
            error(aContext, HTTP.BAD_REQUEST, LOGGER.getMessage(MessageCodes.BUCKETEER_162, path));
        }
    }

    /**
     * Returns a more detailed error response when the request cannot match an item in a job because of missing
     * information.
     *
     * @param aContext A routing context
     * @param aErrorCode An HTTP response code for the error
     * @param aMessage An HTTP response status message for the error
     */
    private void error(final RoutingContext aContext, final int aErrorCode, final String aMessage) {
        final HttpServerResponse response = aContext.response();

        response.setStatusCode(aErrorCode);
        response.setStatusMessage(aMessage);

        aContext.fail(aErrorCode);
    }
}
