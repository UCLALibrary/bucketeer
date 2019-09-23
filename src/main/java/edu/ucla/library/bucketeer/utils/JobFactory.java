
package edu.ucla.library.bucketeer.utils;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.opencsv.CSVReader;

import info.freelibrary.util.BufferedFileReader;
import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;
import info.freelibrary.util.StringUtils;

import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.CsvParsingException;
import edu.ucla.library.bucketeer.Item;
import edu.ucla.library.bucketeer.Job;
import edu.ucla.library.bucketeer.Job.WorkflowState;
import edu.ucla.library.bucketeer.MessageCodes;
import edu.ucla.library.bucketeer.Metadata;

/**
 * A creator of jobs.
 */
public final class JobFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(JobFactory.class, Constants.MESSAGES);

    private JobFactory() {
    }

    /**
     * Creates a batch job from a CSV file that has headers.
     *
     * @param aCsvFile A CSV file containing the job's metadata
     * @return A new batch job
     * @throws IOException If there is trouble reading the CSV file
     */
    public static Job createJob(final String aName, final File aCsvFile) throws IOException, CsvParsingException {
        final List<CsvParsingException> exceptions = new ArrayList<>();
        final Reader reader = new BufferedFileReader(aCsvFile);
        final CSVReader csvReader = new CSVReader(reader);
        final List<Item> items = new ArrayList<>();
        final Job job = new Job(aName);

        try {
            final List<String[]> metadata = csvReader.readAll();

            int bucketeerStateIndex = -1;
            int objectTypeIndex = -1;
            int accessCopyIndex = -1;
            int fileNameIndex = -1;
            int itemIdIndex = -1;

            // Store the metadata without its headers
            job.setMetadata(metadata.subList(1, metadata.size()));

            for (int rowIndex = 0; rowIndex < metadata.size(); rowIndex++) {
                final CsvParsingException csvParsingException = new CsvParsingException();
                final String[] columns = metadata.get(rowIndex);
                final Item item = new Item();

                for (int columnIndex = 0; columnIndex < columns.length; columnIndex++) {
                    // We assume first line contains the headers and remember the indices of those we care about
                    if (rowIndex == 0) {
                        switch (columns[columnIndex]) {
                            case Metadata.ITEM_ID:
                                LOGGER.debug(MessageCodes.BUCKETEER_117, columnIndex);
                                itemIdIndex = columnIndex;
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
                        if (fileNameIndex == -1) {
                            // File Name is a required header, if we don't find it complain
                            csvParsingException.addMessage(MessageCodes.BUCKETEER_122);
                            break;
                        } else if (itemIdIndex == -1) {
                            // Item ID/ARK is a required header, if we don't find it complain
                            csvParsingException.addMessage(MessageCodes.BUCKETEER_123);
                            break;
                        } else if (fileNameIndex == columnIndex) {
                            final String filePath = StringUtils.trimToNull(columns[columnIndex]);

                            // Items have files by default so it may just be that hasFile(false) has not yet been set
                            if (item.hasFile()) {
                                if (filePath != null) {
                                    item.setFilePath(columns[columnIndex]);
                                } else {
                                    item.setFilePath("");
                                }
                            }
                        } else if (itemIdIndex == columnIndex) {
                            item.setID(columns[columnIndex]);
                        } else if (bucketeerStateIndex == columnIndex) {
                            try {
                                // If item already has a workflow state, preserve it
                                item.setWorkflowState(WorkflowState.fromString(columns[columnIndex]));
                            } catch (final IllegalArgumentException details) {
                                csvParsingException.addMessage(MessageCodes.BUCKETEER_124, columns[columnIndex]);
                            }
                        } else if (accessCopyIndex == columnIndex) {
                            // If item already has an access URL, preserve it
                            item.setAccessURL(columns[columnIndex]);
                        } else if (objectTypeIndex == columnIndex) {
                            // Note if we're not expecting this column to have a source file
                            if (Metadata.COLLECTION.equals(columns[columnIndex])) {
                                item.hasFile(false);
                            }
                        }
                    }
                }

                // Skip the first headers row; there are no errors in that (we take it at face value)
                if (rowIndex != 0) {
                    // If we're supposed to have a file but don't have one, add an exception message
                    if (item.hasFile() && !item.fileExists()) {
                        csvParsingException.addMessage(MessageCodes.BUCKETEER_125, item.getID(), item.getFilePath());
                    }

                    // If we've had any exceptions so far, mark the object as failed
                    if (csvParsingException.countMessages() > 0) {
                        exceptions.add(csvParsingException);
                        item.setWorkflowState(WorkflowState.FAILED);
                    }

                    items.add(item);
                } else {
                    job.setMetadataHeader(columns);
                }
            }
        } finally {
            final StringBuilder builder = new StringBuilder();
            final Iterator<CsvParsingException> iterator = exceptions.iterator();

            // Clean up the CSV reader
            csvReader.close();

            while (iterator.hasNext()) {
                builder.append(iterator.next().getMessages());
            }

            // Write out CSV errors as warnings in the application log
            LOGGER.warn(MessageCodes.BUCKETEER_126, aName, builder.toString());
        }

        return job.setItems(items);
    }

}
