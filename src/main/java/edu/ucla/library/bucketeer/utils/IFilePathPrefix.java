
package edu.ucla.library.bucketeer.utils;

import java.io.File;
import java.io.Serializable;

/**
 * An interface for providing a file path prefix to use with an object's metadata.
 */
public interface IFilePathPrefix extends Serializable {

    /**
     * Get the prefix to use with the supplied file.
     *
     * @return The supplied file's prefix
     */
    String getPrefix(File aFile);

}
