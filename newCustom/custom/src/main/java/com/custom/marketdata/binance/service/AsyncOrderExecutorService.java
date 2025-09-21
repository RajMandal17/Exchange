package com.custom.marketdata.binance.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Consumer;

/**
 * Thread-safe async order execution service for batching and queueing operations.
 * This service helps prevent database bottlenecks by batching operations and
 * using async processing queues.
 */
@Service
public class AsyncOrderExecutorService {
    private static final Logger logger = LoggerFactory.getLogger(AsyncOrderExecutorService.class);

    // Configuration constants - optimized for aggressive queue draining
    private static final int QUEUE_CAPACITY = 50000;
    private static final int BATCH_SIZE = 500;
    private static final long BATCH_TIMEOUT_MS = 10;
    private static final long KEEP_ALIVE_TIME = 60L;
    // Adaptive thread pool/drain thread settings
    private static final int MIN_CORE_POOL_SIZE = 8;
    private static final int MAX_CORE_POOL_SIZE = 32;
    private static final int MIN_DRAIN_THREADS = 8;
    private static final int MAX_DRAIN_THREADS = 32;
    private static final int ADAPTIVE_CHECK_INTERVAL_MS = 2000;
    private static final int QUEUE_HIGH_THRESHOLD = BATCH_SIZE * 8;
    private static final int QUEUE_LOW_THRESHOLD = BATCH_SIZE * 2;

    //[ADAPTIVE] Scaling up: corePoolSize=12 maxPoolSize=32 drainThreads=10
    // Thread-safe components
    private final BlockingQueue<OrderOperation> operationQueue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    // Use a SynchronousQueue so the ThreadPoolExecutor will create new threads up to
    // MAX_POOL_SIZE when under load instead of queuing tasks indefinitely in an
    // unbounded LinkedBlockingQueue (which effectively capped threads to CORE_POOL_SIZE).
    private final ThreadPoolExecutor executorService = new ThreadPoolExecutor(
            MIN_CORE_POOL_SIZE, MAX_CORE_POOL_SIZE, KEEP_ALIVE_TIME, TimeUnit.SECONDS,
            new SynchronousQueue<Runnable>(),
            r -> {
                Thread t = new Thread(r, "async-order-executor-" + System.currentTimeMillis());
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy());

    // Metrics
    private final AtomicInteger queueSize = new AtomicInteger(0);
    private final AtomicLong processedOperations = new AtomicLong(0);
    private final AtomicLong droppedOperations = new AtomicLong(0);

    // Batch processing control
    private volatile boolean running = true;

    // Multiple drain threads for aggressive queue processing
    private ScheduledExecutorService[] drainThreads = new ScheduledExecutorService[MIN_DRAIN_THREADS];
    private int currentDrainThreads = MIN_DRAIN_THREADS;
    private final ScheduledExecutorService adaptiveMonitor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "adaptive-monitor");
        t.setDaemon(true);
        return t;
    });
    // Throttle scaling: minimum interval between scale actions (ms)
    private static final long MIN_SCALE_INTERVAL_MS = 5000;
    private volatile long lastScaleTime = 0L;

    @PostConstruct
    public void initialize() {
        logger.info("Initializing AsyncOrderExecutorService with {} drain threads, batch size: {}, queue capacity: {}",
                currentDrainThreads, BATCH_SIZE, QUEUE_CAPACITY);
        startDrainThreads(currentDrainThreads);
        adaptiveMonitor.scheduleWithFixedDelay(this::adaptiveTune, ADAPTIVE_CHECK_INTERVAL_MS, ADAPTIVE_CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private synchronized void startDrainThreads(int numThreads) {
        drainThreads = new ScheduledExecutorService[numThreads];
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            drainThreads[i] = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "batch-drain-" + threadId);
                t.setDaemon(true);
                return t;
            });
            drainThreads[i].scheduleWithFixedDelay(this::processBatch,
                    threadId * 2, BATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }
        currentDrainThreads = numThreads;
    }

    // private synchronized void stopDrainThreads() {
    //     if (drainThreads != null) {
    //         for (ScheduledExecutorService s : drainThreads) {
    //             if (s != null) {
    //                 s.shutdownNow();
    //             }
    //         }
    //     }
    // }
