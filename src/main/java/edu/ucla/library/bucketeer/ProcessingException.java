
package edu.ucla.library.bucketeer;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

/**
 * An exception thrown when parsing a CSV file or converting an image.
 */
public class ProcessingException extends Exception {

    /** The exception's logger. */
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessingException.class, Constants.MESSAGES);

    /** The <code>serialVersionUID</code> for ProcessingException. */
    private static final long serialVersionUID = -4725879946862646567L;

    /** The detail messages associated with this exception. */
    private List<String> myMessages;

    /**
     * Create an exception.
     */
    public ProcessingException() {
        myMessages = new ArrayList<>();
    }

    /**
     * Create an exception.
     *
     * @param aMessage A message with details about the processing exception
     */
    public ProcessingException(final String aMessage) {
        myMessages = new ArrayList<>();
        myMessages.add(LOGGER.getMessage(aMessage));
    }

    /**
     * Adds message to the exception.
     *
     * @param aMessage A new message to add to the exception
     */
    public void addMessage(final String aMessage) {
        myMessages.add(LOGGER.getMessage(aMessage));
    }

    /**
     * Adds message with details to the exception.
     *
     * @param aMessage A new message to add to the exception
     * @param aDetails Additional details about the added message
     */
    public void addMessage(final String aMessage, final Object... aDetails) {
        myMessages.add(LOGGER.getMessage(aMessage, aDetails));
    }

    /**
     * Gets the error messages formatted into a single string.
     */
    @Override
    @JsonIgnore
    public String getMessage() {
        return getMessages(System.lineSeparator());
    }

    /**
     * Returns the error messages in a list.
     *
     * @return The error messages
     */
    public List<String> getMessages() {
        return myMessages;
    }

    /**
     * Returns the parsing errors formatted with the supplied delimiter.
     *
     * @param aDelimiter A delimiter used to join the messages into a single string
     * @return The parsing errors formatted with the supplied delimiter
     */
    @JsonIgnore
    public String getMessages(final String aDelimiter) {
        return String.join(aDelimiter, myMessages);
    }

    /**
     * Sets the messages for this exception.
     *
     * @param aMessageList A list of messages
     */
    public void setMessages(final List<String> aMessageList) {
        myMessages = aMessageList;
    }

    /**
     * Get the number of exception messages we've logged.
     *
     * @return The number of exception messages
     */
    @JsonIgnore
    public int countMessages() {
        return myMessages.size();
    }

    @Override
    @JsonIgnore
    public String getLocalizedMessage() {
        return getMessage();
    }

}
