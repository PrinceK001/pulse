/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android;

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import android.util.Log;
import androidx.annotation.NonNull;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.internal.DaemonThreadFactory;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of the {@link SpanProcessor} that batches spans exported by the SDK then
 * pushes them to the exporter pipeline.
 *
 * <p>All spans reported by the SDK implementation are first added to a synchronized queue (with a
 * {@code maxQueueSize} maximum size, if queue is full spans are dropped). Spans are exported either
 * when there are {@code maxExportBatchSize} pending spans or {@code scheduleDelayNanos} has passed
 * since the last export finished.
 *
 * @since 1.27.0
 */
public final class SB implements SpanProcessor {

    private static final String WORKER_THREAD_NAME =
            BatchSpanProcessor.class.getSimpleName() + "_WorkerThread";
    private static final AttributeKey<String> SPAN_PROCESSOR_TYPE_LABEL =
            AttributeKey.stringKey("processorType");
    private static final AttributeKey<Boolean> SPAN_PROCESSOR_DROPPED_LABEL =
            AttributeKey.booleanKey("dropped");
    private static final String SPAN_PROCESSOR_TYPE_VALUE =
            BatchSpanProcessor.class.getSimpleName();

    private final Worker worker;
    private final AtomicBoolean isShutdown = new AtomicBoolean(false);

    /**
     * Returns a new Builder for {@link io.opentelemetry.sdk.trace.export.BatchSpanProcessor}.
     *
     * @param spanExporter the {@link SpanExporter} to which the Spans are pushed
     * @return a new {@link io.opentelemetry.sdk.trace.export.BatchSpanProcessor}.
     * @throws NullPointerException if the {@code spanExporter} is {@code null}.
     */
    public static SBB builder(SpanExporter spanExporter) {
        return new SBB(spanExporter);
    }

    SB(
            SpanExporter spanExporter,
            MeterProvider meterProvider,
            long scheduleDelayNanos,
            int maxQueueSize,
            int maxExportBatchSize,
            long exporterTimeoutNanos) {
        this.worker =
                new Worker(
                        spanExporter,
                        meterProvider,
                        scheduleDelayNanos,
                        maxExportBatchSize,
                        exporterTimeoutNanos,
                        new ArrayBlockingQueue<>(
                                maxQueueSize)); // TODO: use JcTools.newFixedSizeQueue(..)
        Thread workerThread = new DaemonThreadFactory(WORKER_THREAD_NAME).newThread(worker);
        workerThread.start();
    }

    @Override
    public void onStart(Context context, ReadWriteSpan span) {
        // No-op for batch processor
    }

    @Override
    public boolean isStartRequired() {
        return false;
    }

    @Override
    public void onEnd(ReadableSpan span) {
        if (span == null) {
            return;
        }
        worker.addSpan(span);
    }

    @Override
    public boolean isEndRequired() {
        return true;
    }

    @Override
    public CompletableResultCode shutdown() {
        if (isShutdown.getAndSet(true)) {
            return CompletableResultCode.ofSuccess();
        }
        return worker.shutdown();
    }

    @Override
    public CompletableResultCode forceFlush() {
        return worker.forceFlush();
    }

    /**
     * Return the processor's configured {@link SpanExporter}.
     *
     * @since 1.37.0
     */
    public SpanExporter getSpanExporter() {
        return worker.spanExporter;
    }

    // Visible for testing
    List<SpanData> getBatch() {
        return worker.batch;
    }

    @NonNull
    @Override
    public String toString() {
        return "BatchSpanProcessor{"
                + "spanExporter="
                + worker.spanExporter
                + ", scheduleDelayNanos="
                + worker.scheduleDelayNanos
                + ", maxExportBatchSize="
                + worker.maxExportBatchSize
                + ", exporterTimeoutNanos="
                + worker.exporterTimeoutNanos
                + '}';
    }

    // Worker is a thread that batches multiple spans and calls the registered SpanExporter to
    // export
    // the data.
    private static final class Worker implements Runnable {

        private static final Logger logger = Logger.getLogger(Worker.class.getName());

        private final LongCounter processedSpansCounter;
        private final Attributes droppedAttrs;
        private final Attributes exportedAttrs;

        private final SpanExporter spanExporter;
        private final long scheduleDelayNanos;
        private final int maxExportBatchSize;
        private final long exporterTimeoutNanos;

        private long nextExportTime;

        private final Queue<ReadableSpan> queue;
        // When waiting on the spans queue, exporter thread sets this atomic to the number of more
        // spans it needs before doing an export. Writer threads would then wait for the queue to
        // reach
        // spansNeeded size before notifying the exporter thread about new entries.
        // Integer.MAX_VALUE is used to imply that exporter thread is not expecting any signal.
        // Since
        // exporter thread doesn't expect any signal initially, this value is initialized to
        // Integer.MAX_VALUE.
        private final AtomicInteger spansNeeded = new AtomicInteger(Integer.MAX_VALUE);
        private final BlockingQueue<Boolean> signal;
        private final AtomicReference<CompletableResultCode> flushRequested =
                new AtomicReference<>();
        private volatile boolean continueWork = true;
        private final ArrayList<SpanData> batch;

