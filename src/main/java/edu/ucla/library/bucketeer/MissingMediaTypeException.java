
package edu.ucla.library.bucketeer;

import info.freelibrary.util.I18nException;

/**
 * Exception thrown when a request is missing a required media-type
 */
public class MissingMediaTypeException extends I18nException {

    /**
     * The <code>serialVersionUID</code> for this class.
     */
    private static final long serialVersionUID = -3004544782946247794L;

    /**
     * Creates an incorrect media type exception from the value of the incorrect media type.
     */
    public MissingMediaTypeException() {
        super(Constants.MESSAGES, MessageCodes.BUCKETEER_504);
    }

}
