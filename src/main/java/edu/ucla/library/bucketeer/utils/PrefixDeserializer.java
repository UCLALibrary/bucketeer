
package edu.ucla.library.bucketeer.utils;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.bucketeer.Constants;
import edu.ucla.library.bucketeer.MessageCodes;

/**
 * A deserializer for classes implementing <code>IFilePathPrefix</code>.
 */
public class PrefixDeserializer extends StdDeserializer<IFilePathPrefix> {

    /**
     * The <code>serialVersionUID<code> for <code>PrefixDeserializer</code>
     */
    private static final long serialVersionUID = 6823136469689445963L;

    private static final Logger LOGGER = LoggerFactory.getLogger(PrefixDeserializer.class, Constants.MESSAGES);

    /**
     * Creates a file path prefix deserializer.
     */
    public PrefixDeserializer() {
        this(null);
    }

    /**
     * Creates a file path prefix deserializer.
     *
     * @param aClass A class to deserialize
     */
    public PrefixDeserializer(final Class<?> aClass) {
        super(aClass);
    }

    @Override
    public IFilePathPrefix deserialize(final JsonParser aParser, final DeserializationContext aContext)
            throws IOException, JsonProcessingException {
        final JsonNode node = aParser.getCodec().readTree(aParser);
        final String fieldName = node.fieldNames().next();

        // TODO: We can get fancier with this in the future
        switch (fieldName) {
            case "uclaRootDir":
                return new UCLAFilePathPrefix(node.get(fieldName).asText());
            case "genericRootDir":
                return new GenericFilePathPrefix(node.get(fieldName).asText());
            default:
                LOGGER.warn(MessageCodes.BUCKETEER_127, GenericFilePathPrefix.class.getSimpleName());
                return new GenericFilePathPrefix();
        }
    }
}
