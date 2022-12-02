
package edu.ucla.library.bucketeer.converters;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import info.freelibrary.util.IOUtils;
import info.freelibrary.util.Logger;

import edu.ucla.library.bucketeer.MessageCodes;

/**
 * An abstract image converter.
 */
abstract class AbstractConverter {

    /**
     * Get the executable command.
     *
     * @return The executable command
     */
    protected abstract String getExecutable();

    /**
     * Run the conversion process.
     *
     * @param aProcessBuilder A process builder that has the process to run
     * @param aID The ID for the image that's being converted
     * @param aLogger A logger for the converter to use
     * @throws IOException If the process has trouble reading or writing
     * @throws InterruptedException If the process has been interrupted
     */
    protected void run(final ProcessBuilder aProcessBuilder, final String aID, final Logger aLogger)
            throws IOException, InterruptedException {
        final Process process = aProcessBuilder.start();

        if (process.waitFor() != 0) {
            aLogger.error(new String(IOUtils.readBytes(process.getErrorStream()), StandardCharsets.UTF_8));
            throw new IOException(aLogger.getMessage(MessageCodes.BUCKETEER_001, aID));
        }
        if (aLogger.isDebugEnabled()) {
            aLogger.debug(new String(IOUtils.readBytes(process.getInputStream()), StandardCharsets.UTF_8));
        }
    }
}
