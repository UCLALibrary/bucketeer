
package edu.ucla.library.bucketeer;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

import info.freelibrary.util.StringUtils;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Test for the JobFactory class.
 */
public class JobFactoryTest {

    private static final File CSV_FILE = new File("src/test/resources/csv/live-test.csv");

    private static final File BAD_HEADERS = new File("src/test/resources/csv/dupe-headers.csv");

    private static final File JSON_FILE = new File("src/test/resources/json/job.json");

    private static final File TEST_TIFF_FILE = new File("src/test/resources/images/test.tif");

    private static final File TEST_FAIL_FILE = new File("src/test/resources/images/fail.tif");

    private static final String TEST_JOB_NAME = "test-job";

    private static final String FILE_PATH = "filePath";

    private static final String SLACK_HANDLE = "ksclarke";

    private static final String ITEMS = "items";

    /**
     * Test JobFactory.createJob().
     */
    @Test
    public final void testCreateJob() throws ProcessingException, IOException {
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
     * Test JobFactory rejects files with duplicate headers.
     */
    @Test
    public final void testRejectDeupeHeaders() throws ProcessingException, IOException {
        final Job job = JobFactory.getInstance().createJob(TEST_JOB_NAME, BAD_HEADERS);
        final int expected = 0;

        assertEquals(expected, job.size());
    }

}
