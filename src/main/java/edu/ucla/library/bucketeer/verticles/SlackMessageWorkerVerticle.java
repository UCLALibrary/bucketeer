
package edu.ucla.library.bucketeer.verticles;

import static edu.ucla.library.bucketeer.Constants.MESSAGES;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;

import com.github.seratch.jslack.Slack;
import com.github.seratch.jslack.api.methods.SlackApiException;
import com.github.seratch.jslack.api.methods.response.files.FilesUploadResponse;
import com.github.seratch.jslack.api.webhook.Payload;
import com.github.seratch.jslack.api.webhook.WebhookResponse;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.HTTP;
import edu.ucla.library.bucketeer.MessageCodes;
import edu.ucla.library.bucketeer.Op;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Accepts and forwards messages to a configured Slack channel.
 */
public class SlackMessageWorkerVerticle extends AbstractBucketeerVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(SlackMessageWorkerVerticle.class, MESSAGES);

    @Override
    public void start() {
        final JsonObject config = config();

        if (LOGGER.isDebugEnabled()) {
            final String threadName = Thread.currentThread().getName();
            final String className = SlackMessageWorkerVerticle.class.getSimpleName();

            LOGGER.debug(MessageCodes.BUCKETEER_004, className, threadName);
        }

        // set up the Slack client
        LOGGER.debug(MessageCodes.BUCKETEER_085);

        final Slack slack = Slack.getInstance();

        // get our message from the event bus, and send it off to Slack
        // messages should contain these 2 vars: slackMessageText and slackChannelId,
        // we deduce which slackWebhookURL to use based on slackChannelId

        getJsonConsumer().handler(message -> {
            final JsonObject json = message.body();
            final String slackMessageText = json.getString(Constants.SLACK_MESSAGE_TEXT);
            final String slackChannelId = json.getString(Constants.SLACK_CHANNEL_ID);

            // figure out which webhook to use based on the slackChannelId we have...
            // ... start with a default webhook, which is for the bucketeer-jobs channel
            String slackWebhookURL = config.getString(Constants.SLACK_WEBHOOK_URL);
            // ... if we want to send a message to the dev-null channel, use the webhook for
            // that

            final String botToken = config.getString(Constants.SLACK_OAUTH_TOKEN);
            final List<String> channels = new ArrayList<>();
            channels.add(slackChannelId);

            // If slackChannelId is "dev-null" use the test webhook URL, otherwise
            // use the error webhook URL
            // TODO: refactor this to use a lookup of some sort, because this is a bit sloppy
            if ("dev-null".contentEquals(slackChannelId)) {
                slackWebhookURL = config.getString(Constants.SLACK_TEST_WEBHOOK_URL);
            } else {
                slackWebhookURL = config.getString(Constants.SLACK_ERROR_WEBHOOK_URL);
            }

            // optionally handle metadata if it's included in our message
            // NOTE: in order to send a file, we need to use the files.upload API, not a webhook

            if (json.containsKey(Constants.BATCH_METADATA)) {
                final JsonArray metadataArray = json.getJsonArray(Constants.BATCH_METADATA);
                // we have a metadataArray, let's use the files.upload API to send our message

                final File file = new File("/tmp/placeholder.json");

                // create our JSON file for uploading (at /tmp/placeholder.json)
                try {
                    FileUtils.write(file, metadataArray.toString());
                } catch (final IOException e1) {
                    // TODO Auto-generated catch block
                    LOGGER.error(MessageCodes.BUCKETEER_092, e1.getMessage());
                }

                // send the message, if there is an io error, log the error
                LOGGER.debug(MessageCodes.BUCKETEER_091, slackMessageText, slackChannelId); // preparing to send
                                                                                            // message

                try {
                    final FilesUploadResponse response = slack.methods(botToken).filesUpload(r -> r.channels(channels)
                            .file(file).filename("placeholder.json").initialComment(slackMessageText).title(
                                    "Placeholder JSON"));
                    // If we get a successful upload response code, reply with SUCCESS...
                    if (response.isOk()) {
                        LOGGER.debug(MessageCodes.BUCKETEER_087, response.toString(), slackChannelId);
                        message.reply(Op.SUCCESS);
                        // ...otherwise, reply with FAILURE
                    } else {
                        LOGGER.error(MessageCodes.BUCKETEER_090, response.getError());
                        message.reply(Op.FAILURE);
                    }
                } catch (IOException | SlackApiException e) {
                    LOGGER.error(MessageCodes.BUCKETEER_089, e.getMessage());
                }

            } else {

                // build our payload to send to Slack
                final Payload payload = Payload.builder().text(slackMessageText).attachments(new ArrayList<>())
                        .build();

                // send the message, if there is an io error, log the error
                LOGGER.debug(MessageCodes.BUCKETEER_086, slackMessageText, slackChannelId); // preparing to send
                                                                                            // message
                try {
                    final WebhookResponse response = slack.send(slackWebhookURL, payload);
                    // If we get a successful upload response code, reply with SUCCESS...
                    final int statusCode = response.getCode();
                    if (statusCode == HTTP.OK) {
                        LOGGER.debug(MessageCodes.BUCKETEER_087, response.toString(), slackChannelId);
                        message.reply(Op.SUCCESS);
                        // ...otherwise, reply with FAILURE
                    } else {
                        LOGGER.error(MessageCodes.BUCKETEER_088, statusCode, response.getBody());
                        message.reply(Op.FAILURE);
                    }
                    // ... and log any IO exceptions
                } catch (final IOException e) {
                    message.reply(Op.FAILURE);
                    LOGGER.error(MessageCodes.BUCKETEER_089, e.getMessage());
                }
            }

        });
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }

}
