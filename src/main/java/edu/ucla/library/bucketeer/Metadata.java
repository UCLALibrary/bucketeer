
package edu.ucla.library.bucketeer;

import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.opencsv.bean.CsvBindByName;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;
import info.freelibrary.util.StringUtils;

import edu.ucla.library.bucketeer.utils.IFilePathPrefix;

/**
 * Formalized metadata recognized by Bucketeer.
 * <p>
 * <a href="https://californica.library.ucla.edu/importer_documentation/guide">Documented</a> in the Californica
 * importer. See that for the most up-to-date descriptions of what these fields are and what examples look like.
 * </p>
 */
public class Metadata {

    @JsonIgnore
    public static final String WORK_TYPE = "Work";

    @JsonIgnore
    public static final String COLLECTION_TYPE = "Collection";

    @JsonIgnore
    private static final Logger LOGGER = LoggerFactory.getLogger(Metadata.class, Constants.MESSAGES);

    /* Required fields */

    @CsvBindByName(column = "Item ARK", required = true)
    @JsonProperty
    private String myItemArk;

    @CsvBindByName(column = "Object Type", required = true)
    @JsonProperty
    private String myObjectType;

    @CsvBindByName(column = "Title", required = true)
    @JsonProperty
    private String myTitle;

    @CsvBindByName(column = "File Name") /* Required for Work objects, not Collection(s) */
    @JsonProperty
    private String myFileName;

    @CsvBindByName(column = "Parent ARK") /* Required for Work objects, not Collection(s) */
    @JsonProperty
    private String myParentArk;

    /* Whether image was previously 'ingested' or just recently 'failed' or 'succeeded' */

    @CsvBindByName(column = "bucketeer_state")
    @JsonProperty
    private String myWorkflowState;

    /* Where we put the IIIF URL */
    @CsvBindByName(column = "access_copy")
    @JsonProperty
    private String myAccessCopy;

    /* Other allowed fields */

    @CsvBindByName(column = "AltIdentifier.local")
    @JsonProperty
    private String myLocalAltID;

    @CsvBindByName(column = "AltTitle.other")
    @JsonProperty
    private String myOtherAltTitle;

    @CsvBindByName(column = "AltTitle.uniform")
    @JsonProperty
    private String myUniformAltTitle;

    @CsvBindByName(column = "Rights.copyrightStatus")
    @JsonProperty
    private String myCopyrightStatus;

    @CsvBindByName(column = "Coverage.geographic")
    @JsonProperty
    private String myGeoCoverage;

    @CsvBindByName(column = "Date.creation")
    @JsonProperty
    private String myCreationDate;

    @CsvBindByName(column = "Date.normalized")
    @JsonProperty
    private String myNormalizedDate;

    @CsvBindByName(column = "Description.caption")
    @JsonProperty
    private String myCaption;

    @CsvBindByName(column = "Description.fundingNote")
    @JsonProperty
    private String myFundingNote;

    @CsvBindByName(column = "Description.latitude")
    @JsonProperty
    private String myLatitude;

    @CsvBindByName(column = "Description.longitude")
    @JsonProperty
    private String myLongitude;

    @CsvBindByName(column = "Description.note")
    @JsonProperty
    private String myNote;

    @CsvBindByName(column = "Format.dimensions")
    @JsonProperty
    private String myDimensions;

    @CsvBindByName(column = "Format.extent")
    @JsonProperty
    private String myExtent;

    @CsvBindByName(column = "Format.medium")
    @JsonProperty
    private String myMedium;

    @CsvBindByName(column = "Item Sequence")
    @JsonProperty
    private String myItemSequence;

    @CsvBindByName(column = "Language")
    @JsonProperty
    private String myLanguage;

    @CsvBindByName(column = "Name.architect")
    @JsonProperty
    private String myArchitect;

    @CsvBindByName(column = "Name.photographer")
    @JsonProperty
    private String myPhotographer;

    @CsvBindByName(column = "Name.repository")
    @JsonProperty
    private String myRepository;

