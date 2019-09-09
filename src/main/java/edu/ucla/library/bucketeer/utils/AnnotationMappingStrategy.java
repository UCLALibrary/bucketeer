
package edu.ucla.library.bucketeer.utils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.HeaderColumnNameTranslateMappingStrategy;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;
import info.freelibrary.util.StringUtils;

import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.MessageCodes;

public class AnnotationMappingStrategy<T> extends HeaderColumnNameTranslateMappingStrategy<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnnotationMappingStrategy.class, Constants.MESSAGES);

    private static final String EMPTY = "";

    // Keep a mapping between annotation names and their actual fields.
    private final Map<String, Field> myValuesMap = new HashMap<>();

    // We can override the alphanumeric sorting of headers by supplying our desired order.
    private String[] myHeader;

    /**
     * Creates a new annotation-based mapping strategy.
     *
     * @param aClass A class to map to CSV
     */
    public AnnotationMappingStrategy(final Class<T> aClass) {
        final Map<String, String> map = new HashMap<>();

        // Find all the fields that have CsvBindByName annotation on them
        for (final Field field : aClass.getDeclaredFields()) {
            final CsvBindByName annotation = field.getAnnotation(CsvBindByName.class);

            if (annotation != null) {
                final String annotationName = annotation.column();

                // Store these annotations/fields for later use
                map.put(annotationName, annotationName);
                myValuesMap.put(annotationName, field);
            }
        }

        setType(aClass);
        setColumnMapping(map);
        setAnnotationDriven(true);
    }

    @SuppressWarnings("unchecked")
    @Override
    public String[] generateHeader(final T aBean) throws CsvRequiredFieldEmptyException {
        final String[] result = super.generateHeader(aBean);

        // We need to run generateHeader() but after we do we can just use our own headers
        if (myHeader != null && myHeader.length > 0) {
            return myHeader;
        } else {
            for (int index = 0; index < result.length; index++) {
                result[index] = getColumnName(index);
            }

            return result;
        }
    }

    /**
     * Sets the desired headers and a particular order in which they should be returned.
     *
     * @param aHeader
     */
    public void setHeader(final String[] aHeader) {
        myHeader = aHeader;
    }

    /**
     * Converts one bean into one CSV row.
     *
     * @param aBean A Java Bean
     */
    @Override
    public String[] transmuteBean(final T aBean) throws CsvDataTypeMismatchException, CsvRequiredFieldEmptyException {
        final List<String> values = new ArrayList<>();
        final String[] headers = generateHeader(aBean);
        final int columnCount = findMaxFieldIndex() + 1;

        // Loop through all our possible fields and supply empty strings where there are null values
        for (int index = 0; index < columnCount; index++) {
            final Field field = myValuesMap.get(headers[index]);

            // Mark field as accessible so we can read 'private' fields too
            field.setAccessible(true);

            try {
                final Object obj = field.get(aBean);
                final String value = StringUtils.trimTo(obj == null ? EMPTY : obj.toString(), EMPTY);

                // Add field value to our CSV values
                if (values.add(value)) {
                    LOGGER.debug(MessageCodes.BUCKETEER_108, headers[index], value);
                } else {
                    LOGGER.error(MessageCodes.BUCKETEER_109, headers[index]);
                }
            } catch (final IllegalAccessException details) {
                throw new CsvDataTypeMismatchException(details.getMessage());
            }
        }

        // Return the CSV row values
        return values.toArray(new String[values.size()]);
    }

    @Override
    public void setColumnOrderOnWrite(final Comparator<String> aComparator) {
        super.setColumnOrderOnWrite(aComparator);
    }

}
