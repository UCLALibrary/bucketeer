
package edu.ucla.library.bucketeer;

/**
 * Constants related to feature flag checking.
 */
public final class Features {

    /** The location of the feature flag configuration. */
    public static final String FEATURE_FLAGS_FILE = "/etc/bucketeer/bucketeer-features.conf";

    /** The name for the large image routing feature. */
    public static final String LARGE_IMAGE_ROUTING = "bucketeer.large.images";

    /** The name for the "write CSVs to local filesystem" feature. */
    public static final String FS_WRITE_CSV = "bucketeer.fs.write.csv";

    /** The property name for our features. */
    @SuppressWarnings("PMD.AvoidFieldNameMatchingTypeName")
    public static final String FEATURES = "features";

    /** A property indicating whether a feature is enabled or not. */
    public static final String ENABLED = "enabled";

    /**
     * Creates a new Features object.
     */
    private Features() {
        // This is intentionally left empty.
    }

}
