
package edu.ucla.library.bucketeer;

import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

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

    public static final String WORK_TYPE = "Work";

    public static final String COLLECTION_TYPE = "Collection";

    private static final Logger LOGGER = LoggerFactory.getLogger(Metadata.class, Constants.MESSAGES);

    private String mySlackHandle;

    /* Required fields */

    @CsvBindByName(column = "Item ARK", required = true)
    private String myItemArk;

    @CsvBindByName(column = "Object Type", required = true)
    private String myObjectType;

    @CsvBindByName(column = "Title", required = true)
    private String myTitle;

    @CsvBindByName(column = "File Name") /* Required for Work objects, not Collection(s) */
    private String myFileName;

    @CsvBindByName(column = "Parent ARK") /* Required for Work objects, not Collection(s) */
    private String myParentArk;

    /* Whether image was previously 'ingested' or just recently 'failed' or 'succeeded' */

    @CsvBindByName(column = "bucketeer_state")
    private String myWorkflowState;

    /* Where we put the IIIF URL */
    @CsvBindByName(column = "access_copy")
    private String myAccessCopy;

    /* Other allowed fields */

    @CsvBindByName(column = "AltIdentifier.local")
    private String myLocalAltID;

    @CsvBindByName(column = "AltTitle.other")
    private String myOtherAltTitle;

    @CsvBindByName(column = "AltTitle.uniform")
    private String myUniformAltTitle;

    @CsvBindByName(column = "Rights.copyrightStatus")
    private String myCopyrightStatus;

    @CsvBindByName(column = "Coverage.geographic")
    private String myGeoCoverage;

    @CsvBindByName(column = "Date.creation")
    private String myCreationDate;

    @CsvBindByName(column = "Date.normalized")
    private String myNormalizedDate;

    @CsvBindByName(column = "Description.caption")
    private String myCaption;

    @CsvBindByName(column = "Description.fundingNote")
    private String myFundingNote;

    @CsvBindByName(column = "Description.latitude")
    private String myLatitude;

    @CsvBindByName(column = "Description.longitude")
    private String myLongitude;

    @CsvBindByName(column = "Description.note")
    private String myNote;

    @CsvBindByName(column = "Format.dimensions")
    private String myDimensions;

    @CsvBindByName(column = "Format.extent")
    private String myExtent;

    @CsvBindByName(column = "Format.medium")
    private String myMedium;

    @CsvBindByName(column = "Item Sequence")
    private String myItemSequence;

    @CsvBindByName(column = "Language")
    private String myLanguage;

    @CsvBindByName(column = "Name.architect")
    private String myArchitect;

    @CsvBindByName(column = "Name.photographer")
    private String myPhotographer;

    @CsvBindByName(column = "Name.repository")
    private String myRepository;

    @CsvBindByName(column = "Name.subject")
    private String myNameSubject;

    @CsvBindByName(column = "Project Name")
    private String myProjectName;

    @CsvBindByName(column = "Place of origin")
    private String myPlaceOfOrigin;

    @CsvBindByName(column = "Publisher.publisherName")
    private String myPublisherName;

    @CsvBindByName(column = "Relation.isPartOf")
    private String myIsPartOfRelation;

    @CsvBindByName(column = "Rights.countryCreation")
    private String myCountryOfCreation;

    @CsvBindByName(column = "Rights.rightsHolderContact")
    private String myRightsHolderContact;

    @CsvBindByName(column = "Subject")
    private String mySubject;

    @CsvBindByName(column = "Support")
    private String mySupport;

    @CsvBindByName(column = "Type.genre")
    private String myGenre;

    @CsvBindByName(column = "Type.typeOfResource")
    private String myTypeOfResource;

    @CsvBindByName(column = "Visibility")
    private String myVisibility;

    /**
     * A prefix to add to the file location that was passed in via the metadata.
     */
    private IFilePathPrefix myFilePathPrefix;

    /**
     * Gets the metadata's ID (ARK).
     *
     * @return The metadata's ID (ARK)
     */
    public String getID() {
        return myItemArk;
    }

    /**
     * Gets the slackHandle
     *
     * @return the slackHandle
     */
    public String getSlackHandle() {
        return mySlackHandle;
    }

    /**
     * Sets the slackHandle
     *
     * @return the slackHandle
     */
    public Metadata setSlackHandle(final String aSlackHandle) {
        mySlackHandle = aSlackHandle;
        return this;
    }

    /**
     * Returns whether the metadata has been previously ingested or failed or succeeded to be ingested in this pass.
     *
     * @return Whether the metadata has been previously ingested or failed or succeeded to be ingested in this pass
     * @throws IllegalArgumentException If the value from the CSV isn't a valid state
     */
    public WorkflowState getWorkflowState() throws IllegalArgumentException {
        return StringUtils.trimToNull(myWorkflowState) == null ? WorkflowState.EMPTY : WorkflowState.valueOf(
                myWorkflowState.toUpperCase());
    }

    /**
     * Sets if the ingest has failed or not.
     *
     * @param aState An ingest workflow state
     */
    public void setWorkflowState(final WorkflowState aState) {
        myWorkflowState = aState.toString();
    }

    /**
     * Returns true if metadata is for a Work object.
     *
     * @return True if metadata is for a Work object.
     */
    public boolean isWork() {
        return WORK_TYPE.equals(myObjectType);
    }

    /**
     * Returns true if metadata is for a Collection object.
     *
     * @return True if metadata is for a Collection object
     */
    public boolean isCollection() {
        return COLLECTION_TYPE.equals(myObjectType);
    }

    /**
     * Returns true if metadata is for a Work object.
     *
     * @return True if metadata is for a Work object
     */
    public boolean isValid() {
        // Right now we just have works and collections
        return isWork() ? isValidWork() : isValidCollection();
    }

    /**
     * Sets a file path prefix to use with the metadata's file path.
     *
     * @param aFilePathPrefix A file path prefix
     */
    public void setFilePathPrefix(final IFilePathPrefix aFilePathPrefix) {
        myFilePathPrefix = aFilePathPrefix;
    }

    /**
     * Gets the source file referenced in the object's metadata.
     *
     * @return The file referenced by the object's metadata
     */
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
    public void setFile(final File aFile) {
        myFileName = aFile.getAbsolutePath();
    }

    /**
     * Tests whether the metadata has an available source file.
     *
     * @return True if a file exists; else, false
     */
    public boolean hasFile() {
        return getFile().exists();
    }

    /**
     * Checks whether the metadata has a valid workflow state set.
     *
     * @return True if the workflow state is valid; else, false
     */
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
     * Returns a string representation of the object's metadata.
     *
     * @return A string representation of the object's metadata
     */
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

    private boolean isValidWork() {
        final String fileName = StringUtils.trimToNull(myFileName);
        final String parentArk = StringUtils.trimToNull(myParentArk);

        if (LOGGER.isDebugEnabled() && (!isWork() || fileName == null || parentArk == null || !hasFile())) {
            LOGGER.debug(MessageCodes.BUCKETEER_058, toString());
        }

        return isWork() && fileName != null && parentArk != null && hasFile();
    }

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
