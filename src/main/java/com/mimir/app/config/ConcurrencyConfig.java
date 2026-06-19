package com.mimir.app.config;

import java.util.concurrent.Semaphore;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConcurrencyConfig {
    @Bean
    public Semaphore llmConcurrencyLimiter() {
        return new Semaphore(5, true);
    }

    @Bean
    public Semaphore embeddingConcurrencyLimiter() {
        return new Semaphore(20, true);
    }
}
