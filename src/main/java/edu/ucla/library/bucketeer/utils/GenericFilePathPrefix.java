
package edu.ucla.library.bucketeer.utils;

import java.io.File;

public class GenericFilePathPrefix implements IFilePathPrefix {

    private final String myRootPath;

    /**
     * Creates a generic file prefix that just returns the root path with which it's initialized.
     *
     * @param aRootPath A root path to use as the prefix
     */
    public GenericFilePathPrefix(final String aRootPath) {
        myRootPath = aRootPath;
    }

    @Override
    public String getPrefix(final File aFile) {
        return myRootPath;
    }

}
