
package edu.ucla.library.bucketeer.handlers;

import java.io.File;
import java.util.Optional;

import com.nike.moirai.ConfigFeatureFlagChecker;
import com.nike.moirai.FeatureFlagChecker;
import com.nike.moirai.Suppliers;
import com.nike.moirai.resource.FileResourceLoaders;
import com.nike.moirai.typesafeconfig.TypesafeConfigDecider;
import com.nike.moirai.typesafeconfig.TypesafeConfigReader;

import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.Features;
import edu.ucla.library.bucketeer.HTTP;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * Handles requests about the status of the Bucketeer application.
 */
public class GetStatusHandler implements Handler<RoutingContext> {

    @Override
    public void handle(final RoutingContext aContext) {
        final HttpServerResponse response = aContext.response();
        final JsonObject status = new JsonObject();

        status.put(Constants.STATUS, "ok");
        status.put(Features.FEATURES, getFeaturesConfigStatus(aContext.vertx()));

        response.setStatusCode(HTTP.OK);
        response.putHeader(Constants.CONTENT_TYPE, Constants.JSON).end(status.encodePrettily());
    }

    /**
     * Gets the status of the feature flag configuration.
     *
     * @param aVertx A Vert.x instance
     * @return A JSON object representing the state of the feature flag configuration
     */
    private JsonObject getFeaturesConfigStatus(final Vertx aVertx) {
        final Optional<FeatureFlagChecker> featureFlagChecker = getFeatureFlagChecker(aVertx);
        final JsonObject features = new JsonObject();

        // Check that our feature flag checker configuration can be found
        if (featureFlagChecker.isPresent()) {
            features.put(Features.ENABLED, true);

            // If it's found, check that it's enabled
            if (featureFlagChecker.get().isFeatureEnabled(Features.LARGE_IMAGE_ROUTING)) {
                features.put(Features.LARGE_IMAGE_ROUTING, true);
            } else { // Feature isn't enabled
                features.put(Features.LARGE_IMAGE_ROUTING, false);
            }
        } else { // Feature flag isn't available
            features.put(Features.ENABLED, false);
        }

        return features;
    }

    /**
     * Gets a feature flag checker.
     *
     * @return An optional feature flag checker
     */
    private Optional<FeatureFlagChecker> getFeatureFlagChecker(final Vertx aVertx) {
        if (aVertx.fileSystem().existsBlocking(Features.FEATURE_FLAGS_FILE)) {
            return Optional.of(ConfigFeatureFlagChecker.forConfigSupplier(
                    Suppliers.supplierAndThen(FileResourceLoaders.forFile(new File(Features.FEATURE_FLAGS_FILE)),
                            TypesafeConfigReader.FROM_STRING),
                    TypesafeConfigDecider.FEATURE_ENABLED));
        } else {
            return Optional.empty();
        }
    }
}
