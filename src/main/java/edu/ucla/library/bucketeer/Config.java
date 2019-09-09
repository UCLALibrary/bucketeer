
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

    public static final String IIIF_URL = "bucketeer.iiif.url";

    public static final String THUMBNAIL_SIZE = "bucketeer.thumbnail.size";

    public static final String MAX_WAIT_QUEUE_SIZE = "httpclient.max.queue.size";

    public static final String FILESYSTEM_MOUNT = "bucketeer.fs.mount";

    public static final String FILESYSTEM_PREFIX = "bucketeer.fs.prefix";

    public static final String LAMBDA_S3_BUCKET = "lambda.s3.bucket";

    // The Slack channel for notifications
    public static final String SLACK_CHANNEL_ID = "bucketeer.slack.channel_id";

    // The Slack channel for errors
    public static final String SLACK_ERROR_CHANNEL_ID = "bucketeer.slack.error_channel_id";

    public static final String SLACK_OAUTH_TOKEN = "bucketeer.slack.oauth_token";

    public static final String SLACK_TEST_USER_HANDLE = "bucketeer.slack.test_user_handle";

    public static final String CDN_DISTRO_ID = "cdn.distribution.id";

    /**
     * Private constructor for the Constants class.
     */
    private Config() {
    }

}
