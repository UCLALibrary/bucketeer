
package edu.ucla.library.bucketeer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * A simple test class that tests operation results. Things would break if these were changed without also changing the
 * things that use them to check the success or failure of a process.
 */
public class OpTest {

    /**
     * Tests the success operation result.
     */
    @Test
    public final void testSuccessResult() {
        assertEquals("success", Op.SUCCESS);
    }

    /**
     * Tests the retry operation result.
     */
    @Test
    public final void testRetryResult() {
        assertEquals("retry", Op.RETRY);
    }

    // TODO: Test the operation IDs defined in the Op class can be found in the OpenAPI spec file.
}
