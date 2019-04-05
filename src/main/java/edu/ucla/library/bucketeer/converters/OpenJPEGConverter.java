
package edu.ucla.library.bucketeer.converters;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * A JP2 converter that uses OpenJPEG behind the scenes.
 */
public class OpenJPEGConverter implements Converter {

    private static final List<String> LOSSLESS_OPTIONS = Arrays.asList("-t", "512,512", "-TP", "R", "-b", "64,64",
            "-n", "6", "-c", "[256,256],[256,256],[128,128]", "-p", "RPCL", "-SOP");

    OpenJPEGConverter() {
    }

    @Override
    public File convert(final String aID, final File aTIFF, final Conversion aConversion) {
        // TODO Auto-generated method stub
        return null;
    }

}
