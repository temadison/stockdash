package com.temadison.stockdash.backend.pricing.alphavantage;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AlphaVantageRequestLimiterTest {

    @Test
    void marksDailyLimitReachedWhenMessageContainsDailyLimitHints() {
        AlphaVantageRequestLimiter limiter = new AlphaVantageRequestLimiter();

        limiter.recordRateLimitMessage("Standard API rate limit is 25 requests per day.");

        assertThat(limiter.isDailyLimitReached()).isTrue();
    }

    @Test
    void ignoresBlankOrUnrelatedMessages() {
        AlphaVantageRequestLimiter limiter = new AlphaVantageRequestLimiter();

        limiter.recordRateLimitMessage("   ");
        limiter.recordRateLimitMessage("temporary upstream timeout");

        assertThat(limiter.isDailyLimitReached()).isFalse();
    }

    @Test
    void clearsStaleDailyLimitMarkerOnNextDay() throws Exception {
        AlphaVantageRequestLimiter limiter = new AlphaVantageRequestLimiter();
        setDateField(limiter, "dailyLimitReachedOn", LocalDate.now().minusDays(1));

        boolean limited = limiter.isDailyLimitReached();

        assertThat(limited).isFalse();
        assertThat(getDateField(limiter, "dailyLimitReachedOn")).isNull();
    }

    @Test
    void enforcesMinimumSpacingBetweenRequests() throws Exception {
        AlphaVantageRequestLimiter limiter = new AlphaVantageRequestLimiter();
        setLongField(limiter, "lastRequestEpochMs", System.currentTimeMillis() - 1790L);

        long started = System.currentTimeMillis();
        limiter.awaitTurn();
        long elapsedMs = System.currentTimeMillis() - started;

        assertThat(elapsedMs).isGreaterThanOrEqualTo(8L);
    }

    @Test
    void awaitsRetryAfterWindow() throws Exception {
        AlphaVantageRequestLimiter limiter = new AlphaVantageRequestLimiter();

        long started = System.currentTimeMillis();
        limiter.awaitRetryAfter(Duration.ofMillis(20));
        long elapsedMs = System.currentTimeMillis() - started;

        assertThat(elapsedMs).isGreaterThanOrEqualTo(15L);
    }

    private static void setDateField(Object target, String fieldName, LocalDate value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static LocalDate getDateField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return (LocalDate) field.get(target);
    }

    private static void setLongField(Object target, String fieldName, long value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setLong(target, value);
    }
}
