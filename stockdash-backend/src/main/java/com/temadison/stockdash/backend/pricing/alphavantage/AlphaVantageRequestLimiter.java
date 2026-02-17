package com.temadison.stockdash.backend.pricing.alphavantage;

import org.springframework.stereotype.Component;

@Component
public class AlphaVantageRequestLimiter {

    private static final long MIN_REQUEST_SPACING_MS = 1800L;

    private long lastRequestEpochMs;

    public synchronized void awaitTurn() throws InterruptedException {
        long now = System.currentTimeMillis();
        long elapsed = now - lastRequestEpochMs;
        if (elapsed < MIN_REQUEST_SPACING_MS) {
            Thread.sleep(MIN_REQUEST_SPACING_MS - elapsed);
        }
        lastRequestEpochMs = System.currentTimeMillis();
    }
}
