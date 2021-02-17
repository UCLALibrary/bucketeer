
package edu.ucla.library.bucketeer.converters;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import info.freelibrary.util.I18nRuntimeException;
import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.KakaduNotFoundException;
import edu.ucla.library.bucketeer.MessageCodes;

/**
 * The Kakadu TIFF to JP2 image converter. It is a blocking process, not reactive.
 */
public class KakaduConverter extends AbstractConverter implements Converter {

    public static final String WORKING_DIR_NAME = "kakadu";

    public static final String KAKADU_HOME = "KAKADU_HOME";

    public static final String RATE = "-rate";

    private static final Logger LOGGER = LoggerFactory.getLogger(KakaduConverter.class, Constants.MESSAGES);

    private static final File TMP_DIR = new File(System.getProperty("java.io.tmpdir"), WORKING_DIR_NAME);

    private static final String KAKADU_COMMAND = "kdu_compress";

    private static final List<String> BASE_OPTIONS = Arrays.asList("Clevels=6", "Clayers=6",
            "Cprecincts={256,256},{256,256},{128,128}", "Stiles={512,512}", "Corder=RPCL", "ORGgen_plt=yes",
            "ORGtparts=R", "Cblk={64,64}", "Cuse_sop=yes", "Cuse_eph=yes", "-flush_period", "1024");

    private static final List<String> LOSSLESS_OPTIONS = Arrays.asList("Creversible=yes", RATE, "-");

    private static final List<String> LOSSY_OPTION = Arrays.asList(RATE, "3");

    // private static final List<String> ALPHA_OPTION = Arrays.asList("-jp2_alpha");

    KakaduConverter() {
        if (!TMP_DIR.exists() && !TMP_DIR.mkdirs()) {
            throw new I18nRuntimeException(MessageCodes.BUNDLE, MessageCodes.BUCKETEER_163, TMP_DIR);
        }
    }

    @Override
    public File convert(final String aID, final File aTIFF, final Conversion aConversion)
            throws IOException, InterruptedException {
        final File jpx = new File(TMP_DIR, URLEncoder.encode(aID, StandardCharsets.UTF_8.toString()) + ".jpx");
        final List<String> command = new ArrayList<>();
        final String conversion = aConversion.name();

        command.addAll(Arrays.asList(getExecutable(), "-i", getPath(aTIFF), "-o", getPath(jpx)));
        command.addAll(BASE_OPTIONS);

        if (conversion.equals(Conversion.LOSSLESS.name())) {
            command.addAll(LOSSLESS_OPTIONS);
        } else if (conversion.equals(Conversion.LOSSY.name())) {
            command.addAll(LOSSY_OPTION);
        }

        if (ConverterFactory.hasSystemKakadu()) {
            run(new ProcessBuilder(command), aID, LOGGER);
        } else {
            throw new KakaduNotFoundException();
        }

        return jpx;
    }

    @Override
    public String getExecutable() {
        final StringBuilder executable = new StringBuilder();

        getKakaduHome().ifPresentOrElse(kakaduHome -> {
            executable.append(new File(kakaduHome, KAKADU_COMMAND));
        }, () -> executable.append(KAKADU_COMMAND));

        return executable.toString();
    }

    /**
     * Gets the absolute path of the supplied file.
     *
     * @param aFile An image file
     * @return The path of the supplied image file
     * @throws IOException If the file doesn't exist and can't be written
     */
    public String getPath(final File aFile) throws IOException {
        if (!aFile.exists() && !aFile.getParentFile().canWrite()) {
            throw new IOException(LOGGER.getMessage(MessageCodes.BUCKETEER_002, aFile));
        }

        return aFile.getAbsolutePath();
    }

    /**
     * Returns whether KAKADU_HOME is configured, indicating that Kakadu is available. KAKADU_HOME can be configured in
     * the system environment or in Java properties. Properties takes precedence.
     *
     * @return True if Kakadu is configured correctly; else, false
     */
    public static final boolean hasKakaduHome() {
        return getKakaduHome().isPresent();
    }

    /**
     * Gets the Kakadu home location.
     *
     * @return The location of the Kakadu binaries
     */
    private static Optional<String> getKakaduHome() {
        String kakaduHome = System.getProperty(KAKADU_HOME);

        if (kakaduHome == null) {
            kakaduHome = System.getenv(KAKADU_HOME);
        }

        return Optional.ofNullable(kakaduHome);
    }

}
