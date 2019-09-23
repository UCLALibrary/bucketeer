
package edu.ucla.library.bucketeer.utils;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

import edu.ucla.library.bucketeer.CsvParsingException;
import edu.ucla.library.bucketeer.Job;
import io.vertx.core.json.JsonObject;

/**
 * Test for the JobFactory class.
 */
public class JobFactoryTest {

    /**
     * Test JobFactory.createJob().
     */
    @Test
    public final void testCreateJob() throws CsvParsingException, IOException {
        final File csvFile = new File("src/test/resources/csv/live-test.csv");
        final Job job = JobFactory.createJob("test-job", csvFile);
        final JsonObject json = JsonObject.mapFrom(job);

        System.out.println(json.encodePrettily());
    }

}