    @CsvBindByName(column = "Name.subject")
    @JsonProperty
    private String myNameSubject;

    @CsvBindByName(column = "Project Name")
    @JsonProperty
    private String myProjectName;

    @CsvBindByName(column = "Place of origin")
    @JsonProperty
    private String myPlaceOfOrigin;

    @CsvBindByName(column = "Publisher.publisherName")
    @JsonProperty
    private String myPublisherName;

    @CsvBindByName(column = "Relation.isPartOf")
    @JsonProperty
    private String myIsPartOfRelation;

    @CsvBindByName(column = "Rights.countryCreation")
    @JsonProperty
    private String myCountryOfCreation;

    @CsvBindByName(column = "Rights.rightsHolderContact")
    @JsonProperty
    private String myRightsHolderContact;

    @CsvBindByName(column = "Subject")
    @JsonProperty
    private String mySubject;

    @CsvBindByName(column = "Support")
    @JsonProperty
    private String mySupport;

    @CsvBindByName(column = "Type.genre")
    @JsonProperty
    private String myGenre;

    @CsvBindByName(column = "Type.typeOfResource")
    @JsonProperty
    private String myTypeOfResource;

    @CsvBindByName(column = "Visibility")
    @JsonProperty
    private String myVisibility;

    /**
     * A prefix to add to the file location that was passed in via the metadata.
     */
    @JsonIgnore
    private IFilePathPrefix myFilePathPrefix;

    /**
     * Gets the metadata's ID (ARK).
     *
     * @return The metadata's ID (ARK)
     */
    @JsonIgnore
    public String getID() {
        return myItemArk;
    }

    /**
     * Returns whether the metadata has been previously ingested or failed or succeeded to be ingested in this pass.
     *
     * @return Whether the metadata has been previously ingested or failed or succeeded to be ingested in this pass
     * @throws IllegalArgumentException If the value from the CSV isn't a valid state
     */
    @JsonIgnore
    public WorkflowState getWorkflowState() throws IllegalArgumentException {
        return StringUtils.trimToNull(myWorkflowState) == null ? WorkflowState.EMPTY : WorkflowState.valueOf(
                myWorkflowState.toUpperCase());
    }

    /**
     * Sets if the ingest has failed or not.
     *
     * @param aState An ingest workflow state
     */
    @JsonIgnore
    public void setWorkflowState(final WorkflowState aState) {
        myWorkflowState = aState.toString();
    }

    /**
     * Returns true if metadata is for a Work object.
     *
     * @return True if metadata is for a Work object.
     */
    @JsonIgnore
    public boolean isWork() {
        return WORK_TYPE.equals(myObjectType);
    }

    /**
     * Returns true if metadata is for a Collection object.
     *
     * @return True if metadata is for a Collection object
     */
    @JsonIgnore
    public boolean isCollection() {
        return COLLECTION_TYPE.equals(myObjectType);
    }

    /**
     * Returns true if metadata is for a Work object.
     *
     * @return True if metadata is for a Work object
     */
    @JsonIgnore
    public boolean isValid() {
        // Right now we just have works and collections
        return isWork() ? isValidWork() : isValidCollection();
    }

    /**
     * Sets a file path prefix to use with the metadata's file path.
     *
     * @param aFilePathPrefix A file path prefix
     */
    @JsonIgnore
    public void setFilePathPrefix(final IFilePathPrefix aFilePathPrefix) {
        myFilePathPrefix = aFilePathPrefix;
    }

    /**
     * Gets the source file referenced in the object's metadata.
     *
     * @return The file referenced by the object's metadata
     */
    @JsonIgnore
    public File getFile() {
        File file = new File(StringUtils.trimTo(myFileName, ""));

        if (myFilePathPrefix != null) {
            file = Paths.get(myFilePathPrefix.getPrefix(file), file.getPath()).toFile();
        }

        return file;
    }

    /**
     * Sets the metadata's source file.
     *
     * @param aFile A source file
     */
    @JsonIgnore
    public void setFile(final File aFile) {
        myFileName = aFile.getAbsolutePath();
    }

