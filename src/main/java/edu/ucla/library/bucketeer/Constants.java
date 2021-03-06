
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
     * The callback URL for batch jobs. This URL may be sent from another Bucketeer instance's batch job processing or
     * it may be supplied by an external application (e.g. something that needs to create thumbnails for A/V materials
     * or PDFs).
     */
    public static final String CALLBACK_URL = "callback-url";

    /**
     * A number of jobs.
     */
    public static final String COUNT = "count";

    /**
     * The number of items remaining in a batch job.
     */
    public static final String REMAINING = "remaining";

    /**
     * Bucketeer jobs.
     */
    public static final String JOBS = "jobs";

    /**
     * A job lock.
     */
    public static final String JOB_LOCK = "job-lock";

    /**
     * A timeout for a job lock.
     */
    public static final int JOB_LOCK_TIMEOUT = 10000;

    /**
     * A timeout for the job delete watcher. Just for context, the curl default timeout is 600 seconds.
     */
    public static final int JOB_DELETE_TIMEOUT = 5000;

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
     * The header for indicating an image is a derivative image.
     */
    public static final String DERIVATIVE_IMAGE = "derivative-image";

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
    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
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
     * The content-type value for CSV.
     */
    public static final String CSV = "text/csv";

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
     * Just a space string, useful
     */
    public static final String SPACE = " ";

    /**
     * Just a empty string, useful
     */
    public static final String EMPTY = "";

    /**
     * An @ AT symbol
     */
    public static final String AT = "@";

    /**
     * A slash constant.
     */
    public static final char SLASH = '/';

    /**
     * Nothing processed in job.
     */
    public static final String NOTHING_PROCESSED = "nothing-processed";

    /**
     * CSV-formatted data.
     */
    public static final String CSV_DATA = "csv-data";

    /**
     * Private constructor for Constants class.
     */
    private Constants() {
    }
}
