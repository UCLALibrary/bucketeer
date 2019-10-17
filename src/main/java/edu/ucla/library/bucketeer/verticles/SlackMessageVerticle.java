
package edu.ucla.library.bucketeer.verticles;

import static edu.ucla.library.bucketeer.Constants.MESSAGES;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import com.github.seratch.jslack.Slack;
import com.github.seratch.jslack.api.methods.SlackApiException;
import com.github.seratch.jslack.api.methods.response.chat.ChatPostMessageResponse;
import com.github.seratch.jslack.api.methods.response.files.FilesUploadResponse;
import com.opencsv.CSVWriter;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.bucketeer.Config;
import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.CsvParsingException;
import edu.ucla.library.bucketeer.Item;
import edu.ucla.library.bucketeer.Job;
import edu.ucla.library.bucketeer.MessageCodes;
import edu.ucla.library.bucketeer.Metadata;
import edu.ucla.library.bucketeer.Op;
import edu.ucla.library.bucketeer.utils.CodeUtils;
import io.vertx.core.json.JsonObject;

/**
 * Accepts and forwards messages to a configured Slack channel.
 */
public class SlackMessageVerticle extends AbstractBucketeerVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(SlackMessageVerticle.class, MESSAGES);

    @Override
    public void start() {
        final JsonObject config = config();

        if (LOGGER.isDebugEnabled()) {
            final String threadName = Thread.currentThread().getName();
            final String className = SlackMessageVerticle.class.getSimpleName();

            LOGGER.debug(MessageCodes.BUCKETEER_004, className, threadName);
        }

        LOGGER.debug(MessageCodes.BUCKETEER_085);

        // Set up the Slack client
        final Slack slack = Slack.getInstance();

        // Create a handler to listen for messages that should be forwarded on to a Slack channel
        getJsonConsumer().handler(message -> {
            final JsonObject json = message.body();
            final String slackMessageText = json.getString(Constants.SLACK_MESSAGE_TEXT);
            final String slackChannelID = json.getString(Config.SLACK_CHANNEL_ID);
            final String botToken = config.getString(Config.SLACK_OAUTH_TOKEN);

            if (json.containsKey(Constants.BATCH_METADATA)) {
                final String jobName = json.getString(Constants.JOB_NAME);

                LOGGER.debug(MessageCodes.BUCKETEER_091, slackMessageText, slackChannelID);

                try {
                    final JsonObject jsonObject = json.getJsonObject(Constants.BATCH_METADATA);
                    final Job job = jsonObject.mapTo(Job.class);
                    final byte[] bytes = writeToCsv(updateMetadata(job)).getBytes(StandardCharsets.UTF_8);
                    final List<String> channels = Arrays.asList(new String[] { slackChannelID });
                    final FilesUploadResponse response = slack.methods(botToken).filesUpload(post -> post.channels(
                            channels).fileData(bytes).filename(jobName + ".csv").filetype("csv").initialComment(
                                    slackMessageText).title(jobName));
                    // The above 'filetype' comes from --> https://api.slack.com/types/file#file_types

                    if (response.isOk()) {
                        LOGGER.debug(MessageCodes.BUCKETEER_087, slackChannelID, response.toString());
                        message.reply(Op.SUCCESS);
                    } else {
                        message.fail(CodeUtils.getInt(MessageCodes.BUCKETEER_090), response.getError());
                    }
                } catch (IOException | SlackApiException | CsvParsingException details) {
                    message.fail(CodeUtils.getInt(MessageCodes.BUCKETEER_089), details.getMessage());
                }
            } else {
                try {
                    final ChatPostMessageResponse response = slack.methods(botToken).chatPostMessage(post -> post
                            .channel(slackChannelID).text(slackMessageText));

                    if (response.isOk()) {
                        LOGGER.debug(MessageCodes.BUCKETEER_087, slackChannelID, response.toString());
                        message.reply(Op.SUCCESS);
                    } else {
                        message.fail(CodeUtils.getInt(MessageCodes.BUCKETEER_090), response.getError());
                    }
                } catch (IOException | SlackApiException details) {
                    message.fail(CodeUtils.getInt(MessageCodes.BUCKETEER_089), details.getMessage());
                }
            }

        });
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }

    /**
     * Update the job's metadata before it's turned back into CSV and output to Slack.
     *
     * @param aJob A job whose metadata we want to update
     * @return The job with the updated metadata
     */
    private Job updateMetadata(final Job aJob) throws CsvParsingException {
        final List<String[]> metadata = aJob.getMetadata();
        final List<Item> items = aJob.getItems();

        String[] metadataHeader = aJob.getMetadataHeader();
        int bucketeerStateIndex = -1;
        int accessUrlIndex = -1;

        // Find the index position of our two columns: Bucketeer State and Access URL
        for (int headerIndex = 0; headerIndex < metadataHeader.length; headerIndex++) {
            if (Metadata.IIIF_ACCESS_URL.equals(metadataHeader[headerIndex])) {
                accessUrlIndex = headerIndex;
            } else if (Metadata.BUCKETEER_STATE.equals(metadataHeader[headerIndex])) {
                bucketeerStateIndex = headerIndex;
            }
        }

        // If both headers are missing, we need to expand our headers array by two
        if (bucketeerStateIndex == -1 && accessUrlIndex == -1) {
            final String[] newHeader = new String[metadataHeader.length + 2];

            System.arraycopy(metadataHeader, 0, newHeader, 0, metadataHeader.length);
            newHeader[metadataHeader.length] = Metadata.BUCKETEER_STATE;
            newHeader[metadataHeader.length + 1] = Metadata.IIIF_ACCESS_URL;
            bucketeerStateIndex = metadataHeader.length;
            accessUrlIndex = metadataHeader.length + 1;
            metadataHeader = newHeader;
            aJob.setMetadataHeader(metadataHeader);
        } else if (bucketeerStateIndex != -1 || accessUrlIndex != -1) {
            // If only one header is missing, we need to expand our headers array by one
            final String[] newHeader = new String[metadataHeader.length + 1];
            final int index = findHeader(metadataHeader, Metadata.BUCKETEER_STATE);

            System.arraycopy(metadataHeader, 0, newHeader, 0, metadataHeader.length);

            // If Bucketeer State was found, add the other one; else, add Bucketeer State
            if (index != -1) {
                newHeader[newHeader.length - 1] = Metadata.IIIF_ACCESS_URL;
                accessUrlIndex = newHeader.length - 1;
            } else {
                newHeader[newHeader.length - 1] = Metadata.BUCKETEER_STATE;
                bucketeerStateIndex = newHeader.length - 1;
            }

            metadataHeader = newHeader;
            aJob.setMetadataHeader(metadataHeader);
        }

        // Then let's loop through the metadata and add or update columns as needed
        for (int index = 0; index < metadata.size(); index++) {
            final Item item = items.get(index);

            String[] row = metadata.get(index);

            // If the number of columns has changed, increase our metadata row array size
            if (row.length != metadataHeader.length) {
                final String[] newRow = new String[(metadataHeader.length - row.length) + row.length];

                System.arraycopy(row, 0, newRow, 0, row.length);
                row = newRow;
            }

            row[bucketeerStateIndex] = item.getWorkflowState().toString();
            row[accessUrlIndex] = item.getAccessURL();
            metadata.set(index, row);
        }

        return aJob;
    }

    private int findHeader(final String[] aHeadersArray, final String aHeader) {
        Objects.requireNonNull(aHeadersArray);
        Objects.requireNonNull(aHeader);

        for (int index = 0; index < aHeadersArray.length; index++) {
            if (aHeader.equals(aHeadersArray[index])) {
                return index;
            }
        }

        return -1;
    }

    private String writeToCsv(final Job aJob) throws IOException {
        final String[] metadataHeader = aJob.getMetadataHeader();
        final List<String[]> metadata = aJob.getMetadata();
        final StringWriter stringWriter = new StringWriter();
        final CSVWriter csvWriter = new CSVWriter(stringWriter);

        // Let's be explicit and put all values in quotes
        csvWriter.writeNext(metadataHeader, true);
        csvWriter.writeAll(metadata, true);
        csvWriter.close();

        return stringWriter.toString();
    }
}
