
package edu.ucla.library.bucketeer;

/**
 * Constants related to testing.
 */
public final class TestConstants {

    /**
     * A test timeout override value: 240 seconds.
     */
    public static final int TEST_TIMEOUT = 240000;

    /**
     * The name of the Bucketeer container.
     */
    public static final String BUCKETEER = "bucketeer";

    /**
     * The name of the Bucketeer container with feature flags turned on.
     */
    public static final String BUCKETEER_FF_ON = "bucketeer-ff-on";

    /**
     * The name of the Bucketeer version system property.
     */
    public static final String BUCKETEER_VERSION = "kakadu.version";

    /**
     * The place in the container where JP2s are written to after conversion.
     */
    public static final String JP2_SRC_DIR = "/tmp/kakadu";

    /**
     * The place on the host where the container's JP2s and CSVs are written temporarily, for inspection by our tests.
     */
    public static final String TMP_DEST_DIR = "target/bucketeer-tmp/";

    // Constant classes should have private constructors.
    private TestConstants() {
        // This is a constants class so doesn't need a public constructor.
    }
}
