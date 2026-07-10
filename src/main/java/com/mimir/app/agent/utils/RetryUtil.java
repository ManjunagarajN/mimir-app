package com.mimir.app.agent.utils;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Generic retry-with-exponential-backoff helper. Replaces the bespoke retry
 * loop that used to live inside PurchaseIntentAgentService#downloadPdfWithRetry
 * so any step (login, search, create, pdf...) can reuse the same logic.
 */
@Slf4j
@Component
public class RetryUtil {

    @FunctionalInterface
    public interface RetryableOperation<T> {
        T execute() throws Exception;
    }

    /**
     * @param operationName label used in logs
     * @param maxAttempts   total attempts (including the first)
     * @param initialDelayMs delay before the first retry; doubles each subsequent retry
     * @param operation     the work to attempt
     * @param fallbackValue value to return (instead of throwing) if all attempts fail;
     *                      pass null to have the last exception re-thrown instead
     */
    public <T> T executeWithRetry(
            String operationName,
            int maxAttempts,
            long initialDelayMs,
            RetryableOperation<T> operation,
            T fallbackValue)
            throws Exception {
        int attempt = 0;
        long delay = initialDelayMs;
        Exception lastError = null;

        while (attempt < maxAttempts) {
            try {
                log.info("🔄 {} - attempt {}/{}", operationName, attempt + 1, maxAttempts);
                return operation.execute();
            } catch (Exception e) {
                lastError = e;
                attempt++;
                log.warn("⚠️ {} attempt {} failed: {}", operationName, attempt, e.getMessage());
                if (attempt < maxAttempts) {
                    Thread.sleep(delay);
                    delay *= 2;
                }
            }
        }

        log.warn("⚠️ All {} attempts failed for {}", maxAttempts, operationName);
        if (fallbackValue != null) {
            return fallbackValue;
        }
        throw lastError != null ? lastError : new RuntimeException(operationName + " failed with no attempts made");
    }
}
