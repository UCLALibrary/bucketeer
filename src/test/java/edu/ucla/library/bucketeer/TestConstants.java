
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
     * The place in the container where JP2s are written to after conversion.
     */
    public static final String JP2_SRC_DIR = "bucketeer:/tmp/kakadu";

    /**
     * The place on the host where temporary JP2s are written before S3 upload.
     */
    public static final String JP2_TMP_DEST_DIR = "target/bucketeer-tmp/";

    // Constant classes should have private constructors.
    private TestConstants() {
        // This is a constants class so doesn't need a public constructor.
    }
}
