
package edu.ucla.library.bucketeer;

/**
 * Some constant values for the project.
 */
public final class Constants {

    // The default port at which to run the application
    public static final int DEFAULT_PORT = 8888;

    //
    // The properties below can be set through the configuration file
    //

    // The HTTP port configuration parameter
    public static final String HTTP_PORT = "http.port";

    // The OpenAPI specification configuration parameter
    public static final String OPENAPI_SPEC_PATH = "openapi.spec.path";

    /**
     * Private constructor for the Constants class.
     */
    private Constants() {
    }

}
