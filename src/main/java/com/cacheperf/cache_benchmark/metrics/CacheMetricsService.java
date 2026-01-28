package com.cacheperf.cache_benchmark.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class CacheMetricsService {

    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, Timer> cacheTimers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicReference<Double>> throughputValues = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicReference<Double>> latencyValues = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicReference<Double>> concurrentThroughputValues = new ConcurrentHashMap<>();

    /**
     * Record a cache operation with timing
     */
    public <T> T recordCacheOperation(String cacheProvider, String operation, Supplier<T> supplier) {
        Timer timer = getOrCreateTimer(cacheProvider, operation);
        return timer.record(supplier);
    }

    /**
     * Record cache operation latency manually
     */
    public void recordLatency(String cacheProvider, String operation, long durationMs) {
        Timer timer = getOrCreateTimer(cacheProvider, operation);
        timer.record(Duration.ofMillis(durationMs));
    }

    /**
     * Increment cache hit counter
     */
    public void incrementCacheHit(String cacheProvider) {
        meterRegistry.counter("cache.hits",
                "provider", cacheProvider,
                "result", "hit"
        ).increment();
    }

    /**
     * Increment cache miss counter
     */
    public void incrementCacheMiss(String cacheProvider) {
        meterRegistry.counter("cache.hits",
                "provider", cacheProvider,
                "result", "miss"
        ).increment();
    }

    /**
     * Record cache size
     */
    public void recordCacheSize(String cacheProvider, long size) {
        meterRegistry.gauge("cache.size",
                java.util.Collections.singletonList(io.micrometer.core.instrument.Tag.of("provider", cacheProvider)),
                size
        );
    }

    /**
     * Record cache throughput (operations per second)
     */
    public void recordThroughput(String cacheProvider, double throughputOpsPerSec) {
        AtomicReference<Double> value = throughputValues.computeIfAbsent(cacheProvider, k -> {
            AtomicReference<Double> ref = new AtomicReference<>(0.0);
            meterRegistry.gauge("cache.throughput",
                    java.util.Collections.singletonList(io.micrometer.core.instrument.Tag.of("provider", cacheProvider)),
                    ref,
                    AtomicReference::get
            );
            return ref;
        });
        value.set(throughputOpsPerSec);
    }

    /**
     * Record average latency in milliseconds
     */
    public void recordAverageLatency(String cacheProvider, double avgLatencyMs) {
        AtomicReference<Double> value = latencyValues.computeIfAbsent(cacheProvider, k -> {
            AtomicReference<Double> ref = new AtomicReference<>(0.0);
            meterRegistry.gauge("cache.avg.latency",
                    java.util.Collections.singletonList(io.micrometer.core.instrument.Tag.of("provider", cacheProvider)),
                    ref,
                    AtomicReference::get
            );
            return ref;
        });
        value.set(avgLatencyMs);
    }

    /**
     * Record concurrent throughput (operations per second under load)
     */
    public void recordConcurrentThroughput(String cacheProvider, double throughputOpsPerSec) {
        AtomicReference<Double> value = concurrentThroughputValues.computeIfAbsent(cacheProvider, k -> {
            AtomicReference<Double> ref = new AtomicReference<>(0.0);
            meterRegistry.gauge("cache.concurrent.throughput",
                    java.util.Collections.singletonList(io.micrometer.core.instrument.Tag.of("provider", cacheProvider)),
                    ref,
                    AtomicReference::get
            );
            return ref;
        });
        value.set(throughputOpsPerSec);
    }

    private Timer getOrCreateTimer(String cacheProvider, String operation) {
        String key = cacheProvider + ":" + operation;
        return cacheTimers.computeIfAbsent(key, k ->
                Timer.builder("cache.operations")
                        .tag("provider", cacheProvider)
                        .tag("operation", operation)
                        .description("Cache operation timing for " + cacheProvider)
                        .register(meterRegistry)
        );
    }
}
