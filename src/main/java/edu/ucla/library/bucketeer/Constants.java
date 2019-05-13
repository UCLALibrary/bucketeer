
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
     * Callback URL
     */
    public static final String CALLBACK_URL = "callback_url";

    /**
     * The ID for the image that's going to be processed.
     */
    public static final String IMAGE_ID = "imageId";

    /**
     * The path to the TIFF file that's going to be read.
     */
    public static final String FILE_PATH = "filePath";

    /**
     * The IP for an unspecified host.
     */
    public static final String UNSPECIFIED_HOST = "0.0.0.0";

    /**
     * The content-type setting for an HTTP response.
     */
    public static final String CONTENT_TYPE = "content-type";

    /**
     * The record of completed S3 uploads.
     */
    public static final String RESULTS_MAP = "s3-uploads";

    /**
     * A name for wait counters.
     */
    public static final String WAIT_COUNT = "wait-count";

    /**
     * Private constructor for Constants class.
     */
    private Constants() {
    }
}
