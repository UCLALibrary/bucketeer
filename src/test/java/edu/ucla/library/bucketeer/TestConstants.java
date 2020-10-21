
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
     * The place where temporary JP2s are written before S3 upload.
     */
    public static final String JP2_TMP_DIR = "target/bucketeer-tmp/";

    // Constant classes should have private constructors.
    private TestConstants() {
        // This is a constants class so doesn't need a public constructor.
    }
}
