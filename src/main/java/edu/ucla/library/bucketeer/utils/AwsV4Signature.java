
package edu.ucla.library.bucketeer.utils;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private static final String DATE_TIME_FORMAT = "yyyyMMdd'T'HHmmss'Z'";

    /** The date format used for timestamping requests */
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(DATE_TIME_FORMAT)
            .withZone(ZoneOffset.UTC);

    private static final String HOST = "Host";

    private static final String X_AMZ_DATE = "X-Amz-Date";

    private static final String X_AMZ_CONTENT_SHA256 = "x-amz-content-sha256";

    private String mySignature;

    private final Map<String, String> myHeaders;

    /**
     * Creates a new AWS v4 signature.
     *
     * @param aCredentials A simple AWS credentials object
     * @param aDistributionId A CloudFront distribution ID
     * @param aInvalidation An invalidation batch
     * @throws JsonProcessingException If there is trouble converting the invalidation batch into a JSON
     *         representation
     */
    public AwsV4Signature(final AwsCredentials aCredentials, final String aDistributionId, final String aInvalidation)
            throws JsonProcessingException {
        this(aCredentials, aDistributionId, aInvalidation, LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC)
                .format(DATE_TIME_FORMATTER));
    }

    /**
     * Creates a new AWS v4 signature.
     *
     * @param aCredentials A simple AWS credentials object
     * @param aDistributionId A CloudFront distribution ID.
     * @param aInvalidation An invalidation
     * @param aDateTime A date time string that's formatted: yyyyMMdd'T'HHmmss'Z'
     * @throws JsonProcessingException If there is trouble converting the invalidation batch into a JSON
     *         representation
     */
    public AwsV4Signature(final AwsCredentials aCredentials, final String aDistributionId, final String aInvalidation,
            final String aDateTime) throws JsonProcessingException, I18nRuntimeException {
        final ObjectMapper mapper = new XmlMapper(); // .enable(SerializationFeature.INDENT_OUTPUT);

        myHeaders = new LinkedHashMap<>(3);

        // Treat invalid datetimes as programming errors (i.e. assume they are checked prior to this point)
        if (isInvalid(aDateTime)) {
            throw new I18nRuntimeException(Constants.MESSAGES, MessageCodes.BUCKETEER_100, aDateTime);
        }

        try {
            final String url = StringUtils.format(INVALIDATION_URL, CLOUDFRONT_HOST, aDistributionId);
            final HttpRequest request = new HttpRequest("POST", new URI(url));
            final Signer.Builder signer = Signer.builder().awsCredentials(aCredentials);
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final String sha256Hex = hashToHex(digest.digest(aInvalidation.getBytes(StandardCharsets.UTF_8)));

            // signer.region(region);

            // Three headers to add; if this changes, adjust the LinkedHashMap size above
            signer.header(HOST, CLOUDFRONT_HOST);
            myHeaders.put(HOST, CLOUDFRONT_HOST);
            signer.header(X_AMZ_DATE, aDateTime);
            myHeaders.put(X_AMZ_DATE, aDateTime);
            signer.header(X_AMZ_CONTENT_SHA256, sha256Hex);
            myHeaders.put(X_AMZ_CONTENT_SHA256, sha256Hex);

            mySignature = signer.build(request, "cloudfront", sha256Hex).getSignature();
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

    /**
     * Get a map with the headers that the AWS v4 signature has added to the request.
     *
     * @return The added headers
     */
    public Map<String, String> getHeaders() {
        return Collections.unmodifiableMap(myHeaders);
    }

    /**
     * Tests whether the supplied date time string is valid.
     *
     * @param aDateTime A date time in uncontrolled string form
     * @return True if the supplied string is a valid date time; else, false
     */
    private boolean isInvalid(final String aDateTime) {
        try {
            final DateFormat format = new SimpleDateFormat(DATE_TIME_FORMAT);

            format.setLenient(false);
            format.parse(aDateTime);

            return false;
        } catch (final ParseException | StringIndexOutOfBoundsException details) {
            return true;
        }
    }

    /**
     * Converts the supplied hash to hex.
     *
     * @param aEncodedHash A hash to be converted into hex format
     * @return The supplied hash in hex format
     */
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
