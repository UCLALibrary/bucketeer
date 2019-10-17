
package edu.ucla.library.bucketeer;

import java.io.Serializable;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreType;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import io.vertx.core.json.JsonObject;

/**
 * A batch job.
 */
@JsonPropertyOrder({ "jobName", "slackHandle", "isSubsequentRun", "items", "metadataHeader", "metadata" })
public class Job implements Serializable {

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
     */
    @JsonIgnore
    public int size() {
        return myItems.size();
    }

    /**
     * Gets the number of items yet to be processed.
     *
     * @return The number of items yet to be processed
     */
    @JsonIgnore
    public int remaining() {
        int remaining = 0;

        // If this is the first run, only process unprocessed items
        if (!isSubsequentRun()) {
            for (final Item item : myItems) {
                if (WorkflowState.EMPTY.equals(item.getWorkflowState())) {
                    remaining += 1;
                }
            }
        } else {
            // If this is a subsequent run, only process failures
            for (final Item item : myItems) {
                if (WorkflowState.FAILED.equals(item.getWorkflowState())) {
                    remaining += 1;
                }
            }
        }

        return remaining;
    }

    /**
     * Mark anything that's previously been successfully processed as ingested.
     */
    @JsonIgnore
    public void markIngestedItems() {
        if (myItems != null) {
            for (final Item item : myItems) {
                if (WorkflowState.SUCCEEDED.equals(item.getWorkflowState())) {
                    item.setWorkflowState(WorkflowState.INGESTED);
                }
            }
        }
    }

    /**
     * Sets whether this is an initial or subsequent run. If this is a subsequent run, it marks anything that has
     * already been successfully processed as ingested so that newly processed items can be marked as successful.
     *
     * @param aBool True if subsequent run; else, false
     */
    @JsonProperty("isSubsequentRun")
    public Job isSubsequentRun(final boolean aBool) {
        myJobIsSubsequentRun = aBool;

        if (myJobIsSubsequentRun) {
            markIngestedItems();
        }

        return this;
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
     */
    @JsonProperty("jobName")
    public Job setName(final String aJobName) {
        myJobName = aJobName;
        return this;
    }

    /**
     * Gets the items in the job.
     *
     * @return
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
     * Bucketeer workflow state representation.
     */
    @JsonIgnoreType
    public enum WorkflowState {
        INGESTED, FAILED, SUCCEEDED, EMPTY;

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
