
package edu.ucla.library.bucketeer;

import static edu.ucla.library.bucketeer.Constants.MESSAGES;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;
import info.freelibrary.util.StringUtils;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Test for the JobFactory class.
 */
public class JobFactoryTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(JobFactoryTest.class, MESSAGES);

    private static final File CSV_FILE = new File("src/test/resources/csv/live-test.csv");

    private static final File MISSING_FAILED_FILE = new File("src/test/resources/csv/missing-failed.csv");

    private static final File BAD_HEADERS = new File("src/test/resources/csv/dupe-headers.csv");

    private static final File FILE_WITH_SPACES = new File("src/test/resources/csv/spaces-file.csv");

    private static final File JSON_FILE = new File("src/test/resources/json/job.json");

    private static final File TEST_TIFF_FILE = new File("src/test/resources/images/test.tif");

    private static final File TEST_FAIL_FILE = new File("src/test/resources/images/fail.tif");

    private static final String TEST_JOB_NAME = "test-job";

    private static final String FILE_PATH = "filePath";

    private static final String SLACK_HANDLE = "ksclarke";

    private static final String ITEMS = "items";

    @Rule
    public ExpectedException myThrown = ExpectedException.none();

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
    public final void testDupeHeadersThrowsException() throws ProcessingException, IOException {
        myThrown.expect(ProcessingException.class);
        myThrown.expectMessage("has one or more duplicate column headers");
        final Job job = JobFactory.getInstance().createJob(TEST_JOB_NAME, BAD_HEADERS);
    }

    /**
     * Test JobFactory rejects files with spaces in "File Name" entries.
     */
    @Test
    public final void testSpacesThrowsException() throws ProcessingException, IOException {
        myThrown.expect(ProcessingException.class);
        myThrown.expectMessage("There are spaces (\" \")");
        final Job job = JobFactory.getInstance().createJob(TEST_JOB_NAME, FILE_WITH_SPACES);
    }

    /**
     * Test JobFactory records failed/missing images.
     */
    @Test
    public final void testFailedMissingCount() throws ProcessingException, IOException {
        final String iiifURL = "unit.test.com";
        final String slackUserHandle = "fake.user";
        final long expectedFailed = 2;
        final long expectedMissing = 1;
        final Job job = JobFactory.getInstance().createJob(TEST_JOB_NAME, MISSING_FAILED_FILE);
        final String slackMessage = LOGGER.getMessage(MessageCodes.BUCKETEER_111, slackUserHandle, job.size(),
                job.failedItems(), job.missingItems(), iiifURL);

        assertEquals(expectedFailed, job.failedItems());
        assertEquals(expectedMissing, job.missingItems());
        assertTrue(slackMessage.contains("2 failed"));
        assertTrue(slackMessage.contains("1 missing images"));
    }

}
