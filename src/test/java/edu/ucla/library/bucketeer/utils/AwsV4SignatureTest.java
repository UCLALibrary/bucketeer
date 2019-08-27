
package edu.ucla.library.bucketeer.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.fasterxml.jackson.core.JsonProcessingException;

import info.freelibrary.util.I18nRuntimeException;
import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.bucketeer.Config;
import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.MessageCodes;
import io.vertx.config.ConfigRetriever;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import uk.co.lucasweb.aws.v4.signer.credentials.AwsCredentials;

/**
 * Tests the AWS v4 signature class.
 */
@RunWith(VertxUnitRunner.class)
public class AwsV4SignatureTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AwsV4SignatureTest.class, Constants.MESSAGES);

    private static final String DATETIME = "20190822T163548Z";

    private static final String BAD_DATETIME = "201908Z";

    private static final String FAKE_ACCESS_KEY = "AKIAIKEAAIKOAIKOAIKO";

    private static final String FAKE_SECRET_KEY = "PunxsutawneyPhilKnowsTheSecretKeyAskHim!";

    private static final String FAKE_CLOUDFRONT_ID = "NUMBERANDCHARS";

    /**
     * Run the tests on the vertx context.
     */
    @Rule
    public RunTestOnContext myRunTestOnContextRule = new RunTestOnContext();

    private String myAccessKey;

    private String mySecretKey;

    private String myCloudFrontID;

    /**
     * Set up the testing environment.
     *
     * @param aContext A test context
     * @throws Exception If there is trouble starting Vert.x or configuring the tests
     */
    @Before
    public void setUp(final TestContext aContext) throws Exception {
        final Vertx vertx = myRunTestOnContextRule.vertx();
        final ConfigRetriever configRetriever = ConfigRetriever.create(vertx);
        final Async asyncTask = aContext.async();

        configRetriever.getConfig(getConfig -> {
            if (getConfig.succeeded()) {
                final JsonObject config = getConfig.result();

                if ("true".equals(System.getProperty("liveSignatureTest"))) {
                    // This causes automated tests to fail (they expect known values)
                    myAccessKey = config.getString(Config.S3_ACCESS_KEY);
                    mySecretKey = config.getString(Config.S3_SECRET_KEY);
                    myCloudFrontID = config.getString(Config.CDN_DISTRO_ID);
                } else {
                    myAccessKey = FAKE_ACCESS_KEY;
                    mySecretKey = FAKE_SECRET_KEY;
                    myCloudFrontID = FAKE_CLOUDFRONT_ID;
                }

                asyncTask.complete();
            } else {
                aContext.fail(getConfig.cause());
            }
        });
    }

    /**
     * Tests getting the AWS v4 signature.
     *
     * @throws JsonProcessingException An exception thrown if the InvalidationBatch can't be serialized to JSON
     */
    @Test
    public final void testToString() throws JsonProcessingException {
        final InvalidationBatch invalidation = new InvalidationBatch("/healthcheckimage.jpx");
        final AwsCredentials credentials = new AwsCredentials(myAccessKey, mySecretKey);
        final AwsV4Signature signature = new AwsV4Signature(credentials, myCloudFrontID, invalidation, DATETIME);
        final StringBuilder expected = new StringBuilder("AWS4-HMAC-SHA256 ");

        expected.append("Credential=AKIAIKEAAIKOAIKOAIKO/20190822/us-east-1/cloudfront/aws4_request, ");
        expected.append("SignedHeaders=host;x-amz-content-sha256;x-amz-date, ");
        expected.append("Signature=27764c480988637f36b9a642dcf705510c970ffa926c6d7621a1d9d99bf8a685");

        assertEquals(expected.toString(), signature.toString());
    }

    /**
     * Tests getting the AWS v4 signature with a bad datetime.
     *
     * @throws JsonProcessingException An exception thrown if the InvalidationBatch can't be serialized to JSON
     */
    @Test
    public final void testToStringBadDateTime() throws JsonProcessingException {
        final InvalidationBatch invalidation = new InvalidationBatch("/some-s3-resource");
        final AwsCredentials credentials = new AwsCredentials(myAccessKey, mySecretKey);

        try {
            new AwsV4Signature(credentials, myCloudFrontID, invalidation, BAD_DATETIME);
            fail(LOGGER.getMessage(MessageCodes.BUCKETEER_101, BAD_DATETIME));
        } catch (final I18nRuntimeException details) {
            // This is expected so we don't need to do anything
        }
    }

}
