
package edu.ucla.library.bucketeer.utils;

import java.io.File;
import java.io.Serializable;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * An interface for providing a file path prefix to use with an object's metadata.
 */
@JsonDeserialize(using = PrefixDeserializer.class)
public interface IFilePathPrefix extends Serializable {

    /**
     * Get the prefix to use with the supplied file.
     *
     * @param aFile A file from which to get a prefix
     * @return The supplied file's prefix
     */
    String getPrefix(File aFile);

}
