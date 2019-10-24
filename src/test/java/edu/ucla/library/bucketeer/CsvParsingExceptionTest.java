
package edu.ucla.library.bucketeer;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

public class CsvParsingExceptionTest {

    private static final String EOL = System.lineSeparator();

    private static final String FIRST_MESSAGE = "first message";

    private static final String SECOND_MESSAGE = "second message";

    private static final String THIRD_MESSAGE = "third message";

    private ProcessingException myException;

    /**
     * Sets up the tests.
     *
     * @throws Exception If the tests cannot be set up cleanly
     */
    @Before
    public void setUp() {
        myException = new ProcessingException();
        myException.addMessage(FIRST_MESSAGE);
        myException.addMessage(SECOND_MESSAGE);
        myException.addMessage(THIRD_MESSAGE);
    }

    /**
     * Tests the addMessage and getMessage methods.
     */
    @Test
    public final void testAddMessage() {
        assertEquals(FIRST_MESSAGE + EOL + SECOND_MESSAGE + EOL + THIRD_MESSAGE, myException.getMessage());
    }

    /**
     * Tests the getLocalizedMessage method.
     */
    @Test
    public final void testGetLocalizedMessage() {
        assertEquals(FIRST_MESSAGE + EOL + SECOND_MESSAGE + EOL + THIRD_MESSAGE, myException.getLocalizedMessage());
    }
}
