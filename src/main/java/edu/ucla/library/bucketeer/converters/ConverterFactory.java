
package edu.ucla.library.bucketeer.converters;

import java.io.IOException;

import info.freelibrary.util.I18nRuntimeException;
import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.KakaduNotFoundException;
import edu.ucla.library.bucketeer.MessageCodes;

/**
 * A converter factory returns the type of JP2 converter that the system supports. If KAKADU_HOME is defined, it will
 * use Kakadu; else it will use OpenJPEG.
 */
public final class ConverterFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConverterFactory.class, Constants.MESSAGES);

    private static final String KDU_VERSION_EXEC = "kdu_compress -v";

    private static Converter myConverter;

    private static boolean hasKakadu;

    private ConverterFactory() {
        // Used internally in the class
    }

    /**
     * Gets the converter that's found on the system.
     *
     * @return The TIFF to JP2 converter
     */
    public static Converter getConverter() {
        if (myConverter == null) {
            if (checkSystemKakadu()) {
                myConverter = new KakaduConverter();
            } else {
                myConverter = new OpenJPEGConverter();
            }
        }

        return myConverter;
    }

    /**
     * Gets the converter that's found on the system.
     *
     * @param aClass A converter's class
     * @return The TIFF to JP2 converter
     * @throws KakaduNotFoundException If Kakadu has been requested but can't be found on the system
     */
    public static Converter getConverter(final Class<?> aClass) throws KakaduNotFoundException {
        if (aClass.getName().equals(KakaduConverter.class.getName())) {
            if (!checkSystemKakadu()) {
                throw new KakaduNotFoundException();
            }

            myConverter = new KakaduConverter();
        } else if (aClass.getName().equals(OpenJPEGConverter.class.getName())) {
            myConverter = new OpenJPEGConverter();
        } else {
            throw new I18nRuntimeException(Constants.MESSAGES, MessageCodes.BUCKETEER_032);
        }

        return myConverter;
    }

    /**
     * Tests whether Kakadu presence has been set manually.
     *
     * @return True if a system Kakadu exists; else, false
     */
    public static boolean hasSystemKakadu() {
        return hasKakadu;
    }

    /**
     * Tries to determine whether there is a system installed Kakadu available.
     *
     * @return <code>True</code> is the system has Kakadu installed; else, <code>False</code>.
     */
    public static boolean checkSystemKakadu() {
        try {
            if (Runtime.getRuntime().exec(KDU_VERSION_EXEC).waitFor() == 0) {
                hasKakadu = true;
                return hasKakadu;
            }
        } catch (final IOException | InterruptedException details) {
            if (details instanceof InterruptedException) {
                LOGGER.error(details, details.getMessage());
            }

            // We don't expect to be able to find it so a debug instead of warning or error is okay
            LOGGER.debug(MessageCodes.BUCKETEER_016);
        }

        hasKakadu = false;
        return false;
    }
}
