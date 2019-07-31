
package edu.ucla.library.bucketeer;

/**
 * A set of HTTP related constants.
 */
public final class HTTP {

    /** Success response */
    public static final int OK = 200;

    /** Created response */
    public static final int CREATED = 201;

    /** Too many requests */
    public static final int TOO_MANY_REQUESTS = 429;

    /** Not found response */
    public static final int NOT_FOUND = 404;

    /** Generic internal server error */
    public static final int INTERNAL_SERVER_ERROR = 500;

    /** Bad request */
    public static final int BAD_REQUEST = 400;

    /**
     * A private constructor for the constants class.
     */
    private HTTP() {
    }
}
