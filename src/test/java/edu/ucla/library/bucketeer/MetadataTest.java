
package edu.ucla.library.bucketeer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Tests for the <code>Metadata</code> constants.
 */
public class MetadataTest {

    /**
     * Tests "File Name" constant.
     */
    @Test
    public final void testFileName() {
        assertEquals("File Name", Metadata.FILE_NAME);
    }

    /**
     * Tests "Item ARK" constant.
     */
    @Test
    public final void testItemID() {
        assertEquals("Item ARK", Metadata.ITEM_ID);
    }

    /**
     * Tests "Object Type" constant.
     */
    @Test
    public final void testObjectType() {
        assertEquals("Object Type", Metadata.OBJECT_TYPE);
    }

    /**
     * Tests "Bucketeer State" constant.
     */
    @Test
    public final void testBucketeerState() {
        assertEquals("Bucketeer State", Metadata.BUCKETEER_STATE);
    }

    /**
     * Tests "IIIF Access URL" constant.
     */
    @Test
    public final void testIiifAccessURL() {
        assertEquals("IIIF Access URL", Metadata.IIIF_ACCESS_URL);
    }

    /**
     * Tests "Collection" constant.
     */
    @Test
    public final void testCollection() {
        assertEquals("Collection", Metadata.COLLECTION);
    }

}