private synchronized void stopDrainThreads() {
    if (drainThreads != null) {
        for (ScheduledExecutorService s : drainThreads) {
            if (s != null) {
                s.shutdown(); // Use shutdown() instead of shutdownNow()
                try {
                    s.awaitTermination(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
    private void adaptiveTune() {
        int qSize = queueSize.get();
        int curCore = executorService.getCorePoolSize();
        int curMax = executorService.getMaximumPoolSize();
        int curDrains = currentDrainThreads;
        long now = System.currentTimeMillis();

        // Throttle scaling: only allow scaling if enough time has passed
        if (now - lastScaleTime < MIN_SCALE_INTERVAL_MS) {
            return;
        }

        // If queue is high, scale up
        if (qSize > QUEUE_HIGH_THRESHOLD && curCore < MAX_CORE_POOL_SIZE) {
            int newCore = Math.min(curCore + 4, MAX_CORE_POOL_SIZE);
            int newMax = Math.min(curMax + 4, MAX_CORE_POOL_SIZE);
            executorService.setCorePoolSize(newCore);
            executorService.setMaximumPoolSize(newMax);
            int newDrains = Math.min(curDrains + 2, MAX_DRAIN_THREADS);
            if (newDrains > curDrains) {
                logger.info("[ADAPTIVE] Scaling up: corePoolSize={} maxPoolSize={} drainThreads={}", newCore, newMax, newDrains);
                stopDrainThreads();
                startDrainThreads(newDrains);
            }
            lastScaleTime = now;
        }
        // If queue is low, scale down
        else if (qSize < QUEUE_LOW_THRESHOLD && curCore > MIN_CORE_POOL_SIZE) {
            int newCore = Math.max(curCore - 2, MIN_CORE_POOL_SIZE);
            int newMax = Math.max(curMax - 2, MIN_CORE_POOL_SIZE);
            executorService.setCorePoolSize(newCore);
            executorService.setMaximumPoolSize(newMax);
            int newDrains = Math.max(curDrains - 1, MIN_DRAIN_THREADS);
            if (newDrains < curDrains) {
                logger.info("[ADAPTIVE] Scaling down: corePoolSize={} maxPoolSize={} drainThreads={}", newCore, newMax, newDrains);
                stopDrainThreads();
                startDrainThreads(newDrains);
            }
            lastScaleTime = now;
        }
    }

    /**
     * Submits an order operation for async processing
     */
    public CompletableFuture<Void> submitOrderOperation(String productId, String operation,
                                                        Consumer<String> executor) {
        if (!running) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("AsyncOrderExecutorService is shutting down"));
        }

        OrderOperation orderOp = new OrderOperation(productId, operation, executor);

        boolean queued = operationQueue.offer(orderOp);
        if (!queued) {
            droppedOperations.incrementAndGet();
            logger.warn("Order operation queue is full. Dropping operation for product: {}", productId);
            return CompletableFuture.failedFuture(
                    new RejectedExecutionException("Order operation queue is full"));
        }

        int currentQueueSize = queueSize.incrementAndGet();

        // Trigger immediate processing if queue is getting large (adaptive processing)
        if (currentQueueSize > BATCH_SIZE * 2) {
            logger.debug("Queue size {} exceeds threshold, triggering immediate batch processing", currentQueueSize);
            // Trigger an immediate batch process on one of the drain threads
            if (drainThreads[0] != null && !drainThreads[0].isShutdown()) {
                drainThreads[0].execute(this::processBatch);
            }
        }

        return orderOp.getFuture();
    }

    /**
     * Process operations in batches for better performance
     */
    private void processBatch() {
        if (!running || operationQueue.isEmpty()) {
            return;
        }

        // Adaptive batch size - larger when queue is backed up
        int currentQueueSize = queueSize.get();
        int adaptiveBatchSize = Math.min(BATCH_SIZE * 4, Math.max(BATCH_SIZE, currentQueueSize / 2));

        List<OrderOperation> batch = new ArrayList<>(adaptiveBatchSize);

        // Drain operations from queue up to adaptive batch size
        operationQueue.drainTo(batch, adaptiveBatchSize);

        if (batch.isEmpty()) {
            return;
        }

        queueSize.addAndGet(-batch.size());

        logger.debug("Processing batch of {} order operations (adaptive size: {}, queue: {})",
                batch.size(), adaptiveBatchSize, currentQueueSize);

        // Record a dequeued timestamp for each operation so we can measure
        // queue time (enqueue -> drained) separate from executor wait (drained -> exec start)
        final long batchDequeuedTime = System.nanoTime();
        for (OrderOperation op : batch) {
            op.setDequeuedTime(batchDequeuedTime);
        }

        // Submit each operation to the executor for execution.
        // We intentionally use executorService.execute to avoid extra CompletableFuture scheduling
        for (OrderOperation op : batch) {
            executeOperation(op);
        }
    }

    /**
     * Execute a single order operation
     */
    private void executeOperation(OrderOperation operation) {
        // Submit work to executor; CallerRunsPolicy will execute in caller thread if pool is saturated
        executorService.execute(() -> {
            long execStart = System.nanoTime(); // actual execution start time

            try {
                long dequeued = operation.getDequeuedTime() > 0 ? operation.getDequeuedTime() : execStart;
                long queueTimeNs = dequeued - operation.getEnqueueTime();
                long executorQueueWaitNs = execStart - dequeued;

                logger.debug("TIME_B: Operation dequeued for {} after {} ns in queue",
                        operation.getProductId(), queueTimeNs);

                if (executorQueueWaitNs > 0) {
                    logger.debug("TIME_B_EXEC_WAIT: Operation {} waited {} ns between drain and execution start",
                            operation.getProductId(), executorQueueWaitNs);
                }

                operation.getExecutor().accept(operation.getProductId());
                operation.complete();
                processedOperations.incrementAndGet();

                long completeTime = System.nanoTime();
                logger.debug("TIME_B_END: Operation completed for {} in {} ns",
                        operation.getProductId(), (completeTime - execStart));

            } catch (Exception e) {
                logger.error("Error executing order operation for product {}: {}",
                        operation.getProductId(), e.getMessage(), e);
                operation.completeExceptionally(e);
            }
        });
    }

    /**
     * Get current queue metrics
     */
    public QueueMetrics getMetrics() {
        return new QueueMetrics(
                queueSize.get(),
                processedOperations.get(),
                droppedOperations.get(),
                operationQueue.remainingCapacity()
        );
    }

    /**
     * Force process all remaining operations (useful for shutdown)
     */
    public void flushQueue() {
        logger.info("Flushing remaining {} operations from queue", queueSize.get());

        while (!operationQueue.isEmpty()) {
            processBatch();
            try {
                Thread.sleep(100); // Small delay to allow processing
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down AsyncOrderExecutorService");
        running = false;

        // Stop adaptive monitor
        adaptiveMonitor.shutdownNow();

        // Flush remaining operations
        flushQueue();

        // Shutdown drain threads
        stopDrainThreads();

        // Shutdown executor service
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warn("Executor service did not terminate gracefully, forcing shutdown");
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.warn("Interrupted while waiting for executor service to terminate");
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }

        QueueMetrics finalMetrics = getMetrics();
        logger.info("AsyncOrderExecutorService shutdown complete. Final metrics: {}", finalMetrics);
    }

    /**
     * Represents an order operation to be executed asynchronously
     */
    private static class OrderOperation {
        private final String productId;
        private final String operation;
        private final Consumer<String> executor;
        private final CompletableFuture<Void> future;
        private final long enqueueTime;
        // Set when the batch drains this operation from the queue.
        private volatile long dequeuedTime = 0L;
        public OrderOperation(String productId, String operation, Consumer<String> executor) {
            this.productId = productId;
            this.operation = operation;
            this.executor = executor;
            this.future = new CompletableFuture<>();
            this.enqueueTime = System.nanoTime();
        }

        public long getDequeuedTime() { return dequeuedTime; }
        public void setDequeuedTime(long dequeuedTime) { this.dequeuedTime = dequeuedTime; }

        public String getProductId() { return productId; }
        public String getOperation() { return operation; }
        public Consumer<String> getExecutor() { return executor; }
        public CompletableFuture<Void> getFuture() { return future; }
        public long getEnqueueTime() { return enqueueTime; }

        public void complete() { future.complete(null); }
        public void completeExceptionally(Throwable ex) { future.completeExceptionally(ex); }
    }

    /**
     * Queue metrics for monitoring
     */
    public static class QueueMetrics {
        private final int currentQueueSize;
        private final long totalProcessed;
        private final long totalDropped;
        private final int remainingCapacity;

        public QueueMetrics(int currentQueueSize, long totalProcessed, long totalDropped, int remainingCapacity) {
            this.currentQueueSize = currentQueueSize;
            this.totalProcessed = totalProcessed;
            this.totalDropped = totalDropped;
            this.remainingCapacity = remainingCapacity;
        }

        public int getCurrentQueueSize() { return currentQueueSize; }
        public long getTotalProcessed() { return totalProcessed; }
        public long getTotalDropped() { return totalDropped; }
        public int getRemainingCapacity() { return remainingCapacity; }

        @Override
        public String toString() {
            return String.format("QueueMetrics{queueSize=%d, processed=%d, dropped=%d, remaining=%d}",
                    currentQueueSize, totalProcessed, totalDropped, remainingCapacity);
        }
    }
}
