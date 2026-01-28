package com.cacheperf.cache_benchmark.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import net.spy.memcached.MemcachedClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.Optional;

@Slf4j
@Component
public class MemcachedCacheProvider implements CacheProvider {

    @Value("${cache.providers.memcached.servers:localhost:11211}")
    private String servers;

    private MemcachedClient memcachedClient;
    private final ObjectMapper objectMapper;

    public MemcachedCacheProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        try {
            String[] parts = servers.split(":");
            String host = parts[0];
            int port = Integer.parseInt(parts[1]);
            memcachedClient = new MemcachedClient(new InetSocketAddress(host, port));
            log.info("Memcached cache provider initialized: {}", servers);
        } catch (Exception e) {
            log.error("Failed to initialize Memcached cache provider", e);
        }
    }

    @PreDestroy
    public void destroy() {
        if (memcachedClient != null) {
            memcachedClient.shutdown();
        }
    }

    @Override
    public String getName() {
        return "Memcached";
    }

    @Override
    public <T> void put(String key, T value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            memcachedClient.set(key, 600, json); // 600 seconds TTL
        } catch (Exception e) {
            log.error("Error putting value in Memcached cache", e);
        }
    }

    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        try {
            Object obj = memcachedClient.get(key);
            if (obj != null) {
                String json = obj.toString();
                return Optional.of(objectMapper.readValue(json, type));
            }
        } catch (Exception e) {
            log.error("Error getting value from Memcached cache", e);
        }
        return Optional.empty();
    }

    @Override
    public void evict(String key) {
        try {
            memcachedClient.delete(key);
        } catch (Exception e) {
            log.error("Error evicting key from Memcached cache", e);
        }
    }

    @Override
    public void clear() {
        try {
            memcachedClient.flush();
        } catch (Exception e) {
            log.error("Error clearing Memcached cache", e);
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            return memcachedClient != null && memcachedClient.getStats() != null;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public long size() {
        try {
            var stats = memcachedClient.getStats();
            if (!stats.isEmpty()) {
                var firstServer = stats.values().iterator().next();
                String currItems = firstServer.get("curr_items");
                return currItems != null ? Long.parseLong(currItems) : 0;
            }
        } catch (Exception e) {
            log.error("Error getting Memcached cache size", e);
        }
        return 0;
    }

    @Override
    public void warmup(int count) {
        for (int i = 0; i < count; i++) {
            put("warmup:" + i, "value" + i);
        }
    }
}
