
package edu.ucla.library.bucketeer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.exceptions.CsvException;

import info.freelibrary.util.IOUtils;
import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;
import info.freelibrary.util.StringUtils;

import edu.ucla.library.bucketeer.Metadata.WorkflowState;
import edu.ucla.library.bucketeer.utils.UCLAFilePathPrefix;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class MetadataTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetadataTest.class, Constants.MESSAGES);

    private static final File CSV_FILE = new File("src/test/resources/csv/ladailynews.csv");

    private static final File CSV_FAILURES_FILE = new File("src/test/resources/csv/failed-ingest.csv");

    private static final File LIVE_TEST_JSON = new File("src/test/resources/json/live-test.json");

    private static final File LIVE_TEST_CSV = new File("src/test/resources/csv/live-test.csv");

    private static final File TEST_MESSAGE_JSON = new File("src/test/resources/json/test-message.json");

    private static final String UCLA_ROOT = "/mnt/ucla";

    private static final String TEST_PREFIX = "test-";

    private static final String TEST_SUFFIX = ".tif";

    private static Metadata myWorkMetadata;

    private static Metadata myCollectionMetadata;

    private static Metadata myMutableMetadata;

    private static Metadata myFailedIngestMetadata;

    /**
     * Sets up the tests.
     *
     * @throws Exception If the tests cannot be set up cleanly
     */
    @BeforeClass
    public static void setUp() throws FileNotFoundException, CsvParsingException {
        List<Metadata> metadataList;
        FileReader csvReader = null;

        try {
            csvReader = new FileReader(CSV_FILE);
            metadataList = getMetadata(csvReader);

            myWorkMetadata = metadataList.get(0);
            myCollectionMetadata = metadataList.get(5172);
            myMutableMetadata = metadataList.get(5100);
        } finally {
            IOUtils.closeQuietly(csvReader);
        }

        try {
            csvReader = new FileReader(CSV_FAILURES_FILE);
            metadataList = getMetadata(csvReader);

            myFailedIngestMetadata = metadataList.get(1);
        } finally {
            IOUtils.closeQuietly(csvReader);
        }
    }

    /**
     * Tests the JSON serialization of the Metadata object.
     */
    @Test
    public final void testJsonSerialization() throws IOException, JsonProcessingException {
        final FileReader csvReader = new FileReader(LIVE_TEST_CSV);
        final CsvToBeanBuilder<Metadata> builder = new CsvToBeanBuilder<Metadata>(csvReader);
        final List<Metadata> metadataList = builder.withType(Metadata.class).build().parse();
        final String json = StringUtils.read(LIVE_TEST_JSON);
        final JsonArray found = new JsonArray(new ObjectMapper().writeValueAsString(metadataList));
        final JsonArray expected = new JsonArray(json);

        assertEquals(expected, found);
    }

    /**
     * Tests the JSON deserialization of the Metadata object.
     */
    @Test
    public final void testJsonDeserialization() throws IOException, JsonProcessingException {
        final FileReader csvReader = new FileReader(LIVE_TEST_CSV);
        final CsvToBeanBuilder<Metadata> builder = new CsvToBeanBuilder<Metadata>(csvReader);
        final List<Metadata> metadataList = builder.withType(Metadata.class).build().parse();
        final TypeReference<List<Metadata>> listTypeRef = new TypeReference<List<Metadata>>() {};
        final List<Metadata> list = new ObjectMapper().readValue(LIVE_TEST_JSON, listTypeRef);
        final StringBuilder expected = new StringBuilder();
        final StringBuilder found = new StringBuilder();

        // Stringify the list of expected metadata objects
        for (final Metadata metadata : metadataList) {
            expected.append(metadata.toString());
        }

        // Stringify the list of found metadata objects
        for (final Metadata metadata : list) {
            found.append(metadata.toString());
        }

        assertEquals(expected.toString(), found.toString());
    }

    /**
     * Tests putting a metadata object in a JSON message.
     */
    @Test
    public final void testPutInMessage() throws IOException {
        final FileReader csvReader = new FileReader(LIVE_TEST_CSV);
        final CsvToBeanBuilder<Metadata> builder = new CsvToBeanBuilder<Metadata>(csvReader);
        final List<Metadata> metadataList = builder.withType(Metadata.class).build().parse();
        final JsonObject expected = new JsonObject(StringUtils.read(TEST_MESSAGE_JSON));
        final JsonObject found = new JsonObject();
        final JsonArray metadataArray = new JsonArray();
        final ObjectMapper mapper = new ObjectMapper();

        // Convert the metadata list into a JsonArray with metadata objects
        for (final Metadata metadata : metadataList) {
            metadataArray.add(new JsonObject(mapper.writeValueAsString(metadata)));
        }

        found.put("channelText", "my text");
        found.put("channelId", "my channel");
        found.put("metadata", metadataArray);

        assertEquals(expected, found);
    }

    /**
     * Gets the ID from the metadata.
     */
    @Test
    public final void testGetID() {
        assertEquals("13030/hb000003n9", myWorkMetadata.getID());
    }

    /**
     * Test the failed ingest flag
     */
    @Test
    public final void testHasFailedIngest() {
        assertEquals(WorkflowState.FAILED, myFailedIngestMetadata.getWorkflowState());
    }

    /**
     * Test the failed ingest flag doesn't have a false positive
     */
    @Test
    public final void testHasFailedIngestFalse() {
        assertNotEquals(WorkflowState.FAILED, myWorkMetadata.getWorkflowState());
    }

    /**
     * Checks whether the object represented by the metadata is a work.
     */
    @Test
    public final void testIsWork() {
        assertTrue(myWorkMetadata.isWork());
    }

    /**
     * Checks whether the object represented by the metadata is a collection.
     */
    @Test
    public final void testIsCollection() {
        assertTrue(myCollectionMetadata.isCollection());
    }

    /**
     * Tests whether the work metadata is valid.
     */
    @Test
    public final void testWorkMetadataIsValid() throws IOException {
        final File testFile = Files.createTempFile(TEST_PREFIX, TEST_SUFFIX).toFile();

        // We won't have the actual file on our system, so set it to a file we do have before testing
        myMutableMetadata.setFilePathPrefix(null);
        myMutableMetadata.setFile(testFile);

        assertTrue(myMutableMetadata.isValid());

        testFile.delete();
    }

    /**
     * Tests whether the collection metadata is valid.
     */
    @Test
    public final void testCollectionMetadataIsValid() {
        assertTrue(myCollectionMetadata.isValid());
    }

    /**
     * Tests setting the file path prefix.
     */
    @Test
    public final void testSetFilePathPrefix() {
        myWorkMetadata.setFilePathPrefix(new UCLAFilePathPrefix(UCLA_ROOT));
    }

    /**
     * Tests getting the object's source file.
     */
    @Test
    public final void testGetFile() {
        final String foundPath;

        myWorkMetadata.setFilePathPrefix(new UCLAFilePathPrefix(UCLA_ROOT));
        foundPath = myWorkMetadata.getFile().getAbsolutePath();
        assertEquals("/mnt/ucla/Masters/dlmasters/ladailynews/image/clusc_1_1_00010432a.tif", foundPath);
    }

    /**
     * Tests whether the object's source file exists.
     */
    @Test
    public final void testHasFile() throws IOException {
        final File testFile = Files.createTempFile(TEST_PREFIX, TEST_SUFFIX).toFile();

        // We won't have the actual file on our system, so set it to a file we do have before testing
        myMutableMetadata.setFilePathPrefix(null);
        myMutableMetadata.setFile(testFile);

        assertTrue(myMutableMetadata.hasFile());

        testFile.delete();
    }

    /**
     * Tests the string representation of the metadata object.
     */
    @Test
    public final void testToString() throws IOException {
        assertEquals(StringUtils.read(new File("src/test/resources/csv/toString.csv")), myWorkMetadata.toString());
    }

    /**
     * Return metadata from the supplied CSV file.
     *
     * @param aCsvReader A CSV file reader
     * @return A list of the metadata elements
     * @throws FileNotFoundException If the supplied file can't be found
     * @throws CsvParsingException If there was an error while parsing the file
     */
    private static List<Metadata> getMetadata(final FileReader aCsvReader) throws FileNotFoundException,
            CsvParsingException {
        final CsvToBeanBuilder<Metadata> builder = new CsvToBeanBuilder<Metadata>(aCsvReader);
        final CsvToBean<Metadata> csvToBean = builder.withType(Metadata.class).build();
        final List<Metadata> metadataList;
        final List<CsvException> errors;

        csvToBean.setThrowExceptions(false);
        metadataList = csvToBean.parse();
        errors = csvToBean.getCapturedExceptions();

        if (errors.size() > 0) {
            final CsvParsingException parsingException = new CsvParsingException();

            errors.forEach(error -> {
                final String message = LOGGER.getMessage(error.getMessage() + " [line: {}]", error.getLineNumber());

                parsingException.addMessage(message);

                LOGGER.debug(message);
                LOGGER.trace(MessageCodes.BUCKETEER_055, String.join(", ", error.getLine()));
            });

            throw parsingException;
        }

        return metadataList;
    }
}
