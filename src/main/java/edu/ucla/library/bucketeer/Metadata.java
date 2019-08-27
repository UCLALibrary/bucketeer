
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

    @CsvBindByName(column = Columns.ITEM_ARK, required = true)
    @JsonProperty
    private String myItemArk;

    @CsvBindByName(column = Columns.OBJECT_TYPE, required = true)
    @JsonProperty
    private String myObjectType;

    @CsvBindByName(column = Columns.TITLE, required = true)
    @JsonProperty
    private String myTitle;

    @CsvBindByName(column = Columns.FILE_NAME) /* Required for Work objects, not Collection(s) */
    @JsonProperty
    private String myFileName;

    @CsvBindByName(column = Columns.PARENT_ARK) /* Required for Work objects, not Collection(s) */
    @JsonProperty
    private String myParentArk;

    /* Whether image was previously 'ingested' or just recently 'failed' or 'succeeded' */

    @CsvBindByName(column = Columns.BUCKETEER_STATE)
    @JsonProperty
    private String myWorkflowState;

    /* Where we put the IIIF URL */
    @CsvBindByName(column = Columns.ACCESS_COPY)
    @JsonProperty
    private String myAccessCopy;

    /* Other allowed fields */

    @CsvBindByName(column = Columns.LOCAL_ID)
    @JsonProperty
    private String myLocalAltID;

    @CsvBindByName(column = Columns.ALT_TITLE)
    @JsonProperty
    private String myOtherAltTitle;

    @CsvBindByName(column = Columns.UNIFORM_TITLE)
    @JsonProperty
    private String myUniformAltTitle;

    @CsvBindByName(column = Columns.COPYRIGHT_STATUS)
    @JsonProperty
    private String myCopyrightStatus;

    @CsvBindByName(column = Columns.GEO_COVERAGE)
    @JsonProperty
    private String myGeoCoverage;

    @CsvBindByName(column = Columns.CREATION_DATE)
    @JsonProperty
    private String myCreationDate;

    @CsvBindByName(column = Columns.NORMALIZED_DATE)
    @JsonProperty
    private String myNormalizedDate;

    @CsvBindByName(column = Columns.CAPTION)
    @JsonProperty
    private String myCaption;

    @CsvBindByName(column = Columns.FUNDING_NOTE)
    @JsonProperty
    private String myFundingNote;

    @CsvBindByName(column = Columns.LATITUDE)
    @JsonProperty
    private String myLatitude;

    @CsvBindByName(column = Columns.LONGITUDE)
    @JsonProperty
    private String myLongitude;

    @CsvBindByName(column = Columns.DESCRIPTIVE_NOTE)
    @JsonProperty
    private String myNote;

    @CsvBindByName(column = Columns.DIMENSIONS)
    @JsonProperty
    private String myDimensions;

    @CsvBindByName(column = Columns.EXTENT)
    @JsonProperty
    private String myExtent;

    @CsvBindByName(column = Columns.MEDIUM)
    @JsonProperty
    private String myMedium;

    @CsvBindByName(column = Columns.ITEM_SEQ)
    @JsonProperty
    private String myItemSequence;

    @CsvBindByName(column = Columns.LANGUAGE)
    @JsonProperty
    private String myLanguage;

    @CsvBindByName(column = Columns.ARCHITECT)
    @JsonProperty
    private String myArchitect;

    @CsvBindByName(column = Columns.PHOTOGRAPHER)
    @JsonProperty
    private String myPhotographer;

    @CsvBindByName(column = Columns.REPOSITORY)
    @JsonProperty
    private String myRepository;

    @CsvBindByName(column = Columns.NAME_SUBJECT)
    @JsonProperty
    private String myNameSubject;

    @CsvBindByName(column = Columns.PROJECT_NAME)
    @JsonProperty
    private String myProjectName;

    @CsvBindByName(column = Columns.PLACE_OF_ORIGIN)
    @JsonProperty
    private String myPlaceOfOrigin;

    @CsvBindByName(column = Columns.PUBLISHER)
    @JsonProperty
    private String myPublisherName;

    @CsvBindByName(column = Columns.IS_PART_OF)
    @JsonProperty
    private String myIsPartOfRelation;

    @CsvBindByName(column = Columns.COUNTRY_OF_CREATION)
    @JsonProperty
    private String myCountryOfCreation;

    @CsvBindByName(column = Columns.RIGHTS_HOLDER)
    @JsonProperty
    private String myRightsHolderContact;

    @CsvBindByName(column = Columns.SUBJECT)
    @JsonProperty
    private String mySubject;

    @CsvBindByName(column = Columns.SUPPORT)
    @JsonProperty
    private String mySupport;

    @CsvBindByName(column = Columns.GENRE)
    @JsonProperty
    private String myGenre;

    @CsvBindByName(column = Columns.TYPE)
    @JsonProperty
    private String myTypeOfResource;

    @CsvBindByName(column = Columns.VISIBILITY)
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
     * Sets a IIIF access copy.
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
        // This order should match the order of the Metadata.Column fields
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

        return StringUtils.format(builder.substring(0, builder.length() - 2), Metadata.Columns.toArray());
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

    public static class Columns {

        public static final String ITEM_ARK = "Item Ark";

        public static final String FILE_NAME = "File Name";

        public static final String OBJECT_TYPE = "Object Type";

        public static final String PARENT_ARK = "Parent ARK";

        public static final String COPYRIGHT_STATUS = "Rights.copyrightStatus";

        public static final String TITLE = "Title";

        public static final String LOCAL_ID = "AltIdentifier.local";

        public static final String ALT_TITLE = "AltTitle.other";

        public static final String UNIFORM_TITLE = "AltTitle.uniform";

        public static final String GEO_COVERAGE = "Coverage.geographic";

        public static final String CREATION_DATE = "Date.creation";

        public static final String NORMALIZED_DATE = "Date.normalized";

        public static final String CAPTION = "Description.caption";

        public static final String FUNDING_NOTE = "Description.fundingNote";

        public static final String LATITUDE = "Description.latitude";

        public static final String LONGITUDE = "Description.longitude";

        public static final String DESCRIPTIVE_NOTE = "Description.note";

        public static final String DIMENSIONS = "Format.dimensions";

        public static final String EXTENT = "Format.extent";

        public static final String MEDIUM = "Format.medium";

        public static final String ITEM_SEQ = "Item Sequence";

        public static final String LANGUAGE = "Language";

        public static final String ARCHITECT = "Name.architect";

        public static final String PHOTOGRAPHER = "Name.photographer";

        public static final String REPOSITORY = "Name.repository";

        public static final String NAME_SUBJECT = "Name.subject";

        public static final String PLACE_OF_ORIGIN = "Place of origin";

        public static final String PROJECT_NAME = "Project Name";

        public static final String PUBLISHER = "Publisher.publisherName";

        public static final String IS_PART_OF = "Relation.isPartOf";

        public static final String COUNTRY_OF_CREATION = "Rights.countryCreation";

        public static final String RIGHTS_HOLDER = "Rights.rightsHolderContact";

        public static final String SUBJECT = "Subject";

        public static final String SUPPORT = "Support";

        public static final String GENRE = "Type.genre";

        public static final String TYPE = "Type.typeOfResource";

        public static final String VISIBILITY = "Visibility";

        public static final String BUCKETEER_STATE = "bucketeer_state";

        public static final String ACCESS_COPY = "access_copy";

        /**
         * Returns an array of the Metadata field values.
         *
         * @return An array of the Metadata field values
         */
        public static String[] toArray() {
            // Return the Metadata column list in the expected order
            return new String[] { ITEM_ARK, FILE_NAME, OBJECT_TYPE, PARENT_ARK, COPYRIGHT_STATUS, TITLE, LOCAL_ID,
                ALT_TITLE, UNIFORM_TITLE, GEO_COVERAGE, CREATION_DATE, NORMALIZED_DATE, CAPTION, FUNDING_NOTE,
                LATITUDE, LONGITUDE, DESCRIPTIVE_NOTE, DIMENSIONS, EXTENT, MEDIUM, ITEM_SEQ, LANGUAGE, ARCHITECT,
                PHOTOGRAPHER, REPOSITORY, NAME_SUBJECT, PLACE_OF_ORIGIN, PROJECT_NAME, PUBLISHER, IS_PART_OF,
                COUNTRY_OF_CREATION, RIGHTS_HOLDER, SUBJECT, SUPPORT, GENRE, TYPE, VISIBILITY, BUCKETEER_STATE,
                ACCESS_COPY };
        }
    }
}
