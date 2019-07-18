
package edu.ucla.library.bucketeer.utils;

import java.io.File;
import java.nio.file.Paths;

/**
 * A prefix for UCLA image masters. The prefix paths are defined in the <a href=
 * "https://github.com/UCLALibrary/californica/blob/master/app/importers/californica_mapper.rb#L180">Californica
 * code</a>.
 */
public class UCLAFilePathPrefix implements IFilePathPrefix {

    private static final String MASTERS_DIR_PATH = "Masters/";

    private static final String DL_MASTERS_DIR_PATH = "Masters/dlmasters/";

    private final String myRootDir;

    /**
     * Creates a prefix for UCLA's image sources.
     *
     * @param aRootDir A source directories for images
     */
    public UCLAFilePathPrefix(final String aRootDir) {
        myRootDir = aRootDir;
    }

    @Override
    public String getPrefix(final File aFile) {
        final String path;

        if (aFile.getPath().startsWith(MASTERS_DIR_PATH)) {
            path = Paths.get(myRootDir, MASTERS_DIR_PATH).toString();
        } else {
            path = Paths.get(myRootDir, DL_MASTERS_DIR_PATH).toString();
        }

        return path;
    }

}
