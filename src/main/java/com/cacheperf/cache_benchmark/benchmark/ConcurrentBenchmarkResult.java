package com.cacheperf.cache_benchmark.benchmark;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConcurrentBenchmarkResult {
    private String providerName;
    private int concurrentUsers;
    private long totalOperations;
    private long successfulOperations;
    private long failedOperations;
    private double averageLatencyMs;
    private double p95LatencyMs;
    private double p99LatencyMs;
    private double throughputOpsPerSecond;
    private long totalDurationMs;
    private double errorRate;
}
