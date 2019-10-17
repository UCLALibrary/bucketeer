
package edu.ucla.library.bucketeer.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import edu.ucla.library.bucketeer.MessageCodes;

/**
 * Tests of CodeUtils.
 */
public class CodeUtilsTest {

    /**
     * Tests whether getInt() returns the correct integer for the supplied MessageCodes string.
     */
    @Test
    public final void testGetInt() {
        assertEquals(001, CodeUtils.getInt(MessageCodes.BUCKETEER_001));
        assertEquals(100, CodeUtils.getInt(MessageCodes.BUCKETEER_100));
    }

    /**
     * Tests whether getString() returns the correct string form for the supplied message codes int.
     */
    @Test
    public final void testGetCode() {
        assertEquals(MessageCodes.BUCKETEER_000, CodeUtils.getCode(000));
        assertEquals(MessageCodes.BUCKETEER_100, CodeUtils.getCode(100));
    }
}
