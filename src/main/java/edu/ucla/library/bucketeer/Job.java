
package edu.ucla.library.bucketeer;

import static edu.ucla.library.bucketeer.Constants.HEIGHT;
import static edu.ucla.library.bucketeer.Constants.WIDTH;

import java.io.IOException;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.ArrayList;
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
import info.freelibrary.util.warnings.JDK;
import info.freelibrary.util.warnings.PMD;

import edu.ucla.library.bucketeer.verticles.WidthHeightVerticle;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * A batch job.
 */
@JsonPropertyOrder({ "jobName", "slackHandle", "isSubsequentRun", "items", "metadataHeader", "metadata" })
public class Job implements Serializable {

    /** The job logger. */
    private static final Logger LOGGER = LoggerFactory.getLogger(Job.class, Constants.MESSAGES);

    /** The <code>serialVersionUID</code> for Job. */
    private static final long serialVersionUID = -2430620678602342169L;

    /** The Slack user handle associated with this job. */
    private String mySlackHandle;

    /** The list of items associated with this job. */
    private List<Item> myItems;

    /** The name of the job. */
    private String myJobName;

    /** The metadata associated with this job. */
    private List<String[]> myMetadata;

    /** The metadata headers associated with this job. */
    private String[] myMetadataHeader;

    /** A flag indicating if this job has been run before. */
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
    @SuppressWarnings({ PMD.CYCLOMATIC_COMPLEXITY, JDK.RAW_TYPES, JDK.DEPRECATION })
    public Future<Job> updateMetadata(final Vertx aVertxRef) {
        final List<String> missingHeaders = new ArrayList<>();
        final Promise<Job> promise = Promise.promise();
        final List<Item> items = getItems();
        final List<Future> futures = new ArrayList<>();

        int bucketeerStateIndex = findHeader(Metadata.BUCKETEER_STATE);
        int accessUrlIndex = findHeader(Metadata.IIIF_ACCESS_URL);
        int widthIndex = findHeader(Metadata.MEDIA_WIDTH);
        int heightIndex = findHeader(Metadata.MEDIA_HEIGHT);

        int nextIndex = myMetadataHeader.length;

        if (bucketeerStateIndex == -1) {
            bucketeerStateIndex = nextIndex++;
            missingHeaders.add(Metadata.BUCKETEER_STATE);
        }

        if (accessUrlIndex == -1) {
            accessUrlIndex = nextIndex++;
            missingHeaders.add(Metadata.IIIF_ACCESS_URL);
        }

        if (widthIndex == -1) {
            widthIndex = nextIndex++;
            missingHeaders.add(Metadata.MEDIA_WIDTH);
        }

        if (heightIndex == -1) {
            heightIndex = nextIndex++;
            missingHeaders.add(Metadata.MEDIA_HEIGHT);
        }

        final int additionalHeadersCount = missingHeaders.size();
        final boolean headersChanged = additionalHeadersCount > 0;
        final int newHeaderLength = myMetadataHeader.length + additionalHeadersCount;

        LOGGER.debug(MessageCodes.BUCKETEER_155, additionalHeadersCount);

        if (headersChanged) {
            final String[] additionalHeaders = missingHeaders.toArray(new String[0]);
            final String[] newHeader = new String[newHeaderLength];

            System.arraycopy(myMetadataHeader, 0, newHeader, 0, myMetadataHeader.length);
            System.arraycopy(additionalHeaders, 0, newHeader, myMetadataHeader.length, additionalHeadersCount);

            setMetadataHeader(newHeader);
        }

        for (int index = 0; index < myMetadata.size(); index++) {
            final Future<Void> rowFuture = Future.future();
            final Item item = items.get(index);
            final int rowIndex = index;

            // The row, modified or as-in, for the item's metadata
            String[] row;

            futures.add(rowFuture);
            row = myMetadata.get(index); // as-in

            if (headersChanged) {
                final String[] newRow = new String[newHeaderLength];

                System.arraycopy(row, 0, newRow, 0, row.length);
                row = newRow; // modified
            }

            // Lambdas require final variables, so we make final copies
            final String[] finalRow = row;
            final int finalBucketeerStateIndex = bucketeerStateIndex;
            final int finalAccessUrlIndex = accessUrlIndex;
            final int finalWidthIndex = widthIndex;
            final int finalHeightIndex = heightIndex;

            finalRow[finalBucketeerStateIndex] = WorkflowState.STRUCTURAL.equals(item.getWorkflowState())
                    ? WorkflowState.EMPTY.toString() : item.getWorkflowState().toString();

            finalRow[finalAccessUrlIndex] = item.getAccessURL();

            if (item.getWidth().isPresent()) {
                finalRow[finalWidthIndex] = item.getWidth().get();
            }

            if (item.getHeight().isPresent()) {
                finalRow[finalHeightIndex] = item.getHeight().get();
            }

            if (item.getWidth().isPresent() && item.getHeight().isPresent()) {
                myMetadata.set(rowIndex, finalRow);
                rowFuture.complete();
            } else {
                final JsonObject request = new JsonObject();

                if (item.hasFile()) {
                    request.put(Constants.IMAGE_ID, item.getID());
                    item.getPrefixedFilePath().ifPresent(path -> request.put(Constants.FILE_PATH, path));

                    aVertxRef.eventBus().<JsonObject>send(WidthHeightVerticle.class.getName(), request, reply -> {
                        if (reply.succeeded()) {
                            final JsonObject body = reply.result().body();
                            final String width = body.getString(WIDTH);
                            final String height = body.getString(HEIGHT);

                            if (width != null) {
                                finalRow[finalWidthIndex] = width;
                            }

                            if (height != null) {
                                finalRow[finalHeightIndex] = height;
                            }
                        } else {
                            LOGGER.error(MessageCodes.BUCKETEER_612, request.getValue(Constants.FILE_PATH));
                        }

                        myMetadata.set(rowIndex, finalRow);
                        rowFuture.complete();
                    });
                } else {
                    myMetadata.set(rowIndex, finalRow);
                    rowFuture.complete();
                }
            }
        }

        CompositeFuture.all(futures).setHandler(result -> {
            if (result.succeeded()) {
                promise.complete(this);
            } else {
                promise.fail(result.cause());
            }
        });

        return promise.future();
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
     * @return This job
     */
    @JsonProperty("isSubsequentRun")
    Job setSubsequentRun(final boolean aBool) {
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
