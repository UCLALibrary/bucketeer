
package edu.ucla.library.bucketeer.utils;

import java.io.File;

import edu.ucla.library.bucketeer.TestConstants;

/**
 * A utility that assists with mounting shared directories during testing.
 */
@SuppressWarnings("uncommentedmain")
public final class SharedDriveSetup {

    /* Utility classes should have private constructors. */
    private SharedDriveSetup() {
    }

    /**
     * A utility that creates a shared drive with the current user's UID.
     *
     * @param args The program's arguments
     */
    public static void main(final String[] args) {
        new File(TestConstants.JP2_TMP_DIR).mkdirs();
    }

}
