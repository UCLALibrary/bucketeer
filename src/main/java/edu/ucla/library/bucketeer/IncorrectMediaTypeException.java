
package edu.ucla.library.bucketeer;

import info.freelibrary.util.I18nException;

/**
 * An exception thrown when the request's media type doesn't match the expected media type.
 */
public class IncorrectMediaTypeException extends I18nException {

    /**
     * A <code>serialVersionUID</code> for this class.
     */
    private static final long serialVersionUID = 2470821347266040691L;

    /**
     * Creates an incorrect media type exception from the value of the incorrect media type.
     *
     * @param aDetail
     */
    public IncorrectMediaTypeException(final String aDetail) {
        super(Constants.MESSAGES, MessageCodes.BUCKETEER_505, aDetail);
    }

}
