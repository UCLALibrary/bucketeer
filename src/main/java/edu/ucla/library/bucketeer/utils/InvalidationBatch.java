
package edu.ucla.library.bucketeer.utils;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * A batch of CloudFront invalidation requests.
 */
@JacksonXmlRootElement(namespace = InvalidationBatch.NAMESPACE, localName = "InvalidationBatch")
@JsonPropertyOrder({ "myCallerRef", "myPaths" })
public class InvalidationBatch {

    /**
     * The XML namespace for the CloudFront invalidation request.
     */
    public static final String NAMESPACE = "http://cloudfront.amazonaws.com/doc/2019-03-26/";

    @JacksonXmlProperty(namespace = InvalidationBatch.NAMESPACE, localName = "CallerReference")
    private final String myCallerRef;

    @JacksonXmlProperty(namespace = InvalidationBatch.NAMESPACE, localName = "Paths")
    private final BatchPaths myPaths;

    /**
     * Create the invalidation batch.
     *
     * @param aCallerRef A caller reference for the invalidation batch
     */
    public InvalidationBatch(final String aCallerRef) {
        myPaths = new BatchPaths();
        myCallerRef = aCallerRef;
    }

    /**
     * Gets the caller reference.
     *
     * @return The caller reference
     */
    @JsonIgnore
    public String getCallerRef() {
        return myCallerRef;
    }

    /**
     * Gets the path at the supplied index position.
     *
     * @param aIndex The position of the desired path
     * @return The invalidation batch path
     */
    @JsonIgnore
    public String getPath(final int aIndex) {
        return myPaths.getItem(aIndex);
    }

    /**
     * Add an path to the invalidation batch.
     *
     * @param aPath A path to add to the invalidation batch
     * @return True if the path was added; else, false
     */
    @JsonIgnore
    public boolean addPath(final String aPath) {
        return myPaths.addItem(aPath);
    }

    /**
     * Gets the number of paths in the invalidation batch.
     *
     * @return The number of paths in the invalidation batch
     */
    @JsonIgnore
    public int size() {
        return myPaths.getQuantity();
    }

    /**
     * A collection of paths to be updated in the invalidation batch.
     */
    public class BatchPaths {

        @JacksonXmlElementWrapper(namespace = InvalidationBatch.NAMESPACE, localName = "Items")
        @JacksonXmlProperty(namespace = InvalidationBatch.NAMESPACE, localName = "Path")
        private final List<String> myPaths;

        /**
         * Create the invalidation batch paths object.
         */
        public BatchPaths() {
            myPaths = new ArrayList<>();
        }

        /**
         * Gets the path at the supplied index position.
         *
         * @param aIndex The position of the desired path
         * @return The invalidation batch path
         */
        @JsonIgnore
        public String getItem(final int aIndex) {
            return myPaths.get(aIndex);
        }

        /**
         * Add an path to the invalidation batch.
         *
         * @param aPath A path to add to the invalidation batch
         * @return True if the path was added; else, false
         */
        @JsonIgnore
        public boolean addItem(final String aPath) {
            return myPaths.add(aPath);
        }

        /**
         * Gets the number of invalidation batch paths.
         *
         * @return The number of invalidation batch paths
         */
        @JacksonXmlProperty(namespace = InvalidationBatch.NAMESPACE, localName = "Quantity")
        public int getQuantity() {
            return myPaths.size();
        }

    }
}
