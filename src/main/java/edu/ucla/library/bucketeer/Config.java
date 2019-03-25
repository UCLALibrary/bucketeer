
package edu.ucla.library.bucketeer;

/**
 * Some constant values for the project.
 */
public final class Config {

    // The HTTP port configuration parameter
    public static final String HTTP_PORT = "http.port";

    // The OpenAPI specification configuration parameter
    public static final String OPENAPI_SPEC_PATH = "openapi.spec.path";

    public static final String S3_ACCESS_KEY = "bucketeer.s3.access_key";

    public static final String S3_SECRET_KEY = "bucketeer.s3.secret_key";

    public static final String S3_REGION = "bucketeer.s3.region";

    public static final String S3_BUCKET = "bucketeer.s3.bucket";

    public static final String HTTP_CALLBACK = "bucketeer.http.callback";

    /**
     * Private constructor for the Constants class.
     */
    private Config() {
    }

}
