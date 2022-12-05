
package edu.ucla.library.bucketeer.utils;

import java.io.File;
import java.nio.file.Paths;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A prefix for UCLA image masters. The prefix paths are defined in the <a href=
 * "https://github.com/UCLALibrary/californica/blob/master/app/importers/californica_mapper.rb#L180">Californica
 * code</a>.
 */
public class UCLAFilePathPrefix implements IFilePathPrefix {

    /** The <code>serialVersionUID</code> of UCLAFilePathPrefix. */
    private static final long serialVersionUID = -7564099601987867700L;

    /** The masters' directory path. */
    private static final String MASTERS_DIR_PATH = "Masters/";

    /** The Digital Library's masters' directory path. */
    private static final String DL_MASTERS_DIR_PATH = "Masters/dlmasters/";

    /** The root directory. */
    @JsonProperty("uclaRootDir")
    private String myUCLARootDir;

    /** Creates a prefix for UCLA's image sources. */
    @SuppressWarnings("unused")
    private UCLAFilePathPrefix() {
    }

    /**
     * Creates a prefix for UCLA's image sources.
     *
     * @param aRootDir A source directories for images
     */
    @JsonIgnore
    public UCLAFilePathPrefix(final String aRootDir) {
        myUCLARootDir = aRootDir;
    }

    @Override
    @JsonIgnore
    public String getPrefix(final File aFile) {
        final String path;

        if (aFile.getPath().startsWith(MASTERS_DIR_PATH)) {
            path = Paths.get(myUCLARootDir).toString();
        } else {
            path = Paths.get(myUCLARootDir, DL_MASTERS_DIR_PATH).toString();
        }

        return path;
    }

}
