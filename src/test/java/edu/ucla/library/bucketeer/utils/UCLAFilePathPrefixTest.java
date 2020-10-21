
package edu.ucla.library.bucketeer.utils;

import static org.junit.Assert.assertEquals;

import java.io.File;

import org.junit.Test;

/**
 * Tests related to the UCLA file path prefix implementation.
 */
public class UCLAFilePathPrefixTest {

    private static final File DL_MASTERS_TEST_FILE = new File("asdf/aaaa");

    private static final File MASTERS_TEST_FILE = new File("Masters/asdf");

    private static final String ROOT_PATH = "/mnt/ucla";

    /**
     * Tests the getPrefix method with a DL masters file.
     */
    @Test
    public final void testGetPrefixDlMasters() {
        assertEquals("/mnt/ucla/Masters/dlmasters", new UCLAFilePathPrefix(ROOT_PATH).getPrefix(DL_MASTERS_TEST_FILE));
    }

    /**
     * Tests the getPrefix method with a masters file.
     */
    @Test
    public final void testGetPrefixMasters() {
        assertEquals(ROOT_PATH, new UCLAFilePathPrefix(ROOT_PATH).getPrefix(MASTERS_TEST_FILE));
    }
}
