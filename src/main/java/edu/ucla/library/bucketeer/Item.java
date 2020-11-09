
package edu.ucla.library.bucketeer;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import info.freelibrary.util.StringUtils;

import edu.ucla.library.bucketeer.Job.WorkflowState;
import edu.ucla.library.bucketeer.utils.IFilePathPrefix;

import io.vertx.core.json.JsonObject;

/**
 * A batch job item.
 */
@JsonPropertyOrder({ "id", "filePath", "accessURL", "workflowState", "filePathPrefix" })
public class Item implements Serializable {

    /**
     * The <code>serialVersionUID</code> of the Item.
     */
    private static final long serialVersionUID = 2062164135217237338L;

    private String myID;

    private Optional<String> myFilePath;

    private Optional<String> myPrefixedFilePath;

    private String myAccessURL;

    private boolean hasImageFile = true;

    @JsonProperty("workflowState")
    private String myWorkflowState = WorkflowState.EMPTY.toString();

    @JsonProperty("filePathPrefix")
    private IFilePathPrefix myFilePathPrefix;

    /**
     * Creates new item.
     */
    public Item() {
        // Used in deserialization and testing
        myFilePath = Optional.empty();
    }

    /**
     * Creates a new item.
     *
     * @param aID An item ID
     * @param aFilePath An item's file path
     */
    @JsonIgnore
    public Item(final String aID, final String aFilePath) {
        myFilePath = Optional.ofNullable(aFilePath);
        if (myFilePath.isEmpty()) {
            hasImageFile = false;
        }
        myID = aID;
    }

    /**
     * Sets a file path prefix to use with the metadata's file path.
     *
     * @param aFilePathPrefix A file path prefix
     * @return The item
     */
    @JsonIgnore
    public Item setFilePathPrefix(final IFilePathPrefix aFilePathPrefix) {
        myFilePathPrefix = aFilePathPrefix;
        return this;
    }

    /**
     * Gets the file path prefix for this job.
     *
     * @return The job's file path prefix
     */
    @JsonIgnore
    public IFilePathPrefix getFilePathPrefix() {
        return myFilePathPrefix;
    }

    /**
     * Returns whether the item has been previously ingested or failed or succeeded to be ingested in this pass.
     *
     * @return Whether the item has been previously ingested or failed or succeeded to be ingested in this pass
     * @throws IllegalArgumentException If the value from the CSV isn't a valid state
     */
    @JsonIgnore
    public WorkflowState getWorkflowState() throws IllegalArgumentException {
        return StringUtils.trimToNull(myWorkflowState) == null ? WorkflowState.EMPTY
                : WorkflowState.valueOf(myWorkflowState.toUpperCase(Locale.US));
    }

    /**
     * Sets if the ingest has failed or not.
     *
     * @param aState An ingest workflow state
     * @return The item
     */
    @JsonIgnore
    public Item setWorkflowState(final WorkflowState aState) {
        myWorkflowState = aState.toString();
        return this;
    }

    /**
     * Gets the item ID.
     *
     * @return The ID item
     */
    public String getID() {
        return myID;
    }

    /**
     * Sets the item ID.
     *
     * @param aID
     * @return The item
     */
    public Item setID(final String aID) {
        myID = aID;
        return this;
    }

    /**
     * Gets the access copy URL.
     *
     * @return The access copy URL
     */
    public String getAccessURL() {
        return myAccessURL;
    }

    /**
     * Sets the access copy URL.
     *
     * @param aAccessURL The access copy URL
     * @return The item
     */
    public Item setAccessURL(final String aAccessURL) {
        myAccessURL = aAccessURL;
        return this;
    }

    /**
     * Gets the source file referenced in the object's metadata.
     *
     * @return The file referenced by the object's metadata
     */
    @JsonIgnore
    public Optional<File> getFile() {
        final String filePath;
        final File file;

        if (!hasFile() || myFilePath.isEmpty()) {
            return Optional.empty();
        } else {
            if (myFilePathPrefix != null) {
                filePath = myFilePath.get();
                file = Paths.get(myFilePathPrefix.getPrefix(new File(filePath)), filePath).toFile();
            } else {
                file = new File(myFilePath.get());
            }

            return Optional.of(file);
        }
    }

    /**
     * Returns the prefixed file name.
     *
     * @return The absolute file path of the item's source file
     * @throws IOException If there is trouble resolving the path to the file
     */
    @JsonIgnore
    public Optional<String> getPrefixedFilePath() {
        if (myPrefixedFilePath == null) {
            final Optional<File> file = getFile();

            if (file.isEmpty()) {
                myPrefixedFilePath = Optional.empty();
            } else {
                myPrefixedFilePath = Optional.of(Paths.get(file.get().getAbsolutePath()).normalize().toString());
            }
        }

        return myPrefixedFilePath;
    }

    /**
     * Gets the non-prefixed file path.
     *
     * @return The file path
     */
    public Optional<String> getFilePath() {
        return myFilePath;
    }

    /**
     * Sets the non-prefixed file path.
     *
     * @param aFilePath A file path
     * @return The item
     */
    public Item setFilePath(final Optional<String> aFilePath) {
        myFilePath = Objects.requireNonNull(aFilePath);
        myPrefixedFilePath = null;

        return this;
    }

    /**
     * Gets whether this item should have an image file.
     *
     * @return Whether this item should have an image file
     */
    @JsonIgnore
    public boolean hasFile() {
        return hasImageFile;
    }

    /**
     * Sets whether this item is supposed to have an image file.
     *
     * @param aStructuralObj True if object is structural and doesn't have a source file
     * @return The item
     */
    @JsonIgnore
    public Item isStructural(final boolean aStructuralObj) {
        if (aStructuralObj) {
            myWorkflowState = WorkflowState.STRUCTURAL.toString();
        }

        hasImageFile = !aStructuralObj;
        return this;
    }

    /**
     * Returns a JSON representation of the item. To go from a JsonObject representation of Item back to an Item object,
     * use: <code>final Item item = jsonObject.mapTo(Item.class);</code>.
     *
     * @return A JSON representation of a job
     */
    @JsonIgnore
    public JsonObject toJSON() {
        return JsonObject.mapFrom(this);
    }

}
