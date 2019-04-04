
package edu.ucla.library.bucketeer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import edu.ucla.library.bucketeer.verticles.MainVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class LoadImageHandlerTest {

    private Vertx myVertx;

    private int myPort;

    /**
     * Test set up.
     *
     * @param aContext A testing context
     */
    @Before
    public void setUp(final TestContext aContext) {
        final DeploymentOptions options = new DeploymentOptions();

        // Get a randomly selected open port and use that for our tests
        myPort = Integer.valueOf(System.getProperty("vertx.test.port"));
        options.setConfig(new JsonObject().put("http.port", myPort));

        // Initialize the Vert.x environment and start our main verticle
        myVertx = Vertx.vertx();
        myVertx.deployVerticle(MainVerticle.class.getName(), options, aContext.asyncAssertSuccess());
    }

    /**
     * Test tear down.
     *
     * @param aContext A testing context
     */
    @After
    public void tearDown(final TestContext aContext) {
        myVertx.close(aContext.asyncAssertSuccess());
    }

    /**
     * Our LoadImageHandler tests.
     *
     * @param aContext A testing context
     */
    @Test
    @SuppressWarnings("deprecation")
    public void confirmLoadImageHandlerResponseMatchesSpec(final TestContext aContext) {
        final Async async = aContext.async();
        // Testing the main loadImage path defined in our OpenAPI YAML file returns a correct response
        // when given complete data
        myVertx.createHttpClient().getNow(myPort, "0.0.0.0", "/12345/imageFile.tif", response -> {
            aContext.assertEquals(response.statusCode(), 200);
            // the confirmation JSON object should match the spec, let's verify that
            response.bodyHandler(body -> {
                final JsonObject jsonConfirm = new JsonObject(body.getString(0, body.length()));
                aContext.assertTrue(jsonConfirm.getBoolean("success"));
                aContext.assertEquals(jsonConfirm.getString("message"), "12345/imageFile.tif");
            });
        });
        async.complete();
    }
    @Test
    @SuppressWarnings("deprecation")
    public void confirmLoadImageHandlerFailsWithMissingParam(final TestContext aContext) {
        final Async async = aContext.async();
        // Testing the main loadImage path defined in our OpenAPI YAML file returns an error response
        // when given incomplete data
        myVertx.createHttpClient().getNow(myPort, "0.0.0.0", "/12345/", response -> {
            aContext.assertNotEquals(response.statusCode(), 200);
        });
        async.complete();
    }
    
    
}
