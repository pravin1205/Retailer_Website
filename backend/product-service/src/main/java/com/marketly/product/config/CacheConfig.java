package com.marketly.product.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

/**
 * Redis cache configuration for product-service.
 *
 * Cache name        TTL        Use
 * ─────────────     ───────    ──────────────────────────────────────
 * product-detail    10 min     Single product fetch by ID
 * product-list      5 min      Paginated product listings (per tenant)
 * category-tree     60 min     Full category tree (rarely changes)
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
            .prefixCacheNameWith("marketly:")
            .serializeKeysWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new GenericJackson2JsonRedisSerializer()))
            .disableCachingNullValues();

        return RedisCacheManager.builder(factory)
            .cacheDefaults(base.entryTtl(Duration.ofMinutes(10)))
            .withInitialCacheConfigurations(Map.of(
                "product-detail", base.entryTtl(Duration.ofMinutes(10)),
                "product-list",   base.entryTtl(Duration.ofMinutes(5)),
                "category-tree",  base.entryTtl(Duration.ofMinutes(60))
            ))
            .build();
    }
}
