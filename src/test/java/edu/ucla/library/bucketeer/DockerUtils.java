
package edu.ucla.library.bucketeer;

import static edu.ucla.library.bucketeer.Constants.MESSAGES;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import edu.ucla.library.bucketeer.converters.KakaduConverter;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

/**
 * Utility methods for running Docker commands on our test containers.
 */
public final class DockerUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(DockerUtils.class, MESSAGES);

    private DockerUtils() {
        // Utility classes must have hidden non-default constructor
    }

    /**
     * Copy a directory of files from inside the Docker container to our host system so that we can inspect them.
     *
     * @param aContainerName The name of the container to copy from
     * @param aSrcDirPath The absolute path to the source directory in the Docker container
     * @param aDestDirPath The path to the destination directory on the host system; may be relative
     * @return True If files were successfully copied to the host system's temporary directory
     * @throws IOException If there is trouble reading from the copying process
     * @throws InterruptedException If the copying process gets interrupted
     */
    public static boolean copy(final String aContainerName, final String aSrcDirPath, final String aDestDirPath) {
        final String namespacedContainerSrcDirPath = aContainerName + ":" + aSrcDirPath;
        final ProcessBuilder builder = new ProcessBuilder("docker", "cp", namespacedContainerSrcDirPath, aDestDirPath);

        builder.redirectErrorStream(true);

        try {
            final Process process = builder.start();

            if (process.waitFor() != 0) {
                final BufferedInputStream inStream = new BufferedInputStream(process.getInputStream());

                LOGGER.error(new String(inStream.readAllBytes(), StandardCharsets.UTF_8));
                inStream.close();
                return false;
            }

            return true;
        } catch (final IOException | InterruptedException details) {
            throw new RuntimeException(details);
        } finally {
            if (LOGGER.isDebugEnabled()) {
                final StringBuilder log = new StringBuilder(System.lineSeparator());

                for (final File dir : new File(aDestDirPath).listFiles()) {
                    // List the converted image files
                    if (dir.getName().equals(KakaduConverter.WORKING_DIR_NAME)) {
                        for (final File file : new File(aDestDirPath, KakaduConverter.WORKING_DIR_NAME).listFiles()) {
                            final Path path = file.toPath();

                            try {
                                final String user = Files.getOwner(path).getName();
                                final String perms = PosixFilePermissions.toString(Files.getPosixFilePermissions(path));

                                log.append("  "); // Add a little indentation since we use line breaks for formatting
                                log.append(String.join(Constants.SPACE, file.getAbsolutePath(), user, perms));
                                log.append(System.lineSeparator());
                            } catch (final IOException details) {
                                throw new RuntimeException(details);
                            }
                        }
                    }
                }

                if (log.length() != System.lineSeparator().length()) {
                    LOGGER.debug(MessageCodes.BUCKETEER_165, log.toString());
                }
            }
        }
    }
}
