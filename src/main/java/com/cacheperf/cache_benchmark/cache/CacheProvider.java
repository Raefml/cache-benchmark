package com.cacheperf.cache_benchmark.cache;

import java.util.Optional;

public interface CacheProvider {

    String getName();

    <T> void put(String key, T value);

    <T> Optional<T> get(String key, Class<T> type);

    void evict(String key);

    void clear();

    boolean isAvailable();

    long size();

    void warmup(int count);
}