        private Worker(
                SpanExporter spanExporter,
                MeterProvider meterProvider,
                long scheduleDelayNanos,
                int maxExportBatchSize,
                long exporterTimeoutNanos,
                Queue<ReadableSpan> queue) {
            this.spanExporter = spanExporter;
            this.scheduleDelayNanos = scheduleDelayNanos;
            this.maxExportBatchSize = maxExportBatchSize;
            this.exporterTimeoutNanos = exporterTimeoutNanos;
            this.queue = queue;
            this.signal = new ArrayBlockingQueue<>(1);
            Meter meter = meterProvider.meterBuilder("io.opentelemetry.sdk.trace").build();
            meter.gaugeBuilder("queueSize")
                    .ofLongs()
                    .setDescription("The number of items queued")
                    .setUnit("1")
                    .buildWithCallback(
                            result ->
                                    result.record(
                                            queue.size(),
                                            Attributes.of(
                                                    SPAN_PROCESSOR_TYPE_LABEL,
                                                    SPAN_PROCESSOR_TYPE_VALUE)));
            processedSpansCounter =
                    meter.counterBuilder("processedSpans")
                            .setUnit("1")
                            .setDescription(
                                    "The number of spans processed by the BatchSpanProcessor. "
                                            + "[dropped=true if they were dropped due to high throughput]")
                            .build();
            droppedAttrs =
                    Attributes.of(
                            SPAN_PROCESSOR_TYPE_LABEL,
                            SPAN_PROCESSOR_TYPE_VALUE,
                            SPAN_PROCESSOR_DROPPED_LABEL,
                            true);
            exportedAttrs =
                    Attributes.of(
                            SPAN_PROCESSOR_TYPE_LABEL,
                            SPAN_PROCESSOR_TYPE_VALUE,
                            SPAN_PROCESSOR_DROPPED_LABEL,
                            false);

            this.batch = new ArrayList<>(this.maxExportBatchSize);
        }

        private volatile int droppedSpan = 0;

        synchronized void updateDroppedCounter() {
            droppedSpan++;
        }

        private volatile int currentSpan = 0;

        synchronized void updateSpanCaptured() {
            currentSpan++;
        }

        private void addSpan(ReadableSpan spanData) {
            //            Log.d("benchmark", "addSpan called spanData = " + spanData.getName());
            if (spanData.getName() != null
                    && Objects.equals(spanData.getName(), "benchmark_completed")) {
                Log.d("benchmark", "dropped spans = " + droppedSpan);
                Log.d("benchmark", "received spans = " + currentSpan);
                return;
            }
            updateSpanCaptured();
            if (!queue.offer(spanData)) {
                //                Log.d("benchmark", "xxx data dropped = " +
                // spanData.getName());
                processedSpansCounter.add(1, droppedAttrs);
                updateDroppedCounter();
            } else {
                if (queue.size() >= spansNeeded.get()) {
                    if (!signal.offer(true)) {
                        Log.d("benchmark", "xxx signal offer dropped = " + spanData.getName());
                    }
                }
            }
        }

        @Override
        public void run() {
            updateNextExportTime();

            while (continueWork) {
                if (flushRequested.get() != null) {
                    flush();
                }
                while (!queue.isEmpty() && batch.size() < maxExportBatchSize) {
                    batch.add(queue.poll().toSpanData());
                }
                if (batch.size() >= maxExportBatchSize || System.nanoTime() >= nextExportTime) {
                    exportCurrentBatch();
                    updateNextExportTime();
                }
                if (queue.isEmpty()) {
                    try {
                        long pollWaitTime = nextExportTime - System.nanoTime();
                        if (pollWaitTime > 0) {
                            spansNeeded.set(maxExportBatchSize - batch.size());
                            signal.poll(pollWaitTime, TimeUnit.NANOSECONDS);
                            spansNeeded.set(Integer.MAX_VALUE);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }

        private void flush() {
            int spansToFlush = queue.size();
            while (spansToFlush > 0) {
                ReadableSpan span = queue.poll();
                assert span != null;
                batch.add(span.toSpanData());
                spansToFlush--;
                if (batch.size() >= maxExportBatchSize) {
                    exportCurrentBatch();
                }
            }
            exportCurrentBatch();
            CompletableResultCode flushResult = flushRequested.get();
            if (flushResult != null) {
                flushResult.succeed();
                flushRequested.set(null);
            }
        }

        private void updateNextExportTime() {
            nextExportTime = System.nanoTime() + scheduleDelayNanos;
        }

        private CompletableResultCode shutdown() {
            CompletableResultCode result = new CompletableResultCode();

            CompletableResultCode flushResult = forceFlush();
            flushResult.whenComplete(
                    () -> {
                        continueWork = false;
                        CompletableResultCode shutdownResult = spanExporter.shutdown();
                        shutdownResult.whenComplete(
                                () -> {
                                    if (!flushResult.isSuccess() || !shutdownResult.isSuccess()) {
                                        result.fail();
                                    } else {
                                        result.succeed();
                                    }
                                });
                    });

            return result;
        }

        private CompletableResultCode forceFlush() {
            CompletableResultCode flushResult = new CompletableResultCode();
            // we set the atomic here to trigger the worker loop to do a flush of the entire queue.
            if (flushRequested.compareAndSet(null, flushResult)) {
                signal.offer(true);
            }
            CompletableResultCode possibleResult = flushRequested.get();
            // there's a race here where the flush happening in the worker loop could complete
            // before we
            // get what's in the atomic. In that case, just return success, since we know it
            // succeeded in
            // the interim.
            return possibleResult == null ? CompletableResultCode.ofSuccess() : possibleResult;
        }

        private void exportCurrentBatch() {
            if (batch.isEmpty()) {
                return;
            }

            try {
                CompletableResultCode result =
                        spanExporter.export(Collections.unmodifiableList(batch));
                result.join(exporterTimeoutNanos, TimeUnit.NANOSECONDS);
                if (result.isSuccess()) {
                    processedSpansCounter.add(batch.size(), exportedAttrs);
                } else {
                    logger.log(Level.FINE, "Exporter failed");
                }
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Exporter threw an Exception", e);
            } finally {
                batch.clear();
            }
        }
    }
}
