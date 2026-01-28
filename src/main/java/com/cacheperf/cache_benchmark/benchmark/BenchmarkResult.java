package com.cacheperf.cache_benchmark.benchmark;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BenchmarkResult {
    private String providerName;
    private long totalOperations;
    private long successfulOperations;
    private long failedOperations;
    private double averageLatencyMs;
    private double p50LatencyMs;
    private double p95LatencyMs;
    private double p99LatencyMs;
    private double minLatencyMs;
    private double maxLatencyMs;
    private double throughputOpsPerSecond;
    private long totalDurationMs;
    private long cacheSize;
    private double speedupVsDatabase;
    private Map<String, Object> additionalMetrics;
}
