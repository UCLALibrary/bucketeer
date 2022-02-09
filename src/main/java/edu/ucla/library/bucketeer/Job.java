
package edu.ucla.library.bucketeer;

import java.io.IOException;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreType;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.opencsv.CSVWriter;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import io.vertx.core.json.JsonObject;

/**
 * A batch job.
 */
@JsonPropertyOrder({ "jobName", "slackHandle", "isSubsequentRun", "items", "metadataHeader", "metadata" })
public class Job implements Serializable {

    private static final Logger LOGGER = LoggerFactory.getLogger(Job.class, Constants.MESSAGES);

    /**
     * The <code>serialVersionUID</code> for Job.
     */
    private static final long serialVersionUID = -2430620678602342169L;

    private String mySlackHandle;

    private List<Item> myItems;

    private String myJobName;

    private List<String[]> myMetadata;

    private String[] myMetadataHeader;

    private boolean myJobIsSubsequentRun;

    /**
     * Creates a new batch job.
     */
    public Job() {
        // Used for deserialization
    }

    /**
     * Creates a new batch job.
     *
     * @param aName A job name
     */
    @JsonIgnore
    public Job(final String aName) {
        myJobName = aName;
    }

    /**
     * Gets the size of the job.
     *
     * @return The job size
     */
    @JsonIgnore
    public int size() {
        return myItems.size();
    }

    /**
     * Gets the failed items count of the job.
     *
     * @return The failed items count
     */
    @JsonIgnore
    public long failedItems() {
        return myItems.stream().filter(item -> item.getWorkflowState().equals(WorkflowState.FAILED)).count();
    }

    /**
     * Gets the missing items count of the job.
     *
     * @return The missing items count
     */
    @JsonIgnore
    public long missingItems() {
        return myItems.stream().filter(item -> item.getWorkflowState().equals(WorkflowState.MISSING)).count();
    }

    /**
     * Gets the number of items yet to be processed.
     *
     * @return The number of items yet to be processed
     */
    @JsonIgnore
    public int remaining() {
        int remaining = 0;

        for (final Item item : myItems) {
            if (WorkflowState.EMPTY.equals(item.getWorkflowState())) {
                remaining += 1;
            }
        }

        return remaining;
    }

    /**
     * Gets whether this is an initial or subsequent run.
     *
     * @return True if subsequent run; else, false
     */
    @JsonProperty("isSubsequentRun")
    public boolean isSubsequentRun() {
        return myJobIsSubsequentRun;
    }

    /**
     * Gets job name.
     *
     * @return The name of the job
     */
    @JsonProperty("jobName")
    public String getName() {
        return myJobName;
    }

    /**
     * Sets the job name.
     *
     * @param aJobName The job name
     * @return The job
     */
    @JsonProperty("jobName")
    public Job setName(final String aJobName) {
        myJobName = aJobName;
        return this;
    }

    /**
     * Gets the items in the job.
     *
     * @return The list of items
     */
    public List<Item> getItems() {
        return myItems;
    }

    /**
     * Sets the batch items.
     *
     * @param aItems A list of batch items
     * @return The job
     */
    public Job setItems(final List<Item> aItems) {
        myItems = aItems;
        return this;
    }

    /**
     * Sets the job metadata.
     *
     * @param aMetadata The job's metadata
     * @return The job
     */
    public Job setMetadata(final List<String[]> aMetadata) {
        myMetadata = aMetadata;
        return this;
    }

    /**
     * Gets the job metadata
     *
     * @return The job's metadata
     */
    public List<String[]> getMetadata() {
        return myMetadata;
    }

    /**
     * Sets the job's metadata header
     *
     * @param aMetadataHeader The job's metadata header
     * @return The job
     */
    public Job setMetadataHeader(final String... aMetadataHeader) {
        myMetadataHeader = aMetadataHeader.clone();
        return this;
    }

    /**
     * Gets the job's metadata header
     *
     * @return The job's metadata header
     */
    public String[] getMetadataHeader() {
        return myMetadataHeader.clone();
    }

    /**
     * Sets the Slack handle
     *
     * @param aSlackHandle A slack handle
     * @return The job
     */
    public Job setSlackHandle(final String aSlackHandle) {
        mySlackHandle = aSlackHandle;
        return this;
    }

    /**
     * Gets the Slack handle.
     *
     * @return The Slack handle
     */
    public String getSlackHandle() {
        return mySlackHandle;
    }

