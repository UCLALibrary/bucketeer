
package edu.ucla.library.bucketeer.converters;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import info.freelibrary.util.IOUtils;
import info.freelibrary.util.Logger;

import edu.ucla.library.bucketeer.MessageCodes;

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
     * @throws IOException If the process has trouble reading or writing
     * @throws InterruptedException If the process has been interrupted
     */
    protected void run(final ProcessBuilder aProcessBuilder, final String aID, final Logger aLogger)
            throws IOException, InterruptedException {
        final Process process = aProcessBuilder.start();

        if (process.waitFor() != 0) {
            aLogger.error(getMessage(new BufferedReader(new InputStreamReader(process.getErrorStream()))));
            throw new IOException(aLogger.getMessage(MessageCodes.BUCKETEER_001, aID));
        } else if (aLogger.isDebugEnabled()) {
            aLogger.debug(getMessage(new BufferedReader(new InputStreamReader(process.getInputStream()))));
        }
    }

    private String getMessage(final BufferedReader aReader) throws IOException {
        final StringBuilder buffer = new StringBuilder();

        String line;

        while ((line = aReader.readLine()) != null) {
            buffer.append(line);
        }

        IOUtils.closeQuietly(aReader);

        return buffer.toString();
    }
}
