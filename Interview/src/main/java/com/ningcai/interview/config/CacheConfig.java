// config/CacheConfig.java
package com.ningcai.interview.config;

import com.ningcai.interview.model.dto.EvaluationDTO;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableAsync
@EnableScheduling
public class CacheConfig {
    
    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("questions", "evaluations");
    }
    
    @Bean
    public java.util.Map<String, String> firstQuestionCache() {
        return new java.util.concurrent.ConcurrentHashMap<>();
    }
    
    @Bean
    public java.util.Map<String, String> questionCache() {
        return new java.util.concurrent.ConcurrentHashMap<>();
    }
    
    @Bean
    public java.util.Map<String, EvaluationDTO> evaluationCache() {
        return new java.util.concurrent.ConcurrentHashMap<>();
    }
}