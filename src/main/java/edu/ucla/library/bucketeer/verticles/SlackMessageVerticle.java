
package edu.ucla.library.bucketeer.verticles;

import static edu.ucla.library.bucketeer.Constants.MESSAGES;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import com.github.seratch.jslack.Slack;
import com.github.seratch.jslack.api.methods.SlackApiException;
import com.github.seratch.jslack.api.methods.response.chat.ChatPostMessageResponse;
import com.github.seratch.jslack.api.methods.response.files.FilesUploadResponse;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.bucketeer.Config;
import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.MessageCodes;
import edu.ucla.library.bucketeer.Op;
import io.vertx.core.json.JsonArray;
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
                final JsonArray metadataArray = json.getJsonArray(Constants.BATCH_METADATA);
                final byte[] bytes = metadataArray.toString().getBytes(StandardCharsets.UTF_8);

                LOGGER.debug(MessageCodes.BUCKETEER_091, slackMessageText, slackChannelID);

                try {
                    final List<String> channels = Arrays.asList(new String[] { slackChannelID });
                    final FilesUploadResponse response = slack.methods(botToken).filesUpload(post -> post.channels(
                            channels).fileData(bytes).filename("placeholder.json").initialComment(slackMessageText)
                            .title("Placeholder"));

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
