
package edu.ucla.library.bucketeer.verticles;

import static edu.ucla.library.bucketeer.Constants.MESSAGES;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import com.github.seratch.jslack.Slack;
import com.github.seratch.jslack.api.methods.MethodsClient;
import com.github.seratch.jslack.api.methods.SlackApiException;
import com.github.seratch.jslack.api.methods.SlackApiResponse;
import com.github.seratch.jslack.api.methods.request.chat.ChatPostMessageRequest;
import com.github.seratch.jslack.api.methods.request.files.FilesUploadRequest;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.bucketeer.Config;
import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.MessageCodes;
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
            final MethodsClient client = slack.methods(botToken);
            final SlackApiResponse response;

            if (json.containsKey(Constants.CSV_DATA)) {
                final String jobName = json.getString(Constants.JOB_NAME);

                LOGGER.debug(MessageCodes.BUCKETEER_091, slackMessageText, slackChannelID);

                try {
                    final byte[] bytes = json.getString(Constants.CSV_DATA).getBytes(StandardCharsets.UTF_8);
                    final List<String> channels = Arrays.asList(new String[] { slackChannelID });

                    response = client.filesUpload(post -> post.channels(channels).fileData(bytes).filename(jobName + ".csv").filetype("csv").initialComment(slackMessageText).title(jobName));
                    // The above 'filetype' comes from --> https://api.slack.com/types/file#file_types
                } catch (IOException | SlackApiException details) {
                    message.fail(CodeUtils.getInt(MessageCodes.BUCKETEER_089), details.getMessage());
                    return;
                }
            } else {
                try {
                    response = client.chatPostMessage(post -> post.channel(slackChannelID).text(slackMessageText));
                } catch (IOException | SlackApiException details) {
                    message.fail(CodeUtils.getInt(MessageCodes.BUCKETEER_089), details.getMessage());
                    return;
                }
            }

            if (response.isOk()) {
                LOGGER.debug(MessageCodes.BUCKETEER_087, slackChannelID, response.toString());
                message.reply(Op.SUCCESS);
            } else {
                message.fail(CodeUtils.getInt(MessageCodes.BUCKETEER_090), response.getError());
            }
        });
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }

}
