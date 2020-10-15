
package edu.ucla.library.bucketeer;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.opencsv.CSVReader;

import info.freelibrary.util.BufferedFileReader;
import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;
import info.freelibrary.util.StringUtils;

import edu.ucla.library.bucketeer.Job.WorkflowState;
import edu.ucla.library.bucketeer.utils.GenericFilePathPrefix;
import edu.ucla.library.bucketeer.utils.IFilePathPrefix;

import io.vertx.core.json.jackson.DatabindCodec;

/**
 * A creator of jobs.
 */
@SuppressWarnings("PMD.NonThreadSafeSingleton") // #FIXME but ignoring for now since it's not related to this ticket
public final class JobFactory {

    static {
        DatabindCodec.mapper().findAndRegisterModules();
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(JobFactory.class, Constants.MESSAGES);

    private static JobFactory myJobFactory;

    private IFilePathPrefix myFilePathPrefix;

    private JobFactory() {
    }

    /**
     * Gets an instance of the JobFactory.
     *
     * @return An instance of the JobFactory
     */
    public static JobFactory getInstance() {
        if (myJobFactory == null) {
            myJobFactory = new JobFactory().setPathPrefix(new GenericFilePathPrefix());
        }

        return myJobFactory;
    }

    /**
     * Sets the IFilePathPrefix for the JobFactory.
     *
     * @param aFilePathPrefix The file path prefix
     * @return The JobFactory instance
     */
    public JobFactory setPathPrefix(final IFilePathPrefix aFilePathPrefix) {
        myFilePathPrefix = aFilePathPrefix;
        return this;
    }

    /**
     * Creates a batch job from a CSV file that has headers.
     *
     * @param aName A job name
     * @param aCsvFile A CSV file containing the job's metadata
     * @return A new batch job
     * @throws IOException If there is trouble reading the CSV file
     * @throws ProcessingException If there is trouble processing the CSV file
     */
    public Job createJob(final String aName, final File aCsvFile) throws IOException, ProcessingException {
        return createJob(aName, aCsvFile, false);
    }

    /**
     * Creates a batch job from a CSV file that has headers.
     *
     * @param aName A job name
     * @param aCsvFile A CSV file containing the job's metadata
     * @param aSubsequentRun If the job to be created is a subsequent run
     * @return A new batch job
     * @throws IOException If there is trouble reading the CSV file
     * @throws ProcessingException If there is trouble processing the CSV file
     */
    @SuppressWarnings({ "PMD.ExcessiveMethodLength", "PMD.NcssCount" })
    public Job createJob(final String aName, final File aCsvFile, final boolean aSubsequentRun)
            throws IOException, ProcessingException {
        final Reader reader = new BufferedFileReader(aCsvFile);
        final CSVReader csvReader = new CSVReader(reader);
        final List<Item> items = new ArrayList<>();
        final Job job = new Job(aName).isSubsequentRun(aSubsequentRun);

        try {
            final List<String[]> metadata = csvReader.readAll();

            /* Whether or not this item has a viewingHint (optional) */
            boolean hasViewingHint = false;

            /* The type of object of our item (optional) */
            String objectType = "";

            // These are the columns we care about
            int bucketeerStateIndex = -1;
            int objectTypeIndex = -1;
            int accessCopyIndex = -1;
            int fileNameIndex = -1;
            int itemIdIndex = -1;
            int viewingHintIndex = -1;

            // Store the metadata without its headers
            job.setMetadata(metadata.subList(1, metadata.size()));

            if (hasHeaderErrors(metadata.get(0), aName)) {
                throw new ProcessingException(LOGGER.getMessage(MessageCodes.BUCKETEER_517, aCsvFile.getName(), aName));
            } else {

                // Cycle through all the rows in the CSV file (the first row has headers, the rest have values)
                for (int rowIndex = 0; rowIndex < metadata.size(); rowIndex++) {
                    final ProcessingException error = new ProcessingException();
                    final String[] columns = metadata.get(rowIndex);
                    final Item item = new Item();

                    // Set the IFilePathPrefix implementation that Item(s) will use to access files
                    item.setFilePathPrefix(myFilePathPrefix);

                    // Get the index positions of all the columns we care about
                    for (int columnIndex = 0; columnIndex < columns.length; columnIndex++) {
                        // We assume first line contains the headers and remember the indices of those we care about
                        if (rowIndex == 0) {
                            switch (columns[columnIndex]) {
                                case Metadata.ITEM_ID:
                                    LOGGER.debug(MessageCodes.BUCKETEER_117, columnIndex);
                                    itemIdIndex = columnIndex;
                                    break;
                                case Metadata.VIEWING_HINT:
                                    LOGGER.debug(MessageCodes.BUCKETEER_152, columnIndex);
                                    viewingHintIndex = columnIndex;
                                    break;
                                case Metadata.FILE_NAME:
                                    LOGGER.debug(MessageCodes.BUCKETEER_118, columnIndex);
                                    fileNameIndex = columnIndex;
                                    break;
                                case Metadata.OBJECT_TYPE:
                                    LOGGER.debug(MessageCodes.BUCKETEER_119, columnIndex);
                                    objectTypeIndex = columnIndex;
                                    break;
                                case Metadata.BUCKETEER_STATE:
                                    LOGGER.debug(MessageCodes.BUCKETEER_120, columnIndex);
                                    bucketeerStateIndex = columnIndex;
                                    break;
                                case Metadata.IIIF_ACCESS_URL:
                                    LOGGER.debug(MessageCodes.BUCKETEER_121, columnIndex);
                                    accessCopyIndex = columnIndex;
                                    break;
                                default:
                                    break;
                            }
                        } else {
                            // Then we use the indices of the columns we care about to extract and/or check the values
                            if (fileNameIndex == -1) {
                                // File Name is a required header, if we don't find it complain
                                error.addMessage(MessageCodes.BUCKETEER_122);
                                break;
                            } else if (itemIdIndex == -1) {
                                // Item ID/ARK is a required header, if we don't find it complain
                                error.addMessage(MessageCodes.BUCKETEER_123);
                                break;
                            } else if (fileNameIndex == columnIndex) {
                                item.setFilePath(Optional.ofNullable(StringUtils.trimToNull(columns[columnIndex])));
                            } else if (viewingHintIndex == columnIndex) {
                                final String viewingHint = columns[columnIndex];

                                if (StringUtils.trimToNull(viewingHint) != null) {
                                    hasViewingHint = true;
                                }
                            } else if (itemIdIndex == columnIndex) {
                                item.setID(columns[columnIndex]);
                            } else if (bucketeerStateIndex == columnIndex) {
                                try {
                                    // Structural state is new so let's not overwrite with older values
                                    if (!WorkflowState.STRUCTURAL.equals(item.getWorkflowState())) {
                                        item.setWorkflowState(WorkflowState.fromString(columns[columnIndex]));
                                    }
                                } catch (final IllegalArgumentException details) {
                                    // Adding an error sets the workflow state to failed
                                    error.addMessage(MessageCodes.BUCKETEER_124, columns[columnIndex]);
                                }
                            } else if (accessCopyIndex == columnIndex) {
                                item.setAccessURL(columns[columnIndex]);
                            } else if (objectTypeIndex == columnIndex) {
                                if (Metadata.COLLECTION.equalsIgnoreCase(columns[columnIndex])) {
                                    item.isStructural(true);
                                } else if (Metadata.WORK.equalsIgnoreCase(columns[columnIndex])) {
                                    objectType = Metadata.WORK.toString();
                                }
                            }
                        }
                    }

                    // Skip the first headers row; there are no errors in that (we take it at face value)
                    if (rowIndex != 0) {
                        final WorkflowState state = item.getWorkflowState();

                        // Cf. https://github.com/UCLALibrary/bucketeer/blob/master/docs/loading-CSVs.md
                        if (job.isSubsequentRun()) {
                            if (WorkflowState.FAILED.equals(state) || WorkflowState.MISSING.equals(state)) {
                                item.setWorkflowState(WorkflowState.EMPTY);
                            } else if (WorkflowState.SUCCEEDED.equals(state)) {
                                item.setWorkflowState(WorkflowState.INGESTED);
                            }
                        } else if (!WorkflowState.STRUCTURAL.equals(state)) {
                            item.setWorkflowState(WorkflowState.EMPTY);
                        }

                        if (Metadata.WORK.toString().equals(objectType) && hasViewingHint) {
                            item.isStructural(true);

                            // Reset our structural row indicators
                            hasViewingHint = false;
                            objectType = "";
                        }

                        // If it's supposed to have a file, check to see if it does and fail if it doesn't
                        if (item.hasFile() && WorkflowState.EMPTY.equals(item.getWorkflowState())) {
                            final Optional<File> file = item.getFile();

                            if (file.isEmpty()) {
                                item.setWorkflowState(WorkflowState.MISSING);
                                error.addMessage(MessageCodes.BUCKETEER_125, item.getID(), job.getName());
                            } else if (!file.get().exists()) {
                                error.addMessage(MessageCodes.BUCKETEER_146, item.getID(), file.get());
                            }
                        }

                        // If we've had any exceptions so far, mark the object as failed and store the exception
                        if (error.countMessages() > 0) {
                            // Let missing be the primary error if there are other issues
                            if (!WorkflowState.MISSING.equals(item.getWorkflowState())) {
                                item.setWorkflowState(WorkflowState.FAILED);
                            }

                            // TODO: Store the exception in the item (have to change object to make this happen)
                        }

                        // Add this item to the job's items list
                        items.add(item);
                    } else {
                        job.setMetadataHeader(columns);
                    }
                }
            }

        } finally {
            csvReader.close();
        }

        return job.setItems(items);
    }

    private boolean hasHeaderErrors(final String[] aHeaders, final String aJobName) {
        boolean hasErrors = false;

        // Counts of instances of header fields
        int bucketeerStateCount = 0;
        int objectTypeCount = 0;
        int accessCopyCount = 0;
        int fileNameCount = 0;
        int itemIdCount = 0;
        int viewingHintCount = 0;

        for (final String aHeader : aHeaders) {
            switch (aHeader) {
                case Metadata.ITEM_ID:
                    itemIdCount += 1;
                    break;
                case Metadata.VIEWING_HINT:
                    viewingHintCount += 1;
                    break;
                case Metadata.FILE_NAME:
                    fileNameCount += 1;
                    break;
                case Metadata.OBJECT_TYPE:
                    objectTypeCount += 1;
                    break;
                case Metadata.BUCKETEER_STATE:
                    bucketeerStateCount += 1;
                    break;
                case Metadata.IIIF_ACCESS_URL:
                    accessCopyCount += 1;
                    break;
                default:
                    break;
            }
        }
        if (itemIdCount > 1) {
            LOGGER.error(LOGGER.getMessage(MessageCodes.BUCKETEER_516, aJobName, "Item ARK"));
            hasErrors = true;
        }
        if (viewingHintCount > 1) {
            LOGGER.error(LOGGER.getMessage(MessageCodes.BUCKETEER_516, aJobName, "viewingHint"));
            hasErrors = true;
        }
        if (fileNameCount > 1) {
            LOGGER.error(LOGGER.getMessage(MessageCodes.BUCKETEER_516, aJobName, "File Name"));
            hasErrors = true;
        }
        if (objectTypeCount > 1) {
            LOGGER.error(LOGGER.getMessage(MessageCodes.BUCKETEER_516, aJobName, "Object Type"));
            hasErrors = true;
        }
        if (bucketeerStateCount > 1) {
            LOGGER.error(LOGGER.getMessage(MessageCodes.BUCKETEER_516, aJobName, "Bucketeer State"));
            hasErrors = true;
        }
        if (accessCopyCount > 1) {
            LOGGER.error(LOGGER.getMessage(MessageCodes.BUCKETEER_516, aJobName, "IIIF Access URL"));
            hasErrors = true;
        }

        return hasErrors;
    }

}
