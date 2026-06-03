package com.ewallet.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class AppEnhancementConfig {

    @Bean
    public CacheManager cacheManager(
            @Value("${app.cache.reference-data.ttl-minutes:60}") int ttlMinutes) {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                "transactionTypes", "roles", "userDetails");
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(ttlMinutes, TimeUnit.MINUTES)
                .maximumSize(500));
        return cacheManager;
    }

    @Bean
    public PageableHandlerMethodArgumentResolverCustomizer pageableCustomizer(
            @Value("${app.pagination.max-page-size:100}") int maxPageSize) {
        return resolver -> resolver.setMaxPageSize(maxPageSize);
    }
}
