
package edu.ucla.library.bucketeer;

import java.util.Iterator;

import org.junit.BeforeClass;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

import io.vertx.core.json.jackson.DatabindCodec;

/**
 * Creates a base testing class that makes sure Jackson's modules have all been initialized.
 */
public abstract class AbstractBucketeerTest {

    /**
     * Sets up the testing environment.
     */
    @BeforeClass
    public static void setupEnv() {
        final ObjectMapper mapper = DatabindCodec.mapper();
        final Iterator<Object> iterator = mapper.getRegisteredModuleIds().iterator();

        boolean modulesLoaded = false;

        // Check to confirm the JDK 8 (and greater) module has been loaded
        while (iterator.hasNext()) {
            if (Jdk8Module.class.getName().equals(iterator.next().toString())) {
                modulesLoaded = true;
            }
        }

        if (!modulesLoaded) {
            mapper.findAndRegisterModules();
        }
    }
}
