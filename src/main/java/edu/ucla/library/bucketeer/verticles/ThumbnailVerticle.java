
package edu.ucla.library.bucketeer.verticles;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;
import info.freelibrary.util.StringUtils;

import edu.ucla.library.bucketeer.Config;
import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.MessageCodes;
import edu.ucla.library.bucketeer.Op;
import edu.ucla.library.bucketeer.utils.AwsV4Signature;
import edu.ucla.library.bucketeer.utils.InvalidationBatch;
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

    private static final int DEFAULT_CDN_PORT = 443;

    // Slots for host, id, and thumbnail size
    private static final String INVALIDATION_TEMPLATE = "/{}/full/{}/0/default.jpg";

    private static final String DEFAULT_THUMBNAIL_SIZE = "!200,200";

    private String myAccessKey;

    private String mySecretKey;

    @SuppressWarnings("deprecation")
    @Override
    public void start() throws Exception {
        super.start();

        if (LOGGER.isDebugEnabled()) {
            final String threadName = Thread.currentThread().getName();
            final String className = S3BucketVerticle.class.getSimpleName();

            LOGGER.debug(MessageCodes.BUCKETEER_004, className, threadName);
        }

        final JsonObject config = config();
        final String tnSize = config.getString(Config.THUMBNAIL_SIZE, DEFAULT_THUMBNAIL_SIZE);
        final String host = config.getString(Config.IIIF_URL);
        final String cdnDistId = config.getString(Config.CDN_DISTRO_ID);

        Objects.requireNonNull(host);
        Objects.requireNonNull(cdnDistId);

        myAccessKey = config.getString(Config.S3_ACCESS_KEY);
        mySecretKey = config.getString(Config.S3_SECRET_KEY);

        Objects.requireNonNull(myAccessKey);
        Objects.requireNonNull(mySecretKey);

        getJsonConsumer().handler(message -> {
            final JsonObject thumbnailRequest = message.body();
            final JsonArray thumbnailIDs = thumbnailRequest.getJsonArray(Constants.IMAGE_ID_ARRAY);
            final String uuid = UUID.randomUUID().toString();
            final InvalidationBatch invalidation = new InvalidationBatch(uuid);
            final Iterator<Object> iterator = thumbnailIDs.iterator();
            final HttpClient client = getVertx().createHttpClient();
            final ObjectMapper mapper = new XmlMapper();
            final RequestOptions request = new RequestOptions();

            try {
                final URL url = new URL(StringUtils.format(INVALIDATION_URL, cdnDistId));
                final String data;

                request.setHost(url.getHost());
                request.setURI(url.getPath());
                request.setPort(DEFAULT_CDN_PORT);
                request.setSsl(true);

                while (iterator.hasNext()) {
                    invalidation.addPath(StringUtils.format(INVALIDATION_TEMPLATE, iterator.next(), tnSize));
                }

                data = mapper.writeValueAsString(invalidation);

                System.out.println(data);
                System.out.println(request.toJson().encodePrettily());

                authenticate(client.post(request, response -> {
                    final String statusMessage = response.statusMessage();
                    final int statusCode = response.statusCode();

                    if (statusCode == 201) {
                        message.reply(Op.SUCCESS);
                    } else {
                        response.bodyHandler(body -> {
                            LOGGER.error(MessageCodes.BUCKETEER_102, statusCode, statusMessage, body.toString());
                        });

                        message.reply(Op.FAILURE);
                    }
                }), cdnDistId, data).exceptionHandler(details -> {
                    LOGGER.error(details, details.getMessage());
                    message.reply(Op.FAILURE);
                }).end(mapper.writeValueAsString(invalidation)); // mapper.writeValueAsString(invalidation)
            } catch (final JsonProcessingException | MalformedURLException details) {
                LOGGER.error(details, details.getMessage());
                message.reply(Op.FAILURE);
            }
        });
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }

    protected HttpClientRequest authenticate(final HttpClientRequest aRequest, final String aDistributionId,
            final String aInvalidation) throws JsonProcessingException {
        final AwsCredentials credentials = new AwsCredentials(myAccessKey, mySecretKey);
        final AwsV4Signature signature = new AwsV4Signature(credentials, aDistributionId, aInvalidation);
        final Map<String, String> headers = signature.getHeaders();
        final Iterator<String> iterator = headers.keySet().iterator();

        System.out.println(signature);
        aRequest.putHeader("Authorization", signature.toString());

        while (iterator.hasNext()) {
            final String headerKey = iterator.next();

            aRequest.putHeader(headerKey, headers.get(headerKey));
        }

        return aRequest;
    }

}
