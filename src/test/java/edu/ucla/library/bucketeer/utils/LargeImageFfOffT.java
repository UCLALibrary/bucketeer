
package edu.ucla.library.bucketeer.utils;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import edu.ucla.library.bucketeer.Config;
import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.Features;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

/**
 * Tests the large image feature flag when it's not enabled.
 */
@RunWith(VertxUnitRunner.class)
public class LargeImageFfOffT {

    /**
     * A JUnit rule to run the test on the active context.
     */
    @Rule
    public RunTestOnContext myTestContext = new RunTestOnContext();

    /**
     * Tests the status of the large image feature flag.
     *
     * @param aContext A test context
     */
    @Test
    public void testStatus(final TestContext aContext) {
        final WebClient webClient = WebClient.create(myTestContext.vertx());
        final int port = Integer.parseInt(System.getProperty(Config.HTTP_PORT));
        final Async asyncTask = aContext.async();

        webClient.get(port, Constants.UNSPECIFIED_HOST, "/status").send(statusCheck -> {
            if (statusCheck.succeeded()) {
                final HttpResponse<Buffer> response = statusCheck.result();
                final JsonObject status = response.bodyAsJsonObject();
                final JsonObject features = status.getJsonObject(Features.FEATURES);

                aContext.assertEquals(status.getString(Constants.STATUS), "ok");
                aContext.assertEquals(true, features.getBoolean(Features.ENABLED, false));
                aContext.assertEquals(false, features.getBoolean(Features.LARGE_IMAGE_ROUTING, true));

                asyncTask.complete();
            } else {
                aContext.fail(statusCheck.cause());
            }
        });
    }
}
