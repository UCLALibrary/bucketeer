
package edu.ucla.library.bucketeer;

/**
 * A constants class used in the handling of operations.
 */
@SuppressWarnings("PMD.ShortClassName")
public final class Op {

    /** The endpoint that retrieves a job's status. */
    public static final String GET_STATUS = "getStatus";

    /** The endpoint that retrieves the jobs in flight. */
    public static final String GET_JOBS = "getJobs";

    /** The endpoint that returns the service's config. */
    public static final String GET_CONFIG = "getConfig";

    /** The endpoint from which to get a list of job statuses. */
    public static final String GET_JOB_STATUSES = "getJobStatuses";

    /** The endpoint at which an in process job can be deleted. */
    public static final String DELETE_JOB = "deleteJob";

    /** The endpoint to trigger an image load. */
    public static final String LOAD_IMAGE = "loadImage";

    /** The endpoint to trigger loading images from a CSV file. */
    public static final String LOAD_IMAGES_FROM_CSV = "loadImagesFromCSV";

    /** The endpoint at which batch jobs can be updated. */
    public static final String UPDATE_BATCH_JOB = "updateBatchJob";

    /** An operation response of successful. */
    public static final String SUCCESS = "success";

    /** An operation response indicating the operation should be tried again. */
    public static final String RETRY = "retry";

    /**
     * The value sent with a message reply from FinalizeJobVerticle if it fails to write a CSV to the local filesystem
     * mount. TODO: check for this value in the reply
     */
    public static final String FS_WRITE_CSV_FAILURE = "fs-write-csv-failure";

    /**
     * Hidden constructor for the Op constants class.
     */
    private Op() {
    }

}
