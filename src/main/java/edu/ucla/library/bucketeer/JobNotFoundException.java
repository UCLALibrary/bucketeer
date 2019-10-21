
package edu.ucla.library.bucketeer;

import info.freelibrary.util.I18nException;

/**
 * An exception thrown when a job can't be found in the jobs queue.
 */
public class JobNotFoundException extends I18nException {

    /**
     * The <code>serialVersionUID</code> for JobNotFoundException.
     */
    private static final long serialVersionUID = -7439422784576946659L;

    /**
     * Creates a new JobNotFoundException.
     *
     * @param aMessageCode A message code
     * @param aDetails A details array
     */
    public JobNotFoundException(final String aMessageCode, final Object... aDetails) {
        super(Constants.MESSAGES, aMessageCode, aDetails);
    }
}