    /**
     * Tests whether the metadata has an available source file.
     *
     * @return True if a file exists; else, false
     */
    @JsonIgnore
    public boolean hasFile() {
        return getFile().exists();
    }

    /**
     * Checks whether the metadata has a valid workflow state set.
     *
     * @return True if the workflow state is valid; else, false
     */
    @JsonIgnore
    public boolean hasValidWorkflowState() {
        boolean result;

        try {
            getWorkflowState();
            result = true;
        } catch (final IllegalArgumentException details) {
            result = false;
        }

        return result;
    }

    /**
     * A IIIF access copy.
     *
     * @param aURL A IIIF access URL
     */
    @JsonIgnore
    public void setAccessCopy(final String aURL) {
        myAccessCopy = aURL;
    }

    /**
     * Returns a string representation of the object's metadata.
     *
     * @return A string representation of the object's metadata
     */
    @JsonIgnore
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        final List<String> values = Arrays.asList(myItemArk, myFileName, myObjectType, myParentArk, myCopyrightStatus,
                myTitle, myLocalAltID, myOtherAltTitle, myUniformAltTitle, myGeoCoverage, myCreationDate,
                myNormalizedDate, myCaption, myFundingNote, myLatitude, myLongitude, myNote, myDimensions, myExtent,
                myMedium, myItemSequence, myLanguage, myArchitect, myPhotographer, myRepository, myNameSubject,
                myProjectName, myPlaceOfOrigin, myPublisherName, myIsPartOfRelation, myCountryOfCreation,
                myRightsHolderContact, mySubject, mySupport, myGenre, myTypeOfResource, myVisibility, myWorkflowState,
                myAccessCopy);

        // Prepare our values for formatting into a single toString representation of the object
        values.stream().forEach(value -> {
            builder.append(StringUtils.trimToNull(value) + " [{}], ");
        });

        return StringUtils.format(builder.substring(0, builder.length() - 2), "Item Ark", "File Name", "Object Type",
                "Parent ARK", "Rights.copyrightStatus", "Title", "AltIdentifier.local", "AltTitle.other",
                "AltTitle.uniform", "Coverage.geographic", "Date.creation", "Date.normalized", "Description.caption",
                "Description.fundingNote", "Description.latitude", "Description.longitude", "Description.note",
                "Format.dimensions", "Format.extent", "Format.medium", "Item Sequence", "Language", "Name.architect",
                "Name.photographer", "Name.repository", "Name.subject", "Place of origin", "Project Name",
                "Publisher.publisherName", "Relation.isPartOf", "Rights.countryCreation",
                "Rights.rightsHolderContact", "Subject", "Support", "Type.genre", "Type.typeOfResource", "Visibility",
                "bucketeer_state", "access_copy");
    }

    @JsonIgnore
    private boolean isValidWork() {
        final String fileName = StringUtils.trimToNull(myFileName);
        final String parentArk = StringUtils.trimToNull(myParentArk);

        if (LOGGER.isDebugEnabled() && (!isWork() || fileName == null || parentArk == null || !hasFile())) {
            LOGGER.debug(MessageCodes.BUCKETEER_058, toString());
        }

        return isWork() && fileName != null && parentArk != null && hasFile();
    }

    @JsonIgnore
    private boolean isValidCollection() {
        final String fileName = StringUtils.trimToNull(myFileName);
        final String parentArk = StringUtils.trimToNull(myParentArk);

        if (LOGGER.isDebugEnabled() && (!isCollection() || fileName != null || parentArk != null)) {
            LOGGER.debug(MessageCodes.BUCKETEER_058, toString());
        }

        return isCollection() && fileName == null && parentArk == null;
    }

    /**
     * Bucketeer workflow state representation.
     */
    public enum WorkflowState {
        INGESTED, FAILED, SUCCEEDED, EMPTY;

        @Override
        public String toString() {
            return name().equals(WorkflowState.EMPTY.name()) ? "" : name().toLowerCase();
        }
    }
}
