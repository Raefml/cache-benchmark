package com.cacheperf.cache_benchmark.controller;

import com.cacheperf.cache_benchmark.benchmark.BenchmarkResult;
import com.cacheperf.cache_benchmark.benchmark.BenchmarkService;
import com.cacheperf.cache_benchmark.benchmark.ConcurrentBenchmarkResult;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/benchmark")
@RequiredArgsConstructor
public class BenchmarkController {

    private final BenchmarkService benchmarkService;

    @PostMapping("/sequential")
    @Timed(value = "benchmark.sequential", description = "Time taken to run sequential benchmark")
    public ResponseEntity<List<BenchmarkResult>> runSequentialBenchmark() {
        log.info("Received request to run sequential benchmark");
        List<BenchmarkResult> results = benchmarkService.runSequentialBenchmark();
        return ResponseEntity.ok(results);
    }

    @PostMapping("/concurrent")
    @Timed(value = "benchmark.concurrent", description = "Time taken to run concurrent benchmark")
    public ResponseEntity<List<ConcurrentBenchmarkResult>> runConcurrentBenchmark() {
        log.info("Received request to run concurrent benchmark");
        List<ConcurrentBenchmarkResult> results = benchmarkService.runConcurrentBenchmark();
        return ResponseEntity.ok(results);
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getProviderStatus() {
        Map<String, Object> status = benchmarkService.getProviderStatus();
        return ResponseEntity.ok(status);
    }
}
