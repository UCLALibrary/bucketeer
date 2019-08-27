
package edu.ucla.library.bucketeer.utils;

import static org.junit.Assert.assertEquals;

import java.io.File;

import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import info.freelibrary.util.StringUtils;

public class InvalidationBatchTest {

    private static final File SINGLE_INVALIDATION = new File("src/test/resources/xml/single-invalidation.xml");

    private static final File DOUBLE_INVALIDATION = new File("src/test/resources/xml/double-invalidation.xml");

    /**
     * Tests InvalidationBatch construction.
     *
     * @throws Exception If there is trouble running the tests
     */
    @Test
    public final void testSingleInvalidation() throws Exception {
        final ObjectMapper mapper = new XmlMapper().enable(SerializationFeature.INDENT_OUTPUT);
        final InvalidationBatch batch = new InvalidationBatch("asdf1");
        final String invalidationXML = StringUtils.read(SINGLE_INVALIDATION);

        batch.addPath("/path/to/1");

        assertEquals(invalidationXML, StringUtils.trimToNull(mapper.writeValueAsString(batch)));
    }

    /**
     * Tests InvalidationBatch construction.
     *
     * @throws Exception If there is trouble running the tests
     */
    @Test
    public final void testDoubleInvalidation() throws Exception {
        final ObjectMapper mapper = new XmlMapper().enable(SerializationFeature.INDENT_OUTPUT);
        final InvalidationBatch batch = new InvalidationBatch("asdf2");
        final String invalidationXML = StringUtils.read(DOUBLE_INVALIDATION);

        batch.addPath("/path/to/2");
        batch.addPath("/path/to/3");

        assertEquals(invalidationXML, StringUtils.trimToNull(mapper.writeValueAsString(batch)));
    }

}
