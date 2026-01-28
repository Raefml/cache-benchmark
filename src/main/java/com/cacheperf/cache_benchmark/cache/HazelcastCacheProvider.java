package com.cacheperf.cache_benchmark.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class HazelcastCacheProvider implements CacheProvider {

    @Value("${cache.providers.hazelcast.cluster-name:cache-benchmark-cluster}")
    private String clusterName;

    private HazelcastInstance hazelcastInstance;
    private IMap<String, String> cache;
    private final ObjectMapper objectMapper;

    public HazelcastCacheProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        try {
            Config config = new Config();
            config.setClusterName(clusterName);
            config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
            config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(true).addMember("localhost");

            hazelcastInstance = Hazelcast.newHazelcastInstance(config);
            cache = hazelcastInstance.getMap("cache-benchmark");
            log.info("Hazelcast cache provider initialized: {}", clusterName);
        } catch (Exception e) {
            log.error("Failed to initialize Hazelcast cache provider", e);
        }
    }

    @PreDestroy
    public void destroy() {
        if (hazelcastInstance != null) {
            hazelcastInstance.shutdown();
        }
    }

    @Override
    public String getName() {
        return "Hazelcast";
    }

    @Override
    public <T> void put(String key, T value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            cache.put(key, json, 10, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("Error putting value in Hazelcast cache", e);
        }
    }

    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        try {
            String json = cache.get(key);
            if (json != null) {
                return Optional.of(objectMapper.readValue(json, type));
            }
        } catch (Exception e) {
            log.error("Error getting value from Hazelcast cache", e);
        }
        return Optional.empty();
    }

    @Override
    public void evict(String key) {
        try {
            cache.remove(key);
        } catch (Exception e) {
            log.error("Error evicting key from Hazelcast cache", e);
        }
    }

    @Override
    public void clear() {
        try {
            cache.clear();
        } catch (Exception e) {
            log.error("Error clearing Hazelcast cache", e);
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            return hazelcastInstance != null && hazelcastInstance.getLifecycleService().isRunning();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public long size() {
        try {
            return cache.size();
        } catch (Exception e) {
            log.error("Error getting Hazelcast cache size", e);
            return 0;
        }
    }

    @Override
    public void warmup(int count) {
        for (int i = 0; i < count; i++) {
            put("warmup:" + i, "value" + i);
        }
    }
}
