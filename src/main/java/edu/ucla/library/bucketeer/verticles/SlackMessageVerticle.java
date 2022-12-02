
package edu.ucla.library.bucketeer.verticles;

import static edu.ucla.library.bucketeer.Constants.MESSAGES;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.github.seratch.jslack.Slack;
import com.github.seratch.jslack.api.methods.MethodsClient;
import com.github.seratch.jslack.api.methods.SlackApiException;
import com.github.seratch.jslack.api.methods.SlackApiRequest;
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

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;

/**
 * Accepts and forwards messages to a configured Slack channel.
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals") // For other SuppressWarnings' text
public class SlackMessageVerticle extends AbstractBucketeerVerticle {

    /** The SlackMessageVerticle's logger. */
    private static final Logger LOGGER = LoggerFactory.getLogger(SlackMessageVerticle.class, MESSAGES);

    @Override
    public void start() {
        // Set up the Slack client
        final String botToken = config().getString(Config.SLACK_OAUTH_TOKEN);
        final MethodsClient slackClient = Slack.getInstance().methods(botToken);
        final int maxRetries = config().getInteger(Config.SLACK_MAX_RETRIES, 0);
        final int retryDelay = config().getInteger(Config.SLACK_RETRY_DELAY, 1);

        if (LOGGER.isDebugEnabled()) {
            final String threadName = Thread.currentThread().getName();
            final String className = SlackMessageVerticle.class.getSimpleName();

            LOGGER.debug(MessageCodes.BUCKETEER_004, className, threadName);
        }

        LOGGER.debug(MessageCodes.BUCKETEER_085);

        // Create a handler to listen for messages that should be forwarded on to a Slack channel
        getJsonConsumer().handler(message -> {
            final String slackChannelID = message.body().getString(Config.SLACK_CHANNEL_ID);

            sendSlackApiRequestWithRetry(slackClient, message.body(), maxRetries, retryDelay, 0).onSuccess(response -> {
                LOGGER.debug(MessageCodes.BUCKETEER_087, slackChannelID, response.toString());
                message.reply(Op.SUCCESS);
            }).onFailure(failure -> {
                message.fail(CodeUtils.getInt(MessageCodes.BUCKETEER_090), failure.getMessage());
            });
        });
    }

    /**
     * Sends a Slack API request, retrying on failure.
     *
     * @param aSlackClient A Slack client
     * @param aMessageData An object containing the Slack message details (whether or not a CSV is attached, etc.)
     * @param aMaxRetries The maximum number of retries before we give up
     * @param aRetryDelay The number of seconds between retry attempts
     * @param aRetryCount The number of retries used so far
     * @return A Future that completes if the Slack API request was successful
     */
    private Future<SlackApiResponse> sendSlackApiRequestWithRetry(final MethodsClient aSlackClient,
            final JsonObject aMessageData, final int aMaxRetries, final int aRetryDelay, final int aRetryCount) {
        final Promise<SlackApiResponse> promise = Promise.promise();

        if (aRetryCount > aMaxRetries) {
            promise.fail(LOGGER.getMessage(MessageCodes.BUCKETEER_523, aMaxRetries));
        } else {
            sendSlackApiRequest(aSlackClient, aMessageData).onSuccess(promise::complete).onFailure(failure -> {
                if (aRetryCount <= aMaxRetries) {
                    vertx.setTimer(1000 * aRetryDelay, timerId -> {
                        final Future<SlackApiResponse> retryAttempt = sendSlackApiRequestWithRetry(
                                aSlackClient, aMessageData, aMaxRetries, aRetryDelay, aRetryCount + 1);

                        retryAttempt.onSuccess(promise::complete).onFailure(promise::fail);
                    });
                } else {
                    promise.fail(LOGGER.getMessage(MessageCodes.BUCKETEER_523, aMaxRetries));
                }
            });
        }

        return promise.future();
    }

    /**
     * Sends a Slack API request.
     *
     * @param aSlackClient A Slack client
     * @param aMessageData An object containing the Slack message details (whether or not a CSV is attached, etc.)
     * @return A Future that completes if the Slack API request was successful
     */
    @SuppressWarnings("PMD.CognitiveComplexity")
    private Future<SlackApiResponse> sendSlackApiRequest(final MethodsClient aSlackClient,
            final JsonObject aMessageData) {
        final Promise<SlackApiResponse> promise = Promise.promise();
        final String slackMessageText = aMessageData.getString(Constants.SLACK_MESSAGE_TEXT);
        final String slackChannelID = aMessageData.getString(Config.SLACK_CHANNEL_ID);
        final SlackApiRequest request;

        if (aMessageData.containsKey(Constants.CSV_DATA)) {
            final String jobName = aMessageData.getString(Constants.JOB_NAME);
            final byte[] bytes = aMessageData.getString(Constants.CSV_DATA).getBytes(StandardCharsets.UTF_8);
            final List<String> channels = List.of(slackChannelID);

            LOGGER.debug(MessageCodes.BUCKETEER_091, slackMessageText, slackChannelID);

            // The 'filetype' comes from --> https://api.slack.com/types/file#file_types
            request = FilesUploadRequest.builder().channels(channels).fileData(bytes).filename(jobName + ".csv")
                    .filetype("csv").initialComment(slackMessageText).title(jobName).build();

            // The request methods are blocking, so we have to execute them on a worker thread
            vertx.<SlackApiResponse>executeBlocking(asyncResult -> {
                try {
                    asyncResult.complete(aSlackClient.filesUpload((FilesUploadRequest) request));
                } catch (IOException | SlackApiException details) {
                    asyncResult.fail(details);
                }
            }, asyncResult -> {
                if (asyncResult.succeeded()) {
                    final SlackApiResponse response = asyncResult.result();

                    if (response.isOk()) {
                        promise.complete(response);
                    } else {
                        promise.fail(response.getError());
                    }
                } else {
                    promise.fail(asyncResult.cause());
                }
            });
        } else {
            request = ChatPostMessageRequest.builder().channel(slackChannelID).text(slackMessageText).build();

            // The request methods are blocking, so we have to execute them on a worker thread
            vertx.<SlackApiResponse>executeBlocking(asyncResult -> {
                try {
                    asyncResult.complete(aSlackClient.chatPostMessage((ChatPostMessageRequest) request));
                } catch (IOException | SlackApiException details) {
                    asyncResult.fail(details);
                }
            }, asyncResult -> {
                if (asyncResult.succeeded()) {
                    final SlackApiResponse response = asyncResult.result();

                    if (response.isOk()) {
                        promise.complete(response);
                    } else {
                        promise.fail(response.getError());
                    }
                } else {
                    promise.fail(asyncResult.cause());
                }
            });
        }

        return promise.future();
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }

}
