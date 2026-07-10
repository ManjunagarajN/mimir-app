package com.mimir.app.agent.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Getter;

@Configuration
@Getter
public class AgentConfig {

    @Value("${purchase.api.base-url:http://localhost:8081}")
    private String baseUrl;

    @Value("${purchase.api.version:/api/v1/vortx}")
    private String apiVersion;

    @Value("${ollama.model:llama3.2}")
    private String ollamaModel;

    @Bean
    public HttpClient httpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
