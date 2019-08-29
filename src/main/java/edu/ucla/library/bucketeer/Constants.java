
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
     * The name of the mapping of deployed verticles.
     */
    public static final String VERTICLE_MAP = "bucketeer-verticles";

    /**
     * Private constructor for Constants class.
     */
    private Constants() {
    }
}
