package com.cacheperf.cache_benchmark.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
public class ValkeyCacheProvider implements CacheProvider {

    @Value("${cache.providers.valkey.host:localhost}")
    private String host;

    @Value("${cache.providers.valkey.port:6380}")
    private int port;

    private RedisClient redisClient;
    private StatefulRedisConnection<String, String> connection;
    private RedisCommands<String, String> commands;
    private final ObjectMapper objectMapper;

    public ValkeyCacheProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        try {
            String uri = String.format("redis://%s:%d", host, port);
            redisClient = RedisClient.create(uri);
            connection = redisClient.connect();
            commands = connection.sync();
            log.info("Valkey cache provider initialized: {}:{}", host, port);
        } catch (Exception e) {
            log.error("Failed to initialize Valkey cache provider", e);
        }
    }

    @PreDestroy
    public void destroy() {
        if (connection != null) {
            connection.close();
        }
        if (redisClient != null) {
            redisClient.shutdown();
        }
    }

    @Override
    public String getName() {
        return "Valkey";
    }

    @Override
    public <T> void put(String key, T value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            commands.set(key, json);
            commands.expire(key, 600);
        } catch (Exception e) {
            log.error("Error putting value in Valkey cache", e);
        }
    }

    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        try {
            String json = commands.get(key);
            if (json != null) {
                return Optional.of(objectMapper.readValue(json, type));
            }
        } catch (Exception e) {
            log.error("Error getting value from Valkey cache", e);
        }
        return Optional.empty();
    }

    @Override
    public void evict(String key) {
        try {
            commands.del(key);
        } catch (Exception e) {
            log.error("Error evicting key from Valkey cache", e);
        }
    }

    @Override
    public void clear() {
        try {
            commands.flushdb();
        } catch (Exception e) {
            log.error("Error clearing Valkey cache", e);
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            return connection != null && connection.isOpen() && "PONG".equals(commands.ping());
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public long size() {
        try {
            return commands.dbsize();
        } catch (Exception e) {
            log.error("Error getting Valkey cache size", e);
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