    /**
     * Update the job's metadata with Bucketeer State and IIIF Access URL.
     *
     * @return The job
     * @throws ProcessingException when there is trouble parsing the metadata
     */
    public Job updateMetadata() throws ProcessingException {
        final List<Item> items = getItems();

        final String[] newHeader;
        final String[] additionalHeaders;

        final int newHeaderLength;
        final int additionalHeadersCount;

        // Find the index position of our two columns: Bucketeer State and Access URL
        int bucketeerStateIndex = findHeader(Metadata.BUCKETEER_STATE);
        int accessUrlIndex = findHeader(Metadata.IIIF_ACCESS_URL);

        // Check which headers already exist in the metadata
        final boolean bucketeerStateMissing = bucketeerStateIndex == -1;
        final boolean accessUrlMissing = accessUrlIndex == -1;

        // If both headers are missing, we need to expand our headers array by two
        if (bucketeerStateMissing && accessUrlMissing) {
            additionalHeadersCount = 2;
            additionalHeaders = new String[additionalHeadersCount];
            additionalHeaders[0] = Metadata.BUCKETEER_STATE;
            additionalHeaders[1] = Metadata.IIIF_ACCESS_URL;

            bucketeerStateIndex = myMetadataHeader.length;
            accessUrlIndex = myMetadataHeader.length + 1;
        } else if (bucketeerStateMissing) {
            // If only one header is missing, we need to expand our headers array by one
            additionalHeadersCount = 1;
            additionalHeaders = new String[additionalHeadersCount];
            additionalHeaders[0] = Metadata.BUCKETEER_STATE;

            bucketeerStateIndex = myMetadataHeader.length;
        } else if (accessUrlMissing) {
            // Expand by one
            additionalHeadersCount = 1;
            additionalHeaders = new String[additionalHeadersCount];
            additionalHeaders[0] = Metadata.IIIF_ACCESS_URL;

            accessUrlIndex = myMetadataHeader.length;
        } else {
            // No change
            additionalHeadersCount = 0;
            additionalHeaders = new String[additionalHeadersCount];
        }

        LOGGER.debug(MessageCodes.BUCKETEER_155, additionalHeadersCount);
        newHeaderLength = myMetadataHeader.length + additionalHeadersCount;

        // Update the headers if there are changes
        if (bucketeerStateMissing || accessUrlMissing) {
            newHeader = new String[newHeaderLength];

            System.arraycopy(myMetadataHeader, 0, newHeader, 0, myMetadataHeader.length);
            System.arraycopy(additionalHeaders, 0, newHeader, myMetadataHeader.length, additionalHeadersCount);

            setMetadataHeader(newHeader);
        }

        // LOGGER.info("myMetadata.size() '{}'", myMetadata.size());

        // Then let's loop through the metadata and add or update columns as needed
        for (int index = 0; index < myMetadata.size(); index++) {
            final Item item = items.get(index);
            // LOGGER.info("Item index '{}'", item);

            String[] row = myMetadata.get(index);

            // If the number of columns has changed, increase our metadata row array size
            if (bucketeerStateMissing || accessUrlMissing) {
                final String[] newRow = new String[newHeaderLength];

                System.arraycopy(row, 0, newRow, 0, row.length);
                row = newRow;
            }

            // We mark structural rows with empty statuses before outputting the CSV data
            if (WorkflowState.STRUCTURAL.equals(item.getWorkflowState())) {
                row[bucketeerStateIndex] = WorkflowState.EMPTY.toString();
            } else {
                row[bucketeerStateIndex] = item.getWorkflowState().toString();
            }

            row[accessUrlIndex] = item.getAccessURL();
            myMetadata.set(index, row);
        }

        return this;
    }

    /**
     * Finds the index of the header in the header row.
     *
     * @param aHeader The header to find
     * @return The index of the header if it exists, otherwise -1
     */
    int findHeader(final String aHeader) {
        Objects.requireNonNull(myMetadataHeader);
        Objects.requireNonNull(aHeader);

        for (int index = 0; index < myMetadataHeader.length; index++) {
            if (aHeader.equals(myMetadataHeader[index])) {
                LOGGER.debug(MessageCodes.BUCKETEER_153, aHeader, index);
                return index;
            }
        }

        return -1;
    }

    /**
     * Converts a Job to a CSV string.
     *
     * @return A string in CSV format
     * @throws IOException
     */
    @JsonIgnore
    public String toCSV() throws IOException {
        final StringWriter stringWriter = new StringWriter();
        final CSVWriter csvWriter = new CSVWriter(stringWriter);

        // Let's be explicit and put all values in quotes
        csvWriter.writeNext(getMetadataHeader(), true);
        csvWriter.writeAll(getMetadata(), true);
        csvWriter.close();

        return stringWriter.toString();
    }

    /**
     * Returns a JSON representation of the Job object. To go from a JsonObject representation of Job back to a Job
     * object, use: <code>final Job job = jsonObject.mapTo(Job.class);</code>.
     *
     * @return A JSON representation of a job
     */
    @JsonIgnore
    public JsonObject toJSON() {
        return JsonObject.mapFrom(this);
    }

    /**
     * Sets whether this is an initial or subsequent run. If this is a subsequent run, it marks anything that has
     * already been successfully processed as ingested so that newly processed items can be marked as successful.
     *
     * @param aBool True if subsequent run; else, false
     */
    @JsonProperty("isSubsequentRun")
    Job isSubsequentRun(final boolean aBool) {
        myJobIsSubsequentRun = aBool;
        return this;
    }

    /**
     * Bucketeer workflow state representation.
     */
    @JsonIgnoreType
    public enum WorkflowState {

        INGESTED, FAILED, SUCCEEDED, EMPTY, MISSING, STRUCTURAL;

        /**
         * Creates a new WorkflowState from the supplied string.
         *
         * @param aString A string representation of a WorkflowState
         * @return A WorkflowState
         */
        public static WorkflowState fromString(final String aString) {
            for (final WorkflowState state : WorkflowState.values()) {
                if (state.toString().equalsIgnoreCase(aString)) {
                    return state;
                }
            }

            throw new IllegalArgumentException(aString);
        }

        @Override
        public String toString() {
            return name().equals(WorkflowState.EMPTY.name()) ? "" : name().toLowerCase(Locale.US);
        }
    }

}
