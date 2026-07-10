package com.mimir.app.agent.memory;

import java.time.LocalDateTime;
import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MemoryManager {

    @Autowired
    private Cache<String, Map<String, Double>> geocodeCache;

    @Autowired
    private Cache<String, Map<String, Object>> weatherCache;

    @Autowired
    private Cache<String, SessionMemory> sessionCache;

    @Autowired
    private Cache<String, QueryHistory> queryHistoryCache;

    @Autowired
    private Cache<String, Integer> frequentCitiesCache;

    private final ThreadLocal<Map<String, Object>> workingMemory = ThreadLocal.withInitial(HashMap::new);
    private final ThreadLocal<String> currentSessionId = new ThreadLocal<>();

    public void startSession(String sessionId) {
        currentSessionId.set(sessionId);
        SessionMemory session = sessionCache.get(sessionId, key -> new SessionMemory(sessionId));
        session.setLastAccess(LocalDateTime.now());

        QueryHistory history = queryHistoryCache.get(sessionId, key -> new QueryHistory(sessionId));
        history.setLastUpdate(LocalDateTime.now());

        log.info("🆕 Session started: {} (Total queries: {})", sessionId, session.getTotalQueries());
    }

    public void storeWorkingMemory(String key, Object value) {
        workingMemory.get().put(key, value);
        log.debug("📝 Working memory: {} = {}", key, value);
    }

    public Optional<Object> getWorkingMemory(String key) {
        return Optional.ofNullable(workingMemory.get().get(key));
    }

    public void storeSessionContext(String key, Object value) {
        String sessionId = currentSessionId.get();
        if (sessionId != null) {
            SessionMemory session = sessionCache.get(sessionId, id -> new SessionMemory(sessionId));
            session.getContext().put(key, value);
            session.setLastAccess(LocalDateTime.now());
            log.debug("📝 Session context: {} = {}", key, value);
        }
    }

    public Optional<Object> getSessionContext(String key) {
        String sessionId = currentSessionId.get();
        if (sessionId != null) {
            SessionMemory session = sessionCache.getIfPresent(sessionId);
            if (session != null) {
                return Optional.ofNullable(session.getContext().get(key));
            }
        }
        return Optional.empty();
    }

    public void storeQuery(String query) {
        String sessionId = currentSessionId.get();
        if (sessionId != null) {
            QueryHistory history = queryHistoryCache.get(sessionId, key -> new QueryHistory(sessionId));
            history.getQueries().add(query);
            history.setLastUpdate(LocalDateTime.now());

            SessionMemory session = sessionCache.get(sessionId, key -> new SessionMemory(sessionId));
            session.setTotalQueries(session.getTotalQueries() + 1);
            session.setLastAccess(LocalDateTime.now());

            log.debug("📝 Query stored: {} (Total: {})", query, session.getTotalQueries());
        }
    }

    public void storeAction(String action) {
        String sessionId = currentSessionId.get();
        if (sessionId != null) {
            QueryHistory history = queryHistoryCache.get(sessionId, key -> new QueryHistory(sessionId));
            history.getActions().add(action);
            history.setLastUpdate(LocalDateTime.now());
        }
    }

    public void storeWeatherResult(String city, Map<String, Object> weather) {
        weatherCache.put(city.toLowerCase(), weather);
        storeSessionContext("last_city", city);
        storeSessionContext("last_weather", weather);
        storeSessionContext("last_weather_time", LocalDateTime.now());

        frequentCitiesCache.asMap().merge(city.toLowerCase(), 1, Integer::sum);

        log.info("🌤️ Cached weather for {} (freq: {})", city, frequentCitiesCache.getIfPresent(city.toLowerCase()));
    }

    public Optional<Map<String, Object>> getCachedWeather(String city) {
        if (city == null) return Optional.empty();
        Map<String, Object> weather = weatherCache.getIfPresent(city.toLowerCase());
        if (weather != null) {
            log.debug("🌤️ Cache hit for weather: {}", city);
            return Optional.of(weather);
        }
        log.debug("🌤️ Cache miss for weather: {}", city);
        return Optional.empty();
    }

    public void storeGeocode(String city, double lat, double lon) {
        Map<String, Double> coords = new HashMap<>();
        coords.put("latitude", lat);
        coords.put("longitude", lon);
        geocodeCache.put(city.toLowerCase(), coords);
        log.debug("🗺️ Cached geocode: {} -> ({}, {})", city, lat, lon);
    }

    public Optional<Map<String, Double>> getCachedGeocode(String city) {
        if (city == null) return Optional.empty();
        Map<String, Double> coords = geocodeCache.getIfPresent(city.toLowerCase());
        if (coords != null) {
            log.debug("🗺️ Cache hit for geocode: {}", city);
            return Optional.of(coords);
        }
        log.debug("🗺️ Cache miss for geocode: {}", city);
        return Optional.empty();
    }

    public Optional<String> getLastCity() {
        return getSessionContext("last_city").map(Object::toString);
    }

    @SuppressWarnings("unchecked")
    public Optional<Map<String, Object>> getLastWeather() {
        return getSessionContext("last_weather").filter(Map.class::isInstance).map(obj -> (Map<String, Object>) obj);
    }

    public List<Map.Entry<String, Integer>> getFrequentCities(int topN) {
        return frequentCitiesCache.asMap().entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(topN)
                .toList();
    }

    public List<String> getRecentQueries(int count) {
        String sessionId = currentSessionId.get();
        if (sessionId == null) return Collections.emptyList();

        QueryHistory history = queryHistoryCache.getIfPresent(sessionId);
        if (history == null) return Collections.emptyList();

        List<String> queries = history.getQueries();
        int size = queries.size();
        int start = Math.max(0, size - count);
        return queries.subList(start, size);
    }

    public String getConversationSummary() {
        StringBuilder summary = new StringBuilder();
        List<String> recent = getRecentQueries(3);
        if (!recent.isEmpty()) {
            summary.append("Recent queries: ").append(String.join(", ", recent)).append("\n");
        }
        getLastCity()
                .ifPresent(city -> summary.append("Last city: ").append(city).append("\n"));
        List<Map.Entry<String, Integer>> topCities = getFrequentCities(3);
        if (!topCities.isEmpty()) {
            summary.append("Frequent cities: ");
            summary.append(topCities.stream()
                    .map(e -> e.getKey() + " (" + e.getValue() + "x)")
                    .toList());
        }
        return summary.toString();
    }

    public boolean hasAskedAboutCity(String city) {
        return frequentCitiesCache.getIfPresent(city.toLowerCase()) != null;
    }

    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("geocode", geocodeCache.stats());
        stats.put("weather", weatherCache.stats());
        stats.put("session", sessionCache.stats());
        stats.put("queryHistory", queryHistoryCache.stats());
        return stats;
    }

    public void clearSession() {
        String sessionId = currentSessionId.get();
        if (sessionId != null) {
            sessionCache.invalidate(sessionId);
            queryHistoryCache.invalidate(sessionId);
            workingMemory.remove();
            log.info("🧹 Session cleared: {}", sessionId);
        }
    }

    public void clearAll() {
        geocodeCache.invalidateAll();
        weatherCache.invalidateAll();
        sessionCache.invalidateAll();
        queryHistoryCache.invalidateAll();
        frequentCitiesCache.invalidateAll();
        workingMemory.remove();
        currentSessionId.remove();
        log.info("🧹 All caches cleared");
    }

    public void cleanup() {
        workingMemory.remove();
        currentSessionId.remove();
    }
}
