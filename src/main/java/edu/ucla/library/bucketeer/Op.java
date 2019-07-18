
package edu.ucla.library.bucketeer;

/**
 * A constants class used in the handling of operations.
 */
public final class Op {

    //
    // Available operations
    //

    public static final String GET_STATUS = "getStatus";

    public static final String LOAD_IMAGE = "loadImage";

    public static final String LOAD_IMAGES_FROM_CSV = "loadImagesFromCSV";

    //
    // Operation responses
    //

    public static final String SUCCESS = "success";

    public static final String FAILURE = "failure";

    public static final String RETRY = "retry";

    /**
     * Hidden constructor for the Op constants class.
     */
    private Op() {
    }

}
