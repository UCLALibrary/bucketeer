
package edu.ucla.library.bucketeer;

/**
 * Some constant values for the project.
 */
public final class Config {

    // The HTTP port configuration parameter
    public static final String HTTP_PORT = "http.port";

    // The OpenAPI specification configuration parameter
    public static final String OPENAPI_SPEC_PATH = "openapi.spec.path";

    // S3 configuration options
    public static final String S3_ACCESS_KEY = "bucketeer.s3.access_key";

    public static final String S3_SECRET_KEY = "bucketeer.s3.secret_key";

    public static final String S3_REGION = "bucketeer.s3.region";

    public static final String S3_BUCKET = "bucketeer.s3.bucket";

    /** The IIIF server URL in which the JPEG 2000 images end up */
    public static final String IIIF_URL = "bucketeer.iiif.url";

    /** The IIIF URL prefix configuration variable */
    public static final String IIIF_PREFIX = "bucketeer.iiif.url.prefix";

    // The local large image Bucketeer server
    public static final String LARGE_IMAGE_URL = "large.image.url";

    // A URL at which batch status updates can be sent
    public static final String BATCH_CALLBACK_URL = "batch.callback.url";

    // The Fester manifestor URL (which includes the /collections endpoint)
    public static final String FESTER_URL = "fester.url";

    // Thumbnail size for pre-caching thumbnails
    public static final String THUMBNAIL_SIZE = "bucketeer.thumbnail.size";

    // The maximum size TIFF image we'll attempt to process
    public static final String MAX_SOURCE_SIZE = "bucketeer.max.source.file.size";

    // Configuration options for the S3 upload verticle(s)
    public static final String S3_MAX_REQUESTS = "s3.max.requests";

    public static final String S3_MAX_RETRIES = "s3.max.retries";

    public static final String S3_REQUEUE_DELAY = "s3.requeue.delay";

    public static final String S3_UPLOADER_INSTANCES = "s3.uploader.instances";

    public static final String S3_UPLOADER_THREADS = "s3.uploader.threads";

    // Where image files can be found on the local file system
    public static final String FILESYSTEM_IMAGE_MOUNT = "bucketeer.fs.image.mount";

    public static final String FILESYSTEM_IMAGE_PREFIX = "bucketeer.fs.image.prefix";

    // Where CSVs are written on the local file system
    public static final String FILESYSTEM_CSV_MOUNT = "bucketeer.fs.csv.mount";

    // The S3 bucket that the bucketeer lambda watches for TIFF uploads
    public static final String LAMBDA_S3_BUCKET = "lambda.s3.bucket";

    // The Slack channel for notifications
    public static final String SLACK_CHANNEL_ID = "bucketeer.slack.channel_id";

    // The Slack channel for errors
    public static final String SLACK_ERROR_CHANNEL_ID = "bucketeer.slack.error_channel_id";

    // The Slack OAuth token that Bucketeer uses to post to Slack channels
    public static final String SLACK_OAUTH_TOKEN = "bucketeer.slack.oauth_token";

    // A Slack user handle that tests use as the recipient of test messages
    public static final String SLACK_TEST_USER_HANDLE = "bucketeer.slack.test_user_handle";

    // A configuration option for turning on or off feature flags
    public static final String FEATURE_FLAGS = "feature.flags";

    // The username for the Bucketeer cache
    public static final String IIIF_CACHE_USER = "bucketeer.iiif.cache.user";

    // The password for the Bucketeer cache
    public static final String IIIF_CACHE_PASSWORD = "bucketeer.iiif.cache.password";

    /**
     * Private constructor for the Constants class.
     */
    private Config() {
    }

}
