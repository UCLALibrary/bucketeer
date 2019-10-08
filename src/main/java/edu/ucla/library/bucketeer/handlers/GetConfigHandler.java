
package edu.ucla.library.bucketeer.handlers;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.bucketeer.Config;
import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.HTTP;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * Gets a list of in-progress jobs.
 */
public class GetConfigHandler extends AbstractBucketeerHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetConfigHandler.class, Constants.MESSAGES);

    private final JsonObject myConfig;

    /**
     * Constructs the GetConfigHandler.
     *
     * @param aConfig
     */
    public GetConfigHandler(final JsonObject aConfig) {
        myConfig = aConfig;
    }

    @Override
    public void handle(final RoutingContext aContext) {
        final HttpServerResponse response = aContext.response();
        final JsonObject viewableConfig = new JsonObject();
        final String iiifURL = myConfig.getString(Config.IIIF_URL);
        final String fsMount = myConfig.getString(Config.FILESYSTEM_MOUNT);
        final String tnSize = myConfig.getString(Config.THUMBNAIL_SIZE);
        final String lambdaBucket = myConfig.getString(Config.LAMBDA_S3_BUCKET);
        final String s3Bucket = myConfig.getString(Config.S3_BUCKET);
        final String s3Region = myConfig.getString(Config.S3_REGION);

        if (iiifURL != null) {
            viewableConfig.put("bucketeer-iiif-url", iiifURL);
        }

        if (tnSize != null) {
            viewableConfig.put("bucketeer-tn-size", tnSize);
        }

        if (fsMount != null) {
            viewableConfig.put("bucketeer-fs-mount", fsMount);
        }

        if (s3Region != null) {
            viewableConfig.put("bucketeer-s3-region", s3Region);
        }

        if (s3Bucket != null) {
            viewableConfig.put("bucketeer-s3-bucket", s3Bucket);
        }

        if (lambdaBucket != null) {
            viewableConfig.put("lambda-s3-bucket", lambdaBucket);
        }

        response.setStatusCode(HTTP.OK);
        response.end(viewableConfig.encodePrettily());
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }

}
