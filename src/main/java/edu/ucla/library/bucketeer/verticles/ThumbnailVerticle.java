
package edu.ucla.library.bucketeer.verticles;

import java.util.Iterator;
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
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import uk.co.lucasweb.aws.v4.signer.credentials.AwsCredentials;

public class ThumbnailVerticle extends AbstractBucketeerVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(ThumbnailVerticle.class, Constants.MESSAGES);

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
            final ObjectMapper mapper = new XmlMapper();

            try {
                while (iterator.hasNext()) {
                    invalidation.addPath(StringUtils.format(INVALIDATION_TEMPLATE, iterator.next(), tnSize));
                }

                System.out.println(mapper.writeValueAsString(invalidation));

                // final HttpClient client = getVertx().createHttpClient();
                // final RequestOptions request = new RequestOptions().setURI(url);

                message.reply(Op.SUCCESS);
            } catch (final JsonProcessingException details) {
                message.reply(Op.FAILURE);
            }

            // try {
            // authenticate(client.get(request, response -> {
            // final String statusMessage = response.statusMessage();
            // final int statusCode = response.statusCode();
            //
            // if (statusCode == 200) {
            //
            // } else {
            //
            // }
            // }), cdnDistId, invalidationBatch).end();
            //
            // message.reply(Op.SUCCESS);
            // } catch (final JsonProcessingException details) {
            // LOGGER.error(details, details.getMessage());
            // message.reply(Op.FAILURE);
            // }
        });
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }

    protected HttpClientRequest authenticate(final HttpClientRequest aRequest, final String aDistributionId,
            final InvalidationBatch aInvalidationBatch) throws JsonProcessingException {
        final AwsCredentials credentials = new AwsCredentials(myAccessKey, mySecretKey);
        final AwsV4Signature signature = new AwsV4Signature(credentials, aDistributionId, aInvalidationBatch);

        return aRequest;
    }

}
