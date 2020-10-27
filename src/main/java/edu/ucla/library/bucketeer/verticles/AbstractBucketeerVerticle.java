
package edu.ucla.library.bucketeer.verticles;

import info.freelibrary.util.Logger;

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
import edu.ucla.library.bucketeer.MessageCodes;
import edu.ucla.library.bucketeer.Op;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.LocalMap;

/**
 * An abstract verticle that Bucketeer verticles can extend.
 */
public abstract class AbstractBucketeerVerticle extends AbstractVerticle {

    protected Optional<FeatureFlagChecker> myFeatureChecker;

    @Override
    public void start() throws Exception {
        super.start();

        // Register our verticle name with its deployment ID.
        final LocalMap<String, String> verticleMap = vertx.sharedData().getLocalMap(Constants.VERTICLE_MAP);
        final String verticleName = getClass().getSimpleName();

        // Add a deployment ID to the verticle map
        if (verticleMap.containsKey(verticleName)) {
            verticleMap.put(verticleName, verticleMap.get(verticleName) + "|" + deploymentID());
        } else {
            verticleMap.put(verticleName, deploymentID());
        }

        // Make a feature flag checker available to verticles if they need it
        myFeatureChecker = getFeatureFlagChecker();
    }

    @Override
    public void stop(final Promise<Void> aPromise) {
        if (getLogger().isDebugEnabled()) {
            getLogger().debug(MessageCodes.BUCKETEER_028, getClass().getName(), deploymentID());
        }

        aPromise.complete();
    }

    protected MessageConsumer<JsonObject> getJsonConsumer() {
        getLogger().debug(MessageCodes.BUCKETEER_025, getClass().getName());
        return vertx.eventBus().consumer(getClass().getName());
    }

    /**
     * Sends a message to another verticle with a supplied timeout value.
     *
     * @param aJsonObject A JSON message
     * @param aVerticleName A verticle name that will respond to the message
     * @param aTimeout A timeout measured in milliseconds
     */
    protected void sendMessage(final JsonObject aJsonObject, final String aVerticleName, final long aTimeout) {
        final DeliveryOptions options = new DeliveryOptions().setSendTimeout(aTimeout);

        vertx.eventBus().request(aVerticleName, aJsonObject, options, response -> {
            if (response.failed()) {
                if (response.cause() != null) {
                    getLogger().error(response.cause(), MessageCodes.BUCKETEER_005, aVerticleName, aJsonObject);
                } else {
                    getLogger().error(MessageCodes.BUCKETEER_005, aVerticleName, aJsonObject);
                }
            } else if (response.result().body().equals(Op.RETRY)) {
                getLogger().debug(MessageCodes.BUCKETEER_048, aVerticleName);
                sendMessage(aJsonObject, aVerticleName, aTimeout);
            }
        });
    }

    /**
     * Send a message to another verticle.
     *
     * @param aJsonObject A JSON message
     * @param aVerticleName A verticle name that will respond to the message
     */
    protected void sendMessage(final JsonObject aJsonObject, final String aVerticleName) {
        sendMessage(aJsonObject, aVerticleName, DeliveryOptions.DEFAULT_TIMEOUT);
    }

    /**
     * Gets a feature flag checker.
     *
     * @return An optional feature flag checker
     */
    protected Optional<FeatureFlagChecker> getFeatureFlagChecker() {
        if (vertx.fileSystem().existsBlocking(Features.FEATURE_FLAGS_FILE)) {
            return Optional.of(ConfigFeatureFlagChecker.forConfigSupplier(
                    Suppliers.supplierAndThen(FileResourceLoaders.forFile(new File(Features.FEATURE_FLAGS_FILE)),
                            TypesafeConfigReader.FROM_STRING),
                    TypesafeConfigDecider.FEATURE_ENABLED));
        } else {
            return Optional.empty();
        }
    }

    /**
     * Gets the logger created in the subclass.
     *
     * @return A configured logger that is ready to use
     */
    protected abstract Logger getLogger();

}
