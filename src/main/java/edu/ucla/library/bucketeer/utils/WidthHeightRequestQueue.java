
package edu.ucla.library.bucketeer.utils;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

import edu.ucla.library.bucketeer.verticles.WidthHeightVerticle;

import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;

/**
 * A queue for requests to the WidthHeightVerticle.
 */
public class WidthHeightRequestQueue {

    /** The queue of width/height requests. */
    private final Deque<QueuedRequest> myQueue = new ArrayDeque<>();

    /** The Vert.x event bus. */
    private final EventBus myEventBus;

    /** The maximum number of requests allowed to be in flight at any moment. */
    private final int myMaxInFlight;

    /** The number of currently in-flight requests. */
    private int myInFlightCount;

    /**
     * Creates a new request queue.
     *
     * @param aVertxRef A reference to the Vert.x instance
     * @param aMaxInFlight The number of in-flight requests
     */
    public WidthHeightRequestQueue(final Vertx aVertxRef, final int aMaxInFlight) {
        myEventBus = aVertxRef.eventBus();
        myMaxInFlight = aMaxInFlight;
    }

    /**
     * Queues a new request.
     *
     * @param aRequest A new width/height request
     * @param aSuccessHandler A handler that handles successful requests
     * @param aFailureHandler A handler that handles failed requests
     * @param aCompletionPromise A promise that the work will get done
     */
    public void enqueue(final JsonObject aRequest, final Consumer<JsonObject> aSuccessHandler,
            final Consumer<Throwable> aFailureHandler, final Promise<Void> aCompletionPromise) {
        myQueue.addLast(new QueuedRequest(aRequest, aSuccessHandler, aFailureHandler, aCompletionPromise));
        pumpQueue();
    }

    /**
     * Pumps the queue.
     */
    private void pumpQueue() {
        while (myInFlightCount < myMaxInFlight && !myQueue.isEmpty()) {
            myInFlightCount++;
            send(myQueue.pollFirst());
        }
    }

    /**
     * Sends a new request.
     *
     * @param aQueuedRequest A previously queued request
     */
    @Deprecated
    private void send(final QueuedRequest aQueuedRequest) {
        myEventBus.<JsonObject>send(WidthHeightVerticle.class.getName(), aQueuedRequest.myRequest, reply -> {
            try {
                if (reply.succeeded()) {
                    aQueuedRequest.mySuccessHandler.accept(reply.result().body());
                } else {
                    aQueuedRequest.myFailureHandler.accept(reply.cause());
                }
            } finally {
                aQueuedRequest.myCompletionPromise.complete();
                myInFlightCount--;
                pumpQueue();
            }
        });
    }

    /**
     * A queued request.
     */
    private static class QueuedRequest {

        /** The request that's been queued. */
        private final JsonObject myRequest;

        /** A handler for successfully processed requests. */
        private final Consumer<JsonObject> mySuccessHandler;

        /** A handler for unsuccessfully processed requests. */
        private final Consumer<Throwable> myFailureHandler;

        /** A promise that the request will be processed. */
        private final Promise<Void> myCompletionPromise;

        /**
         * Creates a new queued request.
         *
         * @param aRequest A request to queue
         * @param aSuccessHandler A handler for successful requests
         * @param aFailureHandler A handler for unsuccessful requests
         * @param aCompletionPromise A promise that the request will be processed
         */
        private QueuedRequest(final JsonObject aRequest, final Consumer<JsonObject> aSuccessHandler,
                final Consumer<Throwable> aFailureHandler, final Promise<Void> aCompletionPromise) {
            myRequest = aRequest;
            mySuccessHandler = aSuccessHandler;
            myFailureHandler = aFailureHandler;
            myCompletionPromise = aCompletionPromise;
        }
    }

}
