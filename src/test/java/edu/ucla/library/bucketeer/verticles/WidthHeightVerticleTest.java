
package edu.ucla.library.bucketeer.verticles;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.UUID;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import edu.ucla.library.bucketeer.Constants;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

/**
 * A test of the {@code WidthHeightVerticleTest}.
 */
@RunWith(VertxUnitRunner.class)
public class WidthHeightVerticleTest {

    /**
     * The context on which the test runs
     */
    @Rule
    public RunTestOnContext rule = new RunTestOnContext();

    /**
     * Tests the {@code WidthHeightVerticle} response.
     *
     * @param aContext A test environment
     */
    @Test
    public void testRepliesWithWidthAndHeight(TestContext aContext) {
        final Vertx vertx = rule.vertx();
        final Async async = aContext.async();

        vertx.deployVerticle(new WidthHeightVerticle(), aContext.asyncAssertSuccess(id -> {
            final JsonObject request = new JsonObject().put(Constants.IMAGE_ID, UUID.randomUUID().toString())
                    .put(Constants.FILE_PATH, "src/test/resources/images/test.tif");

            vertx.eventBus().<JsonObject>send(WidthHeightVerticle.class.getName(), request, reply -> {
                if (reply.failed()) {
                    aContext.fail(reply.cause());
                } else {
                    final JsonObject body = reply.result().body();

                    // Check what the WidthHeightVerticle has returned to us
                    aContext.assertEquals("2000", body.getString(Constants.WIDTH));
                    aContext.assertEquals("2000", body.getString(Constants.HEIGHT));

                    async.complete();
                }
            });
        }));
    }
}
