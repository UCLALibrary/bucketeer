
package edu.ucla.library.bucketeer;

/**
 * A constants class used in the handling of operations.
 */
@SuppressWarnings("PMD.ShortClassName")
public final class Op {

    //
    // Available operations
    //

    public static final String GET_STATUS = "getStatus";

    public static final String GET_JOBS = "getJobs";

    public static final String GET_CONFIG = "getConfig";

    public static final String GET_JOB_STATUSES = "getJobStatuses";

    public static final String DELETE_JOB = "deleteJob";

    public static final String LOAD_IMAGE = "loadImage";

    public static final String LOAD_IMAGES_FROM_CSV = "loadImagesFromCSV";

    public static final String UPDATE_BATCH_JOB = "updateBatchJob";

    //
    // Operation responses
    //

    public static final String SUCCESS = "success";

    public static final String RETRY = "retry";

    /**
     * Hidden constructor for the Op constants class.
     */
    private Op() {
    }

}
