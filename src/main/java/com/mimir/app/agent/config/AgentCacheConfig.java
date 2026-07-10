package com.mimir.app.agent.config;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mimir.app.agent.domain.PurchaseIntentAgentState;
import com.mimir.app.agent.memory.QueryHistory;
import com.mimir.app.agent.memory.SessionMemory;

@Configuration
public class AgentCacheConfig {

    @Bean
    public Cache<String, Map<String, Double>> geocodeCache() {
        return Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(24, TimeUnit.HOURS)
                .recordStats()
                .build();
    }

    @Bean
    public Cache<String, Map<String, Object>> weatherCache() {
        return Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }

    @Bean
    public Cache<String, SessionMemory> sessionCache() {
        return Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterAccess(1, TimeUnit.HOURS)
                .recordStats()
                .build();
    }

    @Bean
    public Cache<String, QueryHistory> queryHistoryCache() {
        return Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(2, TimeUnit.HOURS)
                .recordStats()
                .build();
    }

    @Bean
    public Cache<String, Integer> frequentCitiesCache() {
        return Caffeine.newBuilder().maximumSize(200).recordStats().build();
    }

    /**
     * NEW: stores the last (in-progress or failed) PurchaseIntentAgentState per
     * workflow key (typically the requester's email). This is what makes the
     * agent resumable: on failure the state is written here; on the next call
     * for the same key, the service picks it back up instead of starting over.
     */
    @Bean
    public Cache<String, PurchaseIntentAgentState> workflowStateCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(30, TimeUnit.MINUTES) // States expire after 30 minutes
                .maximumSize(100) // Max 100 concurrent workflows
                .build();
    }
}
