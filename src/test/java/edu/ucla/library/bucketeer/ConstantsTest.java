
package edu.ucla.library.bucketeer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * A simple test class that tests constants that are used as message keys. Things would break if these were changed
 * without also changing the things that use them to pass messages.
 */
public class ConstantsTest {

    /**
     * Tests the Slack handle message key.
     */
    @Test
    public final void testSlackHandle() {
        assertEquals("slack-handle", Constants.SLACK_HANDLE);
    }

    /**
     * Tests the file path message key.
     */
    @Test
    public final void testFilePath() {
        assertEquals("file-path", Constants.FILE_PATH);
    }

    /**
     * Tests the image ID message key.
     */
    @Test
    public final void testImageID() {
        assertEquals("image-id", Constants.IMAGE_ID);
    }

}
