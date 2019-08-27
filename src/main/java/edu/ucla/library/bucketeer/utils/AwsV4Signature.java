
package edu.ucla.library.bucketeer.utils;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import info.freelibrary.util.I18nRuntimeException;
import info.freelibrary.util.StringUtils;

import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.MessageCodes;
import uk.co.lucasweb.aws.v4.signer.HttpRequest;
import uk.co.lucasweb.aws.v4.signer.Signer;
import uk.co.lucasweb.aws.v4.signer.credentials.AwsCredentials;

public class AwsV4Signature {

    private static final String INVALIDATION_URL = "https://{}/2019-03-26/distribution/{}/invalidation";

    private static final String CLOUDFRONT_HOST = "cloudfront.amazonaws.com";

    /** The date format used for timestamping requests */
    private static final String DATE_FORMAT = "yyyyMMdd'T'HHmmss'Z'";

    private String mySignature;

    /**
     * Creates a new AWS v4 signature.
     *
     * @param aCredentials A simple AWS credentials object
     * @param aDistributionId A CloudFront distribution ID
     * @param aInvalidationBatch An invalidation batch
     * @throws JsonProcessingException If there is trouble converting the invalidation batch into a JSON
     *         representation
     */
    public AwsV4Signature(final AwsCredentials aCredentials, final String aDistributionId,
            final InvalidationBatch aInvalidationBatch) throws JsonProcessingException {
        this(aCredentials, aDistributionId, aInvalidationBatch, new SimpleDateFormat(DATE_FORMAT, Locale.US).format(
                new Date()));
    }

    /**
     * Creates a new AWS v4 signature.
     *
     * @param aCredentials A simple AWS credentials object
     * @param aDistributionId A CloudFront distribution ID.
     * @param aInvalidationBatch An invalidation batch
     * @param aDateTime A date time string that's formatted: yyyyMMdd'T'HHmmss'Z'
     * @throws JsonProcessingException If there is trouble converting the invalidation batch into a JSON
     *         representation
     */
    public AwsV4Signature(final AwsCredentials aCredentials, final String aDistributionId,
            final InvalidationBatch aInvalidationBatch, final String aDateTime) throws JsonProcessingException,
            I18nRuntimeException {
        final ObjectMapper mapper = new XmlMapper().enable(SerializationFeature.INDENT_OUTPUT);

        // Treat invalid datetimes as programming errors (i.e. assume they are checked prior to this point)
        if (isInvalid(aDateTime)) {
            throw new I18nRuntimeException(Constants.MESSAGES, MessageCodes.BUCKETEER_100, aDateTime);
        }

        try {
            final String url = StringUtils.format(INVALIDATION_URL, CLOUDFRONT_HOST, aDistributionId);
            final String invalidationJSON = mapper.writeValueAsString(aInvalidationBatch);
            final HttpRequest request = new HttpRequest("POST", new URI(url));
            final Signer.Builder signer = Signer.builder().awsCredentials(aCredentials);
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] encodedHash = digest.digest(invalidationJSON.getBytes(StandardCharsets.UTF_8));

            signer.header("Host", CLOUDFRONT_HOST);
            signer.header("x-amz-date", aDateTime);
            signer.header("x-amz-content-sha256", hashToHex(encodedHash));

            mySignature = signer.build(request, "cloudfront", invalidationJSON).getSignature();
        } catch (final URISyntaxException | NoSuchAlgorithmException details) {
            throw new I18nRuntimeException(details);
        }
    }

    /**
     * Returns a string representation of the AWS v4 signature.
     *
     * @return A string representation of the AWS v4 signature
     */
    @Override
    public String toString() {
        return mySignature;
    }

    private boolean isInvalid(final String aDateTime) {
        try {
            final DateFormat format = new SimpleDateFormat(DATE_FORMAT);

            format.setLenient(false);
            format.parse(aDateTime);

            return false;
        } catch (final ParseException | StringIndexOutOfBoundsException details) {
            return true;
        }
    }

    private String hashToHex(final byte[] aEncodedHash) {
        final StringBuilder hexString = new StringBuilder();

        for (int index = 0; index < aEncodedHash.length; index++) {
            final String hex = Integer.toHexString(0xff & aEncodedHash[index]);

            if (hex.length() == 1) {
                hexString.append('0');
            }

            hexString.append(hex);
        }

        return hexString.toString();
    }

}
