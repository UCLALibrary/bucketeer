
package edu.ucla.library.bucketeer.io;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ListIterator;

import com.opencsv.exceptions.CsvDataTypeMismatchException;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;

import info.freelibrary.util.StringUtils;

import edu.ucla.library.bucketeer.utils.AnnotationMappingStrategy;

/**
 * Creates a Bean to CSV file writer.
 *
 * @param <T>
 */
public class BeanToCsvWriter<T> {

    private static final String VALUE = "\"{}\",";

    private final AnnotationMappingStrategy<T> myMappingStrategy;

    private String[] myUrlDecodes;

    private String[] myHeaders;

    /**
     * Creates a new Bean to CSV writer.
     *
     * @param aMappingStrategy
     */
    public BeanToCsvWriter(final AnnotationMappingStrategy<T> aMappingStrategy) {
        myMappingStrategy = aMappingStrategy;
    }

    /**
     * Sets Headers whose values should be URL decoded before putting into CSV form.
     *
     * @param aHeaders Headers whose values should be URL decoded before putting into CSV form
     */
    public void urlDecodeColumns(final String[] aHeaders) {
        myUrlDecodes = aHeaders;
    }

    /**
     * Write a list of Beans to a CSV file.
     *
     * @param aList A list of beans
     * @param aFile A CSV file
     * @throws IOException If there is trouble writing the CSV
     * @throws CsvRequiredFieldEmptyException If a required field is empty
     * @throws CsvDataTypeMismatchException If the field's data isn't what it's supposed to be
     */
    public void write(final List<T> aList, final File aFile) throws IOException, CsvRequiredFieldEmptyException,
            CsvDataTypeMismatchException {
        write(aList, new FileWriter(aFile));
    }

    /**
     * Write a list of Beans to a Writer.
     *
     * @param aList A list of beans
     * @param aWriter A CSV writer
     * @throws IOException If there is trouble writing the CSV
     * @throws CsvRequiredFieldEmptyException If a required field is empty
     * @throws CsvDataTypeMismatchException If the field's data isn't what it's supposed to be
     */
    public void write(final List<T> aList, final Writer aWriter) throws IOException, CsvRequiredFieldEmptyException,
            CsvDataTypeMismatchException {
        final ListIterator<T> iterator = aList.listIterator();

        myHeaders = myMappingStrategy.generateHeader(aList.get(0));
        aWriter.write(format(myHeaders));

        while (iterator.hasNext()) {
            aWriter.write(format(myMappingStrategy.transmuteBean(iterator.next())));
        }

        aWriter.close();
    }

    /**
     * Formats the supplied values array into a string.
     *
     * @param aValuesArray A CSV values array
     * @return The string representation of the supplied values array
     */
    private String format(final String[] aValuesArray) {
        final StringBuilder builder = new StringBuilder();
        final int length;

        int headerIndex = 0;

        if (aValuesArray.length > 0) {
            for (String value : aValuesArray) {
                boolean urlDecode = false;

                // Check to see if this value is one that needs to be URL decoded
                for (int index = 0; index < myUrlDecodes.length; index++) {
                    if (myHeaders[headerIndex].equals(myUrlDecodes[index])) {
                        urlDecode = true;
                    }
                }

                headerIndex += 1;

                if (urlDecode) {
                    value = URLDecoder.decode(value, StandardCharsets.UTF_8);
                }

                // Add formatted version of value to the string that goes into the CSV
                builder.append(StringUtils.format(VALUE, value.replace("\"", "\"\"")));
            }

            length = builder.length();
            builder.delete(length - 1, length).append(System.lineSeparator());
        }

        return builder.toString();
    }

}
