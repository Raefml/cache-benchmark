package com.cacheperf.cache_benchmark.benchmark;

import com.cacheperf.cache_benchmark.cache.*;
import com.cacheperf.cache_benchmark.metrics.CacheMetricsService;
import com.cacheperf.cache_benchmark.model.dto.ProductDTO;
import com.cacheperf.cache_benchmark.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BenchmarkService {

    private final RedisCacheProvider redisCacheProvider;
    private final ValkeyCacheProvider valkeyCacheProvider;
    private final DragonflyCacheProvider dragonflyCacheProvider;
    private final KeyDBCacheProvider keydbCacheProvider;
    private final MemcachedCacheProvider memcachedCacheProvider;
    private final HazelcastCacheProvider hazelcastCacheProvider;
    private final ProductRepository productRepository;
    private final CacheMetricsService cacheMetricsService;

    @Value("${benchmark.warmup-iterations:1000}")
    private int warmupIterations;

    @Value("${benchmark.test-iterations:10000}")
    private int testIterations;

    @Value("${benchmark.concurrent-users:50}")
    private int concurrentUsers;

    private List<CacheProvider> getAllProviders() {
        return Arrays.asList(
                redisCacheProvider,
                valkeyCacheProvider,
                dragonflyCacheProvider,
                keydbCacheProvider,
                memcachedCacheProvider,
                hazelcastCacheProvider
        );
    }

    public List<BenchmarkResult> runSequentialBenchmark() {
        log.info("Starting sequential benchmark with {} iterations", testIterations);
        List<BenchmarkResult> results = new ArrayList<>();

        // Benchmark database first
        BenchmarkResult dbResult = benchmarkDatabase();
        results.add(dbResult);
        double dbLatency = dbResult.getAverageLatencyMs();

        // Benchmark each cache provider
        for (CacheProvider provider : getAllProviders()) {
            if (provider.isAvailable()) {
                log.info("Benchmarking {}", provider.getName());
                BenchmarkResult result = benchmarkProvider(provider);
                result.setSpeedupVsDatabase(dbLatency / result.getAverageLatencyMs());
                results.add(result);
            } else {
                log.warn("Provider {} is not available, skipping", provider.getName());
            }
        }

        return results;
    }

    public List<ConcurrentBenchmarkResult> runConcurrentBenchmark() {
        log.info("Starting concurrent benchmark with {} users", concurrentUsers);
        List<ConcurrentBenchmarkResult> results = new ArrayList<>();

        for (CacheProvider provider : getAllProviders()) {
            if (provider.isAvailable()) {
                log.info("Benchmarking {} with {} concurrent users", provider.getName(), concurrentUsers);
                ConcurrentBenchmarkResult result = benchmarkProviderConcurrent(provider);
                results.add(result);
            }
        }

        return results;
    }

    private BenchmarkResult benchmarkDatabase() {
        log.info("Warming up database...");
        List<Long> latencies = new ArrayList<>();

        long startTime = System.currentTimeMillis();
        int successful = 0;
        int failed = 0;

        for (int i = 0; i < testIterations; i++) {
            long id = (i % 1000) + 1;
            long opStart = System.nanoTime();
            try {
                productRepository.findById(id);
                latencies.add(System.nanoTime() - opStart);
                successful++;
            } catch (Exception e) {
                failed++;
            }
        }

        long totalDuration = System.currentTimeMillis() - startTime;

        return buildBenchmarkResult("Database", latencies, successful, failed, totalDuration, 0);
    }

    private BenchmarkResult benchmarkProvider(CacheProvider provider) {
        // Warmup
        log.info("Warming up {} with {} iterations", provider.getName(), warmupIterations);
        provider.clear();
        provider.warmup(warmupIterations);

        // Prepare test data
        List<ProductDTO> testProducts = prepareTestProducts(testIterations);

        // Clear and populate cache
        provider.clear();
        for (int i = 0; i < Math.min(1000, testProducts.size()); i++) {
            provider.put("product:" + testProducts.get(i).getId(), testProducts.get(i));
        }

        // Run benchmark
        List<Long> latencies = new ArrayList<>();
        long startTime = System.currentTimeMillis();
        int successful = 0;
        int failed = 0;

        for (int i = 0; i < testIterations; i++) {
            ProductDTO product = testProducts.get(i % testProducts.size());
            String key = "product:" + product.getId();

            long opStart = System.nanoTime();
            try {
                // Alternate between reads and writes (80% read, 20% write)
                if (i % 5 == 0) {
                    provider.put(key, product);
                    long duration = (System.nanoTime() - opStart) / 1_000_000; // Convert to ms
                    cacheMetricsService.recordLatency(provider.getName(), "put", duration);
                } else {
                    provider.get(key, ProductDTO.class);
                    long duration = (System.nanoTime() - opStart) / 1_000_000; // Convert to ms
                    cacheMetricsService.recordLatency(provider.getName(), "get", duration);
                }
                latencies.add(System.nanoTime() - opStart);
                successful++;
            } catch (Exception e) {
                failed++;
                log.error("Error during benchmark operation", e);
            }
        }

        long totalDuration = System.currentTimeMillis() - startTime;
        long cacheSize = provider.size();

        return buildBenchmarkResult(provider.getName(), latencies, successful, failed, totalDuration, cacheSize);
    }

    private ConcurrentBenchmarkResult benchmarkProviderConcurrent(CacheProvider provider) {
        provider.clear();
        provider.warmup(1000);

        List<ProductDTO> testProducts = prepareTestProducts(1000);
        ExecutorService executor = Executors.newFixedThreadPool(concurrentUsers);
        CountDownLatch latch = new CountDownLatch(concurrentUsers);

        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successful = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);

        int operationsPerUser = testIterations / concurrentUsers;

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < concurrentUsers; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerUser; j++) {
                        ProductDTO product = testProducts.get(j % testProducts.size());
                        String key = "product:" + product.getId();

                        long opStart = System.nanoTime();
                        try {
                            if (j % 5 == 0) {
                                provider.put(key, product);
                            } else {
                                provider.get(key, ProductDTO.class);
                            }
                            latencies.add(System.nanoTime() - opStart);
                            successful.incrementAndGet();
                        } catch (Exception e) {
                            failed.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        executor.shutdown();
        long totalDuration = System.currentTimeMillis() - startTime;

        return buildConcurrentBenchmarkResult(provider.getName(), latencies, successful.get(),
                                              failed.get(), totalDuration);
    }

    private BenchmarkResult buildBenchmarkResult(String name, List<Long> latencies, int successful,
                                                  int failed, long totalDuration, long cacheSize) {
        if (latencies.isEmpty()) {
            return BenchmarkResult.builder()
                    .providerName(name)
                    .totalOperations(successful + failed)
                    .successfulOperations(successful)
                    .failedOperations(failed)
                    .build();
        }

        List<Long> sortedLatencies = new ArrayList<>(latencies);
        Collections.sort(sortedLatencies);

        double avgLatency = latencies.stream().mapToLong(Long::longValue).average().orElse(0) / 1_000_000.0;
        double p50 = sortedLatencies.get((int) (sortedLatencies.size() * 0.50)) / 1_000_000.0;
        double p95 = sortedLatencies.get((int) (sortedLatencies.size() * 0.95)) / 1_000_000.0;
        double p99 = sortedLatencies.get((int) (sortedLatencies.size() * 0.99)) / 1_000_000.0;
        double min = sortedLatencies.get(0) / 1_000_000.0;
        double max = sortedLatencies.get(sortedLatencies.size() - 1) / 1_000_000.0;
        double throughput = (successful * 1000.0) / totalDuration;

        // Record metrics for Grafana
        cacheMetricsService.recordThroughput(name, throughput);
        cacheMetricsService.recordAverageLatency(name, avgLatency);

        return BenchmarkResult.builder()
                .providerName(name)
                .totalOperations(successful + failed)
                .successfulOperations(successful)
                .failedOperations(failed)
                .averageLatencyMs(avgLatency)
                .p50LatencyMs(p50)
                .p95LatencyMs(p95)
                .p99LatencyMs(p99)
                .minLatencyMs(min)
                .maxLatencyMs(max)
                .throughputOpsPerSecond(throughput)
                .totalDurationMs(totalDuration)
                .cacheSize(cacheSize)
                .build();
    }

    private ConcurrentBenchmarkResult buildConcurrentBenchmarkResult(String name, List<Long> latencies,
                                                                      int successful, int failed,
                                                                      long totalDuration) {
        if (latencies.isEmpty()) {
            return ConcurrentBenchmarkResult.builder()
                    .providerName(name)
                    .concurrentUsers(concurrentUsers)
                    .totalOperations(successful + failed)
                    .successfulOperations(successful)
                    .failedOperations(failed)
                    .build();
        }

        List<Long> sortedLatencies = new ArrayList<>(latencies);
        Collections.sort(sortedLatencies);

        double avgLatency = latencies.stream().mapToLong(Long::longValue).average().orElse(0) / 1_000_000.0;
        double p95 = sortedLatencies.get((int) (sortedLatencies.size() * 0.95)) / 1_000_000.0;
        double p99 = sortedLatencies.get((int) (sortedLatencies.size() * 0.99)) / 1_000_000.0;
        double throughput = (successful * 1000.0) / totalDuration;
        double errorRate = (failed * 100.0) / (successful + failed);

        // Record metrics for Grafana (concurrent metrics)
        cacheMetricsService.recordConcurrentThroughput(name, throughput);

        return ConcurrentBenchmarkResult.builder()
                .providerName(name)
                .concurrentUsers(concurrentUsers)
                .totalOperations(successful + failed)
                .successfulOperations(successful)
                .failedOperations(failed)
                .averageLatencyMs(avgLatency)
                .p95LatencyMs(p95)
                .p99LatencyMs(p99)
                .throughputOpsPerSecond(throughput)
                .totalDurationMs(totalDuration)
                .errorRate(errorRate)
                .build();
    }

    private List<ProductDTO> prepareTestProducts(int count) {
        List<ProductDTO> products = new ArrayList<>();
        for (int i = 1; i <= Math.min(count, 1000); i++) {
            products.add(ProductDTO.builder()
                    .id((long) i)
                    .sku("SKU-" + i)
                    .name("Product " + i)
                    .description("Description " + i)
                    .price(BigDecimal.valueOf(10.0 + i))
                    .stock(100 + i)
                    .category("Category " + (i % 10))
                    .brand("Brand " + (i % 5))
                    .active(true)
                    .build());
        }
        return products;
    }

    public Map<String, Object> getProviderStatus() {
        Map<String, Object> status = new HashMap<>();
        for (CacheProvider provider : getAllProviders()) {
            Map<String, Object> providerInfo = new HashMap<>();
            providerInfo.put("available", provider.isAvailable());
            if (provider.isAvailable()) {
                providerInfo.put("size", provider.size());
            }
            status.put(provider.getName(), providerInfo);
        }
        return status;
    }
}
