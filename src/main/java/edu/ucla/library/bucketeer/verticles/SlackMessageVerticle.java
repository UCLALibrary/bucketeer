
package edu.ucla.library.bucketeer.verticles;

import static edu.ucla.library.bucketeer.Constants.MESSAGES;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.seratch.jslack.Slack;
import com.github.seratch.jslack.api.methods.SlackApiException;
import com.github.seratch.jslack.api.methods.response.chat.ChatPostMessageResponse;
import com.github.seratch.jslack.api.methods.response.files.FilesUploadResponse;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.bucketeer.Config;
import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.MessageCodes;
import edu.ucla.library.bucketeer.Metadata;
import edu.ucla.library.bucketeer.Op;
import edu.ucla.library.bucketeer.io.BeanToCsvWriter;
import edu.ucla.library.bucketeer.utils.AnnotationMappingStrategy;
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
                    final TypeReference<List<Metadata>> listTypeRef = new TypeReference<List<Metadata>>() {};
                    final String jsonString = json.getJsonArray(Constants.BATCH_METADATA).toString();
                    final List<Metadata> metadataList = new ObjectMapper().readValue(jsonString, listTypeRef);
                    final AnnotationMappingStrategy<Metadata> map = new AnnotationMappingStrategy<>(Metadata.class);
                    final String[] cols = new String[] { Metadata.Columns.ITEM_ARK, Metadata.Columns.PARENT_ARK };
                    final BeanToCsvWriter<Metadata> writer = new BeanToCsvWriter<>(map);
                    final StringWriter stringWriter = new StringWriter();
                    final byte[] bytes;

                    map.setHeader(Metadata.Columns.toArray());
                    writer.urlDecodeColumns(cols);
                    writer.write(metadataList, stringWriter);
                    bytes = stringWriter.getBuffer().toString().getBytes(StandardCharsets.UTF_8);

                    final List<String> channels = Arrays.asList(new String[] { slackChannelID });
                    final FilesUploadResponse response = slack.methods(botToken).filesUpload(post -> post.channels(
                            channels).fileData(bytes).filename(jobName + ".csv").filetype("csv").initialComment(
                                    slackMessageText).title(jobName));
                    // The above 'filetype' comes from --> https://api.slack.com/types/file#file_types

                    if (response.isOk()) {
                        LOGGER.debug(MessageCodes.BUCKETEER_087, slackChannelID, response.toString());
                        message.reply(Op.SUCCESS);
                    } else {
                        LOGGER.error(MessageCodes.BUCKETEER_090, response.getError());
                        message.reply(Op.FAILURE);
                    }
                } catch (IOException | SlackApiException | CsvDataTypeMismatchException |
                        CsvRequiredFieldEmptyException details) {
                    LOGGER.error(MessageCodes.BUCKETEER_089, details.getMessage());
                    message.reply(Op.FAILURE);
                }
            } else {
                try {
                    final ChatPostMessageResponse response = slack.methods(botToken).chatPostMessage(post -> post
                            .channel(slackChannelID).text(slackMessageText));

                    if (response.isOk()) {
                        LOGGER.debug(MessageCodes.BUCKETEER_087, slackChannelID, response.toString());
                        message.reply(Op.SUCCESS);
                    } else {
                        LOGGER.error(MessageCodes.BUCKETEER_090, response.getError());
                        message.reply(Op.FAILURE);
                    }
                } catch (IOException | SlackApiException details) {
                    LOGGER.error(MessageCodes.BUCKETEER_089, details.getMessage());
                    message.reply(Op.FAILURE);
                }
            }

        });
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }

}
