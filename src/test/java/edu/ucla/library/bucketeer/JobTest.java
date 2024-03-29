
package edu.ucla.library.bucketeer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.junit.Test;

import edu.ucla.library.bucketeer.Job.WorkflowState;
import info.freelibrary.util.StringUtils;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * A suite of batch job tests.
 */
public class JobTest extends AbstractBucketeerTest {

    private static final String TEST_JOB_NAME = "test-job";

    private static final String SLACK_HANDLE = "ksclarke";

    private static final File CSV_FILE = new File("src/test/resources/csv/live-test.csv");

    private static final File JSON_FILE = new File("src/test/resources/json/job.json");

    private static final File HEADER_FILE = new File("src/test/resources/text/headers.txt");

    private static final File TEST_TIFF_FILE = new File("src/test/resources/images/test.tif");

    private static final File TEST_FAIL_FILE = new File("src/test/resources/images/fail.tif");

    private static final String FILE_PATH = "filePath";

    private static final String ITEMS = "items";

    /**
     * Tests JSON serialization.
     */
    @Test
    public final void testJsonSerialization() throws IOException, ProcessingException {
        final Job job = JobFactory.getInstance().createJob(TEST_JOB_NAME, CSV_FILE);
        final JsonObject expected = new JsonObject(StringUtils.read(JSON_FILE));
        final JsonArray items = expected.getJsonArray(ITEMS);

        for (int index = 0; index < items.size(); index++) {
            final JsonObject item = items.getJsonObject(index);

            if (index != 7) {
                item.put(FILE_PATH, TEST_TIFF_FILE.getPath());
            } else {
                item.put(FILE_PATH, TEST_FAIL_FILE.getPath());
            }
        }

        assertEquals(expected, job.setSlackHandle(SLACK_HANDLE).toJSON());
    }

    /**
     * Test JSON deserialization.
     *
     * @throws IOException If there is trouble reading the test file
     */
    @Test
    public final void testJsonDeserialization() throws IOException {
        final JsonObject json = new JsonObject(StringUtils.read(JSON_FILE));
        final Job job = json.mapTo(Job.class);

        assertEquals(TEST_JOB_NAME, job.getName());
    }

    /**
     * Tests getting the name of the batch job.
     */
    @Test
    public final void testGetName() {
        assertEquals(TEST_JOB_NAME, new Job(TEST_JOB_NAME).getName());
    }

    /**
     * Tests getting the batch items.
     */
    @Test
    public final void testGetItems() throws IOException, ProcessingException {
        assertEquals(9, JobFactory.getInstance().createJob(TEST_JOB_NAME, CSV_FILE).size());
    }

    /**
     * Tests setting the Slack handle.
     */
    @Test
    public final void testSetSlackHandle() {
        assertEquals(SLACK_HANDLE, new Job(TEST_JOB_NAME).setSlackHandle(SLACK_HANDLE).getSlackHandle());
    }

    /**
     * Tests setting and getting the subsequent run boolean.
     */
    @Test
    public final void testSetIsSunsequentRun() {
        assertTrue(new Job(TEST_JOB_NAME).setSubsequentRun(true).isSubsequentRun());
    }

    /**
     * Tests getting the metadata header.
     */
    @Test
    public final void testGetMetadataHeader() throws IOException, ProcessingException {
        final Job job = JobFactory.getInstance().createJob(TEST_JOB_NAME, CSV_FILE);
        final String header = StringUtils.read(HEADER_FILE);

        assertEquals(header, StringUtils.toString(job.getMetadataHeader(), '|'));
    }

    /**
     * Tests finding a header.
     */
    @Test
    public final void testFindHeader() throws IOException, ProcessingException {
        final Job job = JobFactory.getInstance().createJob(TEST_JOB_NAME, CSV_FILE);

        assertNotEquals(job.findHeader(Metadata.ITEM_ID), -1);
        assertEquals(job.findHeader("???"), -1);
    }

    /**
     * Tests updating the metadata.
     */
    @Test
    public final void testUpdateMetadata() throws IOException, ProcessingException {
        final Job job = JobFactory.getInstance().createJob(TEST_JOB_NAME, CSV_FILE);
        final String[] originalHeader = job.getMetadataHeader();
        final int bucketeerStateIndex = job.findHeader(Metadata.BUCKETEER_STATE);

        final String[] newHeader;
        final List<String[]> newMetadata;

        job.updateMetadata();

        newHeader = job.getMetadataHeader();
        newMetadata = job.getMetadata();

        // IIIF Access URL should have been added
        assertEquals(originalHeader.length + 1, newHeader.length);
        assertTrue(newHeader[originalHeader.length].equals(Metadata.IIIF_ACCESS_URL));

        // Bucketeer State of all items should be predictable
        for (final String[] row : newMetadata) {
            final WorkflowState expectedWorkflowState;

            if (row[job.findHeader(Metadata.FILE_NAME)].equals(TEST_FAIL_FILE.getPath())) {
                expectedWorkflowState = WorkflowState.FAILED;
            } else {
                expectedWorkflowState = WorkflowState.EMPTY;
            }
            assertTrue(row[bucketeerStateIndex].equals(expectedWorkflowState.toString()));
        }
    }
}
