
package edu.ucla.library.bucketeer.converters;

import java.io.IOException;

import info.freelibrary.util.I18nRuntimeException;
import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.MessageCodes;

/**
 * A converter factory returns the type of JP2 converter that the system supports. If KAKADU_HOME is defined, it will
 * use Kakadu; else it will use OpenJPEG.
 */
@SuppressWarnings("PMD.NonThreadSafeSingleton") // FIXME: Letting it pass for now, but fix this in the future
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
            if (KakaduConverter.hasKakaduHome() || hasKakadu) {
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
     * @return The TIFF to JP2 converter
     */
    public static Converter getConverter(final Class<?> aClass) {
        if (aClass.getName().equals(KakaduConverter.class.getName())) {
            myConverter = new KakaduConverter();
        } else if (aClass.getName().equals(OpenJPEGConverter.class.getName())) {
            myConverter = new OpenJPEGConverter();
        } else {
            throw new I18nRuntimeException(Constants.MESSAGES, MessageCodes.BUCKETEER_032);
        }

        return myConverter;
    }

    /**
     * Kakadu presence is usually determined by the presence of KAKADU_HOME, but we can also tell the factory to trust
     * that it's been installed and is configured correctly.
     *
     * @param aKakaduFound If Kakadu can be found on the system without the assistance of KAKADU_HOME
     */
    public static void hasSystemKakadu(final boolean aKakaduFound) {
        hasKakadu = aKakaduFound;
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
     */
    public static void checkSystemKakadu() throws IOException, InterruptedException {
        if (!hasKakadu) {
            try {
                if (Runtime.getRuntime().exec(KDU_VERSION_EXEC).waitFor() == 0) {
                    hasKakadu = true;
                }
            } catch (final IOException details) {
                // We don't expect to be able to find it so a debug instead of warning or error is okay
                LOGGER.debug(MessageCodes.BUCKETEER_016);
            }
        }
    }
}
