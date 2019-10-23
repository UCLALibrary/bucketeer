
package edu.ucla.library.bucketeer;

/**
 * The metadata elements we care about; everything else is just passed through the process.
 */
public final class Metadata {

    /**
     * The item ID (ARK in our case)
     */
    public static final String ITEM_ID = "Item ARK"; // TODO: Would be nice if we could generalize to 'Item ID'

    /**
     * The file name for the source file for the item.
     */
    public static final String FILE_NAME = "File Name";

    /**
     * The viewing hint for Work object types, indicating they don't have an image.
     */
    public static final String VIEWING_HINT = "viewingHint";

    /**
     * The object type of the item being ingested.
     * <p>
     * This is optional, but we'll check for any occurrence that's 'Collection' to ignore a missing image file.
     * </p>
     */
    public static final String OBJECT_TYPE = "Object Type";

    /**
     * The state of the source file ingest process in Bucketeer.
     */
    public static final String BUCKETEER_STATE = "Bucketeer State";

    /**
     * The location of the image in the IIIF server.
     */
    public static final String IIIF_ACCESS_URL = "IIIF Access URL";

    /**
     * Collection is a metadata value rather than type, indicating it has no source file.
     */
    public static final String COLLECTION = "Collection";

    /**
     * Work is a metadata value value rather than type; some works have files, some don't.
     */
    public static final String WORK = "Work";

    // Constants classes should not have public constructors.
    private Metadata() {
    }

}
