package edu.ucla.library.bucketeer.utils;

import io.vertx.ext.unit.Async;

/**
 * Utilities related to working with test IDs.
 */
public final class TestUtils {

    /**
    * Creates new TestUtils instance.
    */
    private TestUtils() {
    }

    /**
     * Completes an asynchronous task.
     *
     * @param aAsyncTask An asynchronous task
     */
    public static void complete(final Async aAsyncTask) {
        if (!aAsyncTask.isCompleted()) {
            aAsyncTask.complete();
        }
    }
}
