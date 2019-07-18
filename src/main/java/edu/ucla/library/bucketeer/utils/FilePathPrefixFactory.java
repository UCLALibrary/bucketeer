
package edu.ucla.library.bucketeer.utils;

/**
 * A factory to get a file path prefix.
 */
public final class FilePathPrefixFactory {

    /**
     * Uses a private constructor since this is a utility class.
     */
    private FilePathPrefixFactory() {
    }

    /**
     * Get a prefix to use in full image source path creation.
     *
     * @param aPrefixName A prefix name to use (optional)
     * @param aRootPath A root path to use when constructing the prefix
     * @return The file path prefix to use when constructing full paths
     */
    public static IFilePathPrefix getPrefix(final String aPrefixName, final String aRootPath) {
        if ("UCLAFilePathPrefix".equals(aPrefixName)) {
            return new UCLAFilePathPrefix(aRootPath);
        } else { // could do other ELSE/IFs with reflection
            return new GenericFilePathPrefix(aRootPath);
        }
    }
}
