
package edu.ucla.library.bucketeer.verticles;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import info.freelibrary.util.I18nRuntimeException;
import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;
import info.freelibrary.util.StringUtils;

import edu.ucla.library.bucketeer.Config;
import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.HTTP;
import edu.ucla.library.bucketeer.MessageCodes;
import edu.ucla.library.bucketeer.Op;
import edu.ucla.library.bucketeer.utils.AwsV4Signature;
import edu.ucla.library.bucketeer.utils.InvalidationBatch;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import uk.co.lucasweb.aws.v4.signer.credentials.AwsCredentials;

public class ThumbnailVerticle extends AbstractBucketeerVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(ThumbnailVerticle.class, Constants.MESSAGES);

    private static final String INVALIDATION_URL =
            "https://cloudfront.amazonaws.com/2019-03-26/distribution/{}/invalidation";

    private static final int DEFAULT_SECURE_PORT = 443;

    private static final int DEFAULT_INSECURE_PORT = 80;

    // Slots for host, id, and thumbnail size
    private static final String INVALIDATION_TEMPLATE = "/{}/full/{}/0/default.jpg";

    private static final String DEFAULT_THUMBNAIL_SIZE = "!200,200";

    private static final String EMPTY = "";

    private static final String SLASH = "/";

    private static final String HTTPS_PROTOCOL = "https";

    private String myAccessKey;

    private String mySecretKey;

    @Override
    public void start() throws Exception {
        super.start();

        if (LOGGER.isDebugEnabled()) {
            final String threadName = Thread.currentThread().getName();
            final String className = S3BucketVerticle.class.getSimpleName();

            LOGGER.debug(MessageCodes.BUCKETEER_004, className, threadName);
        }

        // Get our configuration values
        final JsonObject config = config();

        myAccessKey = config.getString(Config.S3_ACCESS_KEY);
        mySecretKey = config.getString(Config.S3_SECRET_KEY);

        // We must have AWS credentials
        Objects.requireNonNull(myAccessKey);
        Objects.requireNonNull(mySecretKey);

        getJsonConsumer().handler(message -> {
            @SuppressWarnings("rawtypes")
            final List<Future> futures = new ArrayList<>();
            final HttpClient client = getVertx().createHttpClient();
            final Future<String> future = Future.future();
            final InvalidationBatch invalidation = new InvalidationBatch(UUID.randomUUID().toString());
            final String tnSize = config.getString(Config.THUMBNAIL_SIZE, DEFAULT_THUMBNAIL_SIZE);
            final JsonArray thumbnailIDs = message.body().getJsonArray(Constants.IMAGE_ID_ARRAY);
            final String iiifURL = config.getString(Config.IIIF_URL);

            for (int index = 0; index < thumbnailIDs.size(); index++) {
                final String id = thumbnailIDs.getString(index);
                final String iiifPath = StringUtils.format(INVALIDATION_TEMPLATE, id, tnSize);

                futures.add(generateThumbnail(client, iiifURL, iiifPath, Future.future()));
            }

            // We require at least one
            CompositeFuture.all(futures).setHandler(handler -> {
                if (handler.succeeded()) {
                    final CompositeFuture compositeFuture = handler.result();
                    final int size = compositeFuture.size();

                    // Get the images that were cached and need to be invalidated
                    for (int index = 0; index < size; index++) {
                        final String iiifPath = compositeFuture.resultAt(index);

                        // Not all successfully completed jobs need cache invalidation
                        if (iiifPath != null) {
                            LOGGER.debug(MessageCodes.BUCKETEER_107, iiifPath);
                            invalidation.addPath(iiifPath);
                        }
                    }

                    invalidateCache(client, message, config, future, invalidation);
                } else {
                    future.fail(handler.cause());
                    message.reply(Op.FAILURE);
                }
            });

        });
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }

    @SuppressWarnings("deprecation")
    private Future<String> generateThumbnail(final HttpClient aClient, final String aURL, final String aIiifPath,
            final Future<String> aFuture) {
        final RequestOptions request = new RequestOptions();
        final URL url;

        try {
            // Construct a URL that can be used to cache the thumbnail image
            if (aURL.endsWith(SLASH) && aIiifPath.startsWith(SLASH)) {
                url = new URL(aURL + aIiifPath.substring(1));
            } else if (!aURL.endsWith(SLASH) && !aIiifPath.startsWith(SLASH)) {
                url = new URL(aURL + SLASH + aIiifPath);
            } else {
                url = new URL(aURL + aIiifPath);
            }
        } catch (final MalformedURLException details) {
            throw new I18nRuntimeException(Constants.MESSAGES, MessageCodes.BUCKETEER_106, aURL, aIiifPath);
        }

        // Set the request options for the caching request
        request.setHost(url.getHost()).setURI(url.getPath());

        // If we're sending to a HTTPS server, note that in our request options
        if (url.getProtocol().equals(HTTPS_PROTOCOL)) {
            request.setPort(DEFAULT_SECURE_PORT).setSsl(true);
        } else {
            request.setPort(DEFAULT_INSECURE_PORT);
        }

        aClient.get(request, response -> {
            final int statusCode = response.statusCode();
            final String statusMessage = response.statusMessage();

            if (statusCode == HTTP.OK) {
                final String cacheHeader = StringUtils.trimTo(response.getHeader("x-cache"), EMPTY);

                if (cacheHeader.startsWith("Miss")) {
                    aFuture.complete();
                } else if (cacheHeader.startsWith("Hit")) {
                    aFuture.complete(aIiifPath);
                } else {
                    final String failureDetails = EMPTY.equals(cacheHeader) ? "(empty)" : cacheHeader;
                    final String failureMessage = LOGGER.getMessage(MessageCodes.BUCKETEER_105, failureDetails);

                    LOGGER.warn(failureMessage);
                    aFuture.fail(failureMessage);
                }
            } else {
                aFuture.fail(LOGGER.getMessage(MessageCodes.BUCKETEER_022, statusCode, statusMessage));
            }
        }).exceptionHandler(details -> {
            LOGGER.error(details, details.getMessage());
            aFuture.fail(details);
        }).end();

        return aFuture;
    }

    @SuppressWarnings("deprecation")
    private void invalidateCache(final HttpClient aClient, final Message<JsonObject> aMessage,
            final JsonObject aConfig, final Future<String> aFuture, final InvalidationBatch aInvalidation) {
        final String cdnDistId = aConfig.getString(Config.CDN_DISTRO_ID);
        final String host = aConfig.getString(Config.IIIF_URL);
        final RequestOptions request = new RequestOptions();

        // We must have a host and CloudFront distribution ID
        Objects.requireNonNull(host);
        Objects.requireNonNull(cdnDistId);

        try {
            final String invalidation = new XmlMapper().writeValueAsString(aInvalidation);
            final URL url = new URL(StringUtils.format(INVALIDATION_URL, cdnDistId));

            LOGGER.debug(MessageCodes.BUCKETEER_103, invalidation);

            request.setHost(url.getHost());
            request.setURI(url.getPath());

            if (url.getProtocol().equals(HTTPS_PROTOCOL)) {
                request.setPort(DEFAULT_SECURE_PORT);
                request.setSsl(true);
            } else {
                request.setPort(DEFAULT_INSECURE_PORT);
            }

            // Send the invalidation request to CloudFront, signing the request with a valid signature
            authenticate(aClient.post(request, response -> {
                final String statusMessage = response.statusMessage();
                final int statusCode = response.statusCode();

                // Check to see if our batch job has been created in the CloudFront jobs queue
                if (statusCode == HTTP.CREATED) {
                    response.bodyHandler(body -> {
                        LOGGER.info(MessageCodes.BUCKETEER_104, body.toString());
                    });

                    aMessage.reply(Op.SUCCESS);
                    aFuture.complete();
                } else {
                    response.bodyHandler(body -> {
                        LOGGER.error(MessageCodes.BUCKETEER_102, statusCode, statusMessage, body.toString());
                    });

                    aMessage.reply(Op.FAILURE);
                    aFuture.fail(LOGGER.getMessage(MessageCodes.BUCKETEER_022, statusCode, statusMessage));
                }
            }), cdnDistId, invalidation).exceptionHandler(details -> {
                // Catch any errors from our HTTP client
                LOGGER.error(details, details.getMessage());
                aMessage.reply(Op.FAILURE);
                aFuture.fail(details);
            }).end(invalidation);
        } catch (final JsonProcessingException | MalformedURLException details) {
            // Catch any errors from preparing our HTTP request
            LOGGER.error(details, details.getMessage());
            aMessage.reply(Op.FAILURE);
            aFuture.fail(details);
        }
    }

    /**
     * Authenticate the CloudFront request with a valid AWS v4 signature.
     *
     * @param aRequest An HTTP request
     * @param aDistributionId A CloudFront distribution ID
     * @param aInvalidation An invalidation batch job
     * @return The authenticated HTTP request
     * @throws JsonProcessingException If there is trouble preparing the signature
     */
    private HttpClientRequest authenticate(final HttpClientRequest aRequest, final String aDistributionId,
            final String aInvalidation) throws JsonProcessingException {
        final AwsCredentials credentials = new AwsCredentials(myAccessKey, mySecretKey);
        final AwsV4Signature signature = new AwsV4Signature(credentials, aDistributionId, aInvalidation);
        final Map<String, String> headers = signature.getHeaders();
        final Iterator<String> iterator = headers.keySet().iterator();

        aRequest.putHeader("Authorization", signature.toString());

        while (iterator.hasNext()) {
            final String headerKey = iterator.next();

            aRequest.putHeader(headerKey, headers.get(headerKey));
        }

        return aRequest;
    }

}
