
package edu.ucla.library.bucketeer.converters;

import java.io.File;
import java.io.IOException;

public interface Converter {

    /**
     * Converts a TIFF image into a JP2 image.
     *
     * @param aID An image identifier
     * @param aTIFF A TIFF source image
     * @param aConversion Whether the conversion should be lossy or lossless
     * @return A JP2 image
     * @throws IOException If there is trouble converting the image file
     * @throws InterruptedException If the conversion process is interrupted
     */
    File convert(String aID, File aTIFF, Conversion aConversion) throws IOException, InterruptedException;

}
