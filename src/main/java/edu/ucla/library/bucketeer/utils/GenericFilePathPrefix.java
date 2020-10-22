
package edu.ucla.library.bucketeer.utils;

import java.io.File;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A basic file path prefix implementation.
 */
public class GenericFilePathPrefix implements IFilePathPrefix {

    /**
     * The <code>serialVersionUID</code> of GenericFilePathPrefix.
     */
    private static final long serialVersionUID = 4651239370553190271L;

    @JsonProperty("genericRootDir")
    private final String myGenericRootDir;

    /**
     * Creates a generic file path prefix that returns the current path as the root.
     */
    public GenericFilePathPrefix() {
        myGenericRootDir = ".";
    }

    /**
     * Creates a generic file prefix that just returns the root path with which it's initialized.
     *
     * @param aRootDir A root path to use as the prefix
     */
    @JsonIgnore
    public GenericFilePathPrefix(final String aRootDir) {
        myGenericRootDir = aRootDir;
    }

    @Override
    @JsonIgnore
    public String getPrefix(final File aFile) {
        return myGenericRootDir;
    }

}
