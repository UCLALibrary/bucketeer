
package edu.ucla.library.bucketeer.verticles;

import static edu.ucla.library.bucketeer.Constants.MESSAGES;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.slack.api.Slack;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.methods.response.files.FilesUploadV2Response;

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
@SuppressWarnings({ "PMD.AvoidDuplicateLiterals" }) // For other SuppressWarnings' text
public class SlackMessageVerticle extends AbstractBucketeerVerticle {

    /** The SlackMessageVerticle's logger. */
    private static final Logger LOGGER = LoggerFactory.getLogger(SlackMessageVerticle.class, MESSAGES);

    @Override
    public void start() {
        // Set up the Slack client
        final String token = config().getString(Config.SLACK_OAUTH_TOKEN);
        final Slack slack = Slack.getInstance();

        if (LOGGER.isDebugEnabled()) {
            final String threadName = Thread.currentThread().getName();
            final String className = SlackMessageVerticle.class.getSimpleName();

            LOGGER.debug(MessageCodes.BUCKETEER_004, className, threadName);
        }

        LOGGER.debug(MessageCodes.BUCKETEER_085);

        // Create a handler to listen for messages that should be forwarded on to a Slack channel
        getJsonConsumer().handler(message -> {
            final JsonObject json = message.body();
            final String slackChannelID = json.getString(Config.SLACK_CHANNEL_ID);

            vertx.<String>executeBlocking(promise -> {
                final String slackMessageText = json.getString(Constants.SLACK_MESSAGE_TEXT);

                try {
                    if (json.containsKey(Constants.CSV_DATA)) {
                        final String jobName = json.getString(Constants.JOB_NAME);
                        final byte[] bytes = json.getString(Constants.CSV_DATA).getBytes(StandardCharsets.UTF_8);

                        final FilesUploadV2Response filePostResponse = slack.methods(token)
                                .filesUploadV2(request -> request.fileData(bytes).filename(jobName + ".csv")
                                        .channels(List.of(slackChannelID)).initialComment(slackMessageText)
                                        .title(jobName));

                        if (filePostResponse.isOk()) {
                            promise.complete(filePostResponse.toString()); // filePostResponse.getFile().getId()
                        } else {
                            promise.fail(filePostResponse.getError());
                        }
                    } else {
                        final ChatPostMessageResponse textPostResponse = slack.methods(token)
                                .chatPostMessage(response -> response.channel(slackChannelID).text(slackMessageText));

                        if (textPostResponse.isOk()) {
                            promise.complete(textPostResponse.toString());
                        } else {
                            promise.fail(textPostResponse.getError());
                        }
                    }
                } catch (SlackApiException | IOException details) {
                    promise.fail(details.getMessage());
                }
            }, response -> {
                if (response.succeeded()) {
                    LOGGER.debug(MessageCodes.BUCKETEER_087, slackChannelID, response.result());
                    message.reply(Op.SUCCESS);
                } else {
                    LOGGER.getMessage(MessageCodes.BUCKETEER_523, 1);
                    message.fail(CodeUtils.getInt(MessageCodes.BUCKETEER_090), response.cause().getMessage());
                }
            });
        });
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }

}
