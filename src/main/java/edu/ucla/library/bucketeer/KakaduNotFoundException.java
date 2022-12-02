
package edu.ucla.library.bucketeer;

import java.util.Locale;

import info.freelibrary.util.I18nRuntimeException;

/**
 * An exception thrown when Kakadu needs to be used, but can't be found on the system.
 */
public class KakaduNotFoundException extends I18nRuntimeException {

    /**
     * The <code>serialVersionUID</code> for a <code>KakaduNotFoundException</code>.
     */
    private static final long serialVersionUID = -8092834594590761451L;

    /**
     * Creates a new <code>KakaduNotFoundException</code>.
     */
    public KakaduNotFoundException() {
        // This is intentionally left empty
    }

    /**
     * Creates a new <code>KakaduNotFoundException</code> with additional details about the exception and a non-default
     * locale.
     *
     * @param aLocale A non-default locale for the message
     * @param aMessageKey The message key (or text) with more details about the exception
     */
    public KakaduNotFoundException(final Locale aLocale, final String aMessageKey) {
        super(aLocale, Constants.MESSAGES, aMessageKey);
    }

    /**
     * Creates a new <code>KakaduNotFoundException</code> with additional details about the exception and a non-default
     * locale to use when constructing the exception's message.
     *
     * @param aLocale A non-default locale to be used when constructing the exception message
     * @param aMessageKey The message key (or text) with more details about the exception
     * @param aDetailsArray Additional details to be inserted into the message key's text
     */
    public KakaduNotFoundException(final Locale aLocale, final String aMessageKey, final Object... aDetailsArray) {
        super(aLocale, Constants.MESSAGES, aMessageKey, aDetailsArray);
    }

    /**
     * Creates a new <code>KakaduNotFoundException</code> with additional details about the exception.
     *
     * @param aMessageKey The message key (or text) with more details about the exception
     */
    public KakaduNotFoundException(final String aMessageKey) {
        super(Constants.MESSAGES, aMessageKey);
    }

    /**
     * Creates a new <code>KakaduNotFoundException</code> with additional details about the exception.
     *
     * @param aMessageKey The message key (or text) with more details about the exception
     * @param aDetailsArray Additional details to be inserted into the message key's text
     */
    public KakaduNotFoundException(final String aMessageKey, final Object... aDetailsArray) {
        super(Constants.MESSAGES, aMessageKey, aDetailsArray);
    }

    /**
     * Creates a new <code>KakaduNotFoundException</code> from the supplied root cause.
     *
     * @param aCause The underlying cause of the <code>KakaduNotFoundException</code>
     */
    public KakaduNotFoundException(final Throwable aCause) {
        super(aCause);
    }

    /**
     * Creates a new <code>KakaduNotFoundException</code> from the supplied underlying exception and some additional
     * details supplied in the message key (or text) string. The supplied message text is retrieved using the
     * non-default locale.
     *
     * @param aCause An underlying cause of the <code>KakaduNotFoundException</code>
     * @param aLocale The locale to use when getting the exception message
     * @param aMessageKey The message key (or text) with more details about the exception
     */
    public KakaduNotFoundException(final Throwable aCause, final Locale aLocale, final String aMessageKey) {
        super(aCause, aLocale, Constants.MESSAGES, aMessageKey);
    }

    /**
     * Creates a new <code>KakaduNotFoundException</code> from the supplied underlying exception and some additional
     * details supplied in the message key (or text) string.
     *
     * @param aCause An underlying cause of the <code>KakaduNotFoundException</code>
     * @param aLocale The locale to use when getting the exception message
     * @param aMessageKey The message key (or text) with more details about the exception
     * @param aDetailsArray Additional details to be inserted into the text from the message key
     */
    public KakaduNotFoundException(final Throwable aCause, final Locale aLocale, final String aMessageKey,
            final Object... aDetailsArray) {
        super(aCause, aLocale, Constants.MESSAGES, aMessageKey, aDetailsArray);
    }

    /**
     * Creates a new <code>KakaduNotFoundException</code> from the supplied underlying exception and some additional
     * details supplied in the message key (or text) string.
     *
     * @param aCause An underlying cause of the <code>KakaduNotFoundException</code>
     * @param aMessageKey The message key (or text) with more details about the exception
     */
    public KakaduNotFoundException(final Throwable aCause, final String aMessageKey) {
        super(aCause, Constants.MESSAGES, aMessageKey);
    }

    /**
     * Creates a new <code>KakaduNotFoundException</code> from the supplied underlying exception and some additional
     * details supplied in the message key (or text) string.
     *
     * @param aCause An underlying cause of the <code>KakaduNotFoundException</code>
     * @param aMessageKey The message key (or text) with more details about the exception
     * @param aDetailsArray Additional details to be inserted into the text from the message key
     */
    public KakaduNotFoundException(final Throwable aCause, final String aMessageKey, final Object... aDetailsArray) {
        super(aCause, Constants.MESSAGES, aMessageKey, aDetailsArray);
    }

}
