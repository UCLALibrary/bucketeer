
package edu.ucla.library.bucketeer.io;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.opencsv.bean.CsvToBeanBuilder;

import info.freelibrary.util.StringUtils;

import edu.ucla.library.bucketeer.Metadata;
import edu.ucla.library.bucketeer.utils.AnnotationMappingStrategy;

/**
 * A test of the BeanToCsvWriter class.
 */
public class BeanToCsvWriterTest {

    private static final File LIVE_TEST_CSV = new File("src/test/resources/csv/live-test.csv");

    private static final File SORTED_TEST_CSV = new File("src/test/resources/csv/sorted.csv");

    private BeanToCsvWriter<Metadata> myWriter;

    private List<Metadata> myMetadataList;

    private File myCsvFile;

    /**
     * Sets up the testing environment.
     *
     * @throws Exception If there is trouble setting up the testing environment
     */
    @Before
    public void setUp() throws Exception {
        final FileReader csvReader = new FileReader(LIVE_TEST_CSV);
        final CsvToBeanBuilder<Metadata> builder = new CsvToBeanBuilder<Metadata>(csvReader);
        final AnnotationMappingStrategy<Metadata> mappingStrategy = new AnnotationMappingStrategy<>(Metadata.class);
        final String[] columnsToDecode = new String[] { Metadata.Columns.ITEM_ARK, Metadata.Columns.PARENT_ARK };
        final int total;

        mappingStrategy.setHeader(Metadata.Columns.toArray());
        myWriter = new BeanToCsvWriter<>(mappingStrategy);
        myWriter.urlDecodeColumns(columnsToDecode);
        myMetadataList = builder.withType(Metadata.class).build().parse();
        total = myMetadataList.size() - 1;

        // Strip our test metadata list down to just one metadata bean
        for (int index = 0; index < total; index++) {
            myMetadataList.remove(0);
        }

        myCsvFile = Files.createTempFile(BeanToCsvWriterTest.class.getSimpleName() + "-", "").toFile();
    }

    /**
     * Tears down the testing environment.
     *
     * @throws Exception If there is trouble tearing down the testing environment
     */
    @After
    public void tearDown() throws Exception {
        if (myCsvFile != null) {
            myCsvFile.delete();
        }
    }

    /**
     * Tests writing the metadata to a CSV file.
     */
    @Test
    public final void testWriteListOfTFile() throws Exception {
        final String expected = StringUtils.read(SORTED_TEST_CSV);

        myWriter.write(myMetadataList, myCsvFile);
        assertEquals(expected, StringUtils.read(myCsvFile));
    }

    /**
     * Tests writing the metadata to a string.
     */
    @Test
    public final void testWriteListOfTWriter() throws Exception {
        final String expected = StringUtils.read(SORTED_TEST_CSV) + System.lineSeparator();
        final StringWriter writer = new StringWriter();

        myWriter.write(myMetadataList, writer);
        assertEquals(expected, writer.toString());
    }

}
