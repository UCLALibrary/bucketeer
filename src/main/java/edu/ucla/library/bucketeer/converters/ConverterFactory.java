
package edu.ucla.library.bucketeer.converters;

import java.io.IOException;

/**
 * A converter factory returns the type of JP2 converter that the system supports. If KAKADU_HOME is defined, it will
 * use Kakadu; else it will use OpenJPEG.
 */
public final class ConverterFactory {

    private static Converter myConverter;

    private static boolean hasKakadu;

    private ConverterFactory() {
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
            if (Runtime.getRuntime().exec("kdu_compress -v").waitFor() == 0) {
                hasKakadu = true;
            }
        }
    }
}
