
package edu.ucla.library.bucketeer;

import java.util.ArrayList;
import java.util.List;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

/**
 * An exception thrown when parsing a CSV file.
 */
public class CsvParsingException extends Exception {

    private static final Logger LOGGER = LoggerFactory.getLogger(CsvParsingException.class, Constants.MESSAGES);

    /**
     * The <code>serialVersionUID</code> for CsvException.
     */
    private static final long serialVersionUID = -4725879946862646567L;

    private final List<String> myMessages;

    /**
     * Create a CSV exception.
     */
    public CsvParsingException() {
        myMessages = new ArrayList<>();
    }

    /**
     * Create a CSV exception.
     */
    public CsvParsingException(final String aMessage) {
        myMessages = new ArrayList<>();
        myMessages.add(LOGGER.getMessage(aMessage));
    }

    /**
     * Adds message to the CsvException.
     *
     * @param aMessage A new message to add to the CsvException
     */
    public void addMessage(final String aMessage) {
        myMessages.add(LOGGER.getMessage(aMessage));
    }

    /**
     * Adds message with details to the CsvException.
     *
     * @param aMessage A new message to add to the CsvException
     * @param aDetails Additional details about the added message
     */
    public void addMessage(final String aMessage, final Object... aDetails) {
        myMessages.add(LOGGER.getMessage(aMessage, aDetails));
    }

    @Override
    public String getMessage() {
        return getMessages();
    }

    /**
     * Returns the parsing errors formatted with a system EOL as the delimiter.
     *
     * @param aDelimiter
     * @return The parsing errors formatted with a system EOL as the delimiter
     */
    public String getMessages() {
        return String.join(System.lineSeparator(), myMessages);
    }

    /**
     * Returns the parsing errors formatted with the supplied delimiter.
     *
     * @param aDelimiter
     * @return The parsing errors formatted with the supplied delimiter
     */
    public String getMessages(final String aDelimiter) {
        return String.join(aDelimiter, myMessages);
    }

    /**
     * Get the number of exception messages we've logged.
     *
     * @return The number of exception messages
     */
    public int countMessages() {
        return myMessages.size();
    }

    @Override
    public String getLocalizedMessage() {
        return getMessage();
    }

}
