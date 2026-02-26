package com.temadison.stockdash.backend.pricing.alphavantage;

import java.time.Duration;
import java.time.LocalDate;

import org.springframework.stereotype.Component;

@Component
public class AlphaVantageRequestLimiter {

    private static final long MIN_REQUEST_SPACING_MS = 1800L;
    private static final String DAILY_LIMIT_HINT_A = "25 requests per day";
    private static final String DAILY_LIMIT_HINT_B = "daily rate limit";

    private long lastRequestEpochMs;
    private LocalDate dailyLimitReachedOn;

    public synchronized boolean isDailyLimitReached() {
        LocalDate today = LocalDate.now();
        if (today.equals(dailyLimitReachedOn)) {
            return true;
        }
        if (dailyLimitReachedOn != null && dailyLimitReachedOn.isBefore(today)) {
            dailyLimitReachedOn = null;
        }
        return false;
    }

    public synchronized void recordRateLimitMessage(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        String normalized = message.toLowerCase();
        if (normalized.contains(DAILY_LIMIT_HINT_A) || normalized.contains(DAILY_LIMIT_HINT_B)) {
            dailyLimitReachedOn = LocalDate.now();
        }
    }

    public synchronized void awaitTurn() throws InterruptedException {
        long now = System.currentTimeMillis();
        long elapsed = now - lastRequestEpochMs;
        if (elapsed < MIN_REQUEST_SPACING_MS) {
            Thread.sleep(MIN_REQUEST_SPACING_MS - elapsed);
        }
        lastRequestEpochMs = System.currentTimeMillis();
    }

    public synchronized void awaitRetryAfter(Duration retryAfter) throws InterruptedException {
        if (retryAfter == null || retryAfter.isNegative() || retryAfter.isZero()) {
            return;
        }
        long sleepMs = Math.min(retryAfter.toMillis(), 60_000L);
        Thread.sleep(sleepMs);
        lastRequestEpochMs = System.currentTimeMillis();
    }
}
