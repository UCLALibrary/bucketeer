
package edu.ucla.library.bucketeer;

/**
 * A class for package constants.
 */
public final class Constants {

    /**
     * ResourceBundle file name for I18n messages.
     */
    public static final String MESSAGES = "bucketeer_messages";

    /**
     * The ID for the image that's going to be processed.
     */
    public static final String IMAGE_ID = "image-id";

    /**
     * An array of all the IDs that have been processed.
     */
    public static final String IMAGE_ID_ARRAY = "image-id-array";

    /**
     * The path to the TIFF file that's going to be read.
     */
    public static final String FILE_PATH = "file-path";

    /**
     * The batch job name (i.e., the name of the file being processed)
     */
    public static final String JOB_NAME = "job-name";

    /**
     * The Slack handle of the person submitting the batch job
     */
    public static final String SLACK_HANDLE = "slack-handle";

    /**
     * The name of the "failures only" processing flag.
     */
    public static final String FAILURES_ONLY = "failures";

    /**
     * The IP for an unspecified host.
     */
    public static final String UNSPECIFIED_HOST = "0.0.0.0";

    /**
     * The content-type setting for an HTTP response.
     */
    public static final String CONTENT_TYPE = "content-type";

    /**
     * The content-type value for text.
     */
    public static final String TEXT = "text/plain";

    /**
     * The content-type value for HTML.
     */
    public static final String HTML = "text/html";

    /**
     * The content-type value for JSON.
     */
    public static final String JSON = "application/json";

    /**
     * The content-type value for XML.
     */
    public static final String XML = "application/xml";

    /**
     * The record of completed S3 uploads.
     */
    public static final String RESULTS_MAP = "s3-uploads";

    /**
     * A name for wait counters.
     */
    public static final String WAIT_COUNT = "wait-count";

    /**
     * The name of the lambda queue.
     */
    public static final String LAMBDA_MAP = "lambda-map";

    /**
     * The name of the mapping of deployed verticles. HERE I'm worried this is wrong, leaving this mark to remember to
     * check on it later
     */
    public static final String VERTICLE_MAP = "bucketeer.verticles";

    /**
     * Text for a slack message we want to sent.
     */
    public static final String SLACK_MESSAGE_TEXT = "slack-message-text";

    /**
     * Batch Metadata, stored as a JSON array
     */
    public static final String BATCH_METADATA = "batch-metadata";

    /**
     * Slack Channel ID to which we want to send messages.
     */
    public static final String SLACK_CHANNEL_ID = "bucketeer.slack.channel_id";

    /**
     * Slack Channel ID to which we want to send test messages.
     */
    public static final String SLACK_TEST_CHANNEL_ID = "bucketeer.slack.test_channel_id";

    /**
     * Slack Channel ID to which we want to send error messages.
     */
    public static final String SLACK_ERROR_CHANNEL_ID = "bucketeer.slack.error_channel_id";

    /**
     * Slack Webhook URL to which we want to send messages.
     */
    public static final String SLACK_WEBHOOK_URL = "bucketeer.slack.webhook_url";

    /**
     * Slack Webhook URL to which we want to send test messages.
     */
    public static final String SLACK_TEST_WEBHOOK_URL = "bucketeer.slack.test_webhook_url";

    /**
     * Slack Webhook URL to which we want to send error messages.
     */
    public static final String SLACK_ERROR_WEBHOOK_URL = "bucketeer.slack.error_webhook_url";

    /**
     * Slack Verification Token (used to confirm messages are coming from Slack).
     */
    public static final String SLACK_VERIFICATION_TOKEN = "bucketeer.slack.verification_token";

    /**
     * Slack OAuth Token (used to authenticate with Slack).
     */
    public static final String SLACK_OAUTH_TOKEN = "bucketeer.slack.oauth_token";

    /**
     * Slack Test User Handle (used in tests messages to send pings).
     */
    public static final String SLACK_TEST_USER_HANDLE = "bucketeer.slack.test_user_handle";

    /**
     * Prefix to use for any error messages sent to Slack (for hours of fun, set this to a <@username>)
     */
    public static final String SLACK_ERROR_MESSAGE_PREFIX = "<!channel>";

    /**
     * just a space string, useful
     */
    public static final String SPACE = " ";

    /**
     * Private constructor for Constants class.
     */
    private Constants() {
    }
}
