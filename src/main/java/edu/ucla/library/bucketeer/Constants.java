
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
     * A number of jobs.
     */
    public static final String COUNT = "count";

    /**
     * Bucketeer jobs.
     */
    public static final String JOBS = "jobs";

    /**
     * The status of a job.
     */
    public static final String STATUS = "status";

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
     * the content-type value for text.
     */
    public static final String TEXT = "text/plain";

    /**
     * The record of completed S3 uploads.
     */
    public static final String RESULTS_MAP = "s3-uploads";

    /**
     * A name for wait counters.
     */
    public static final String WAIT_COUNT = "wait-count";

    /**
     * A name for the S3 request counter.
     */
    public static final String S3_REQUEST_COUNT = "s3-request-count";

    /**
     * The name of the lambda jobs queue.
     */
    public static final String LAMBDA_JOBS = "lambda-jobs";

    /**
     * The name of the mapping of deployed verticles.
     */
    public static final String VERTICLE_MAP = "bucketeer-verticles";

    /**
     * Text for a slack message we want to sent.
     */
    public static final String SLACK_MESSAGE_TEXT = "slack-message-text";

    /**
     * Batch Metadata, stored as a JSON array
     */
    public static final String BATCH_METADATA = "batch-metadata";

    /**
     * just a space string, useful
     */
    public static final String SPACE = " ";

    /**
     * just a empty string, useful
     */
    public static final String EMPTY_STRING = "";

    /**
     * an @ AT symbol
     */
    public static final String AT = "@";

    /**
     * Private constructor for Constants class.
     */
    private Constants() {
    }
}
