package com.temadison.stockdash.backend.pricing.alphavantage;

import com.temadison.stockdash.backend.pricing.PricingProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlphaVantageDailySeriesClientTest {

    @Test
    void retriesTransientFailureAndRecovers() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        AlphaVantageRequestLimiter requestLimiter = mock(AlphaVantageRequestLimiter.class);
        doReturn(false).when(requestLimiter).isDailyLimitReached();

        @SuppressWarnings("unchecked")
        HttpResponse<String> successResponse = (HttpResponse<String>) mock(HttpResponse.class);
        when(successResponse.statusCode()).thenReturn(200);
        when(successResponse.body()).thenReturn("""
                {
                  "Time Series (Daily)": {
                    "2026-01-02": {"4. close": "101.23"}
                  }
                }
                """);

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("transient network error"))
                .thenReturn(successResponse);

        AlphaVantageDailySeriesClient client = new AlphaVantageDailySeriesClient(
                new PricingProperties("test-key", "https://example.com/query", Duration.ofSeconds(1), Duration.ofSeconds(1)),
                requestLimiter,
                httpClient,
                retry(3),
                circuitBreaker(50.0f, 10, 5),
                timeLimiter(1)
        );

        SeriesFetchResult result = client.fetchDailyCloseSeries("AAPL");

        assertThat(result.status()).isEqualTo(SeriesFetchStatus.SUCCESS);
        assertThat(result.series()).containsEntry(LocalDate.of(2026, 1, 2), new java.math.BigDecimal("101.23"));
        verify(httpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void opensCircuitAfterFailureAndShortCircuitsNextCall() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        AlphaVantageRequestLimiter requestLimiter = mock(AlphaVantageRequestLimiter.class);
        doReturn(false).when(requestLimiter).isDailyLimitReached();

        @SuppressWarnings("unchecked")
        HttpResponse<String> failureResponse = (HttpResponse<String>) mock(HttpResponse.class);
        when(failureResponse.statusCode()).thenReturn(500);
        when(failureResponse.body()).thenReturn("{}");

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(failureResponse);

        AlphaVantageDailySeriesClient client = new AlphaVantageDailySeriesClient(
                new PricingProperties("test-key", "https://example.com/query", Duration.ofSeconds(1), Duration.ofSeconds(1)),
                requestLimiter,
                httpClient,
                retry(1),
                circuitBreaker(1.0f, 1, 1),
                timeLimiter(1)
        );

        SeriesFetchResult first = client.fetchDailyCloseSeries("AAPL");
        SeriesFetchResult second = client.fetchDailyCloseSeries("AAPL");

        assertThat(first.status()).isEqualTo(SeriesFetchStatus.API_ERROR);
        assertThat(second.status()).isEqualTo(SeriesFetchStatus.CIRCUIT_OPEN);
        verify(httpClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void returnsInvalidSymbolForInvalidApiCallPayload() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        AlphaVantageRequestLimiter requestLimiter = mock(AlphaVantageRequestLimiter.class);
        doReturn(false).when(requestLimiter).isDailyLimitReached();

        @SuppressWarnings("unchecked")
        HttpResponse<String> response = (HttpResponse<String>) mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {"Error Message":"Invalid API call. Please retry or visit the documentation."}
                """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        AlphaVantageDailySeriesClient client = new AlphaVantageDailySeriesClient(
                new PricingProperties("test-key", "https://example.com/query", Duration.ofSeconds(1), Duration.ofSeconds(1)),
                requestLimiter,
                httpClient,
                retry(1),
                circuitBreaker(50.0f, 10, 5),
                timeLimiter(1)
        );

        SeriesFetchResult result = client.fetchDailyCloseSeries("BAD");

        assertThat(result.status()).isEqualTo(SeriesFetchStatus.INVALID_SYMBOL);
        assertThat(result.series()).isEmpty();
    }

    @Test
    void returnsRateLimitedForNotePayload() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        AlphaVantageRequestLimiter requestLimiter = mock(AlphaVantageRequestLimiter.class);
        doReturn(false).when(requestLimiter).isDailyLimitReached();

        @SuppressWarnings("unchecked")
        HttpResponse<String> response = (HttpResponse<String>) mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {"Note":"Thank you for using Alpha Vantage! Our standard API rate limit is 25 requests per day."}
                """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        AlphaVantageDailySeriesClient client = new AlphaVantageDailySeriesClient(
                new PricingProperties("test-key", "https://example.com/query", Duration.ofSeconds(1), Duration.ofSeconds(1)),
                requestLimiter,
                httpClient,
                retry(1),
                circuitBreaker(50.0f, 10, 5),
                timeLimiter(1)
        );

        SeriesFetchResult result = client.fetchDailyCloseSeries("AAPL");

        assertThat(result.status()).isEqualTo(SeriesFetchStatus.RATE_LIMITED);
    }

    @Test
    void returnsApiErrorForMalformedJsonPayload() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        AlphaVantageRequestLimiter requestLimiter = mock(AlphaVantageRequestLimiter.class);
        doReturn(false).when(requestLimiter).isDailyLimitReached();

        @SuppressWarnings("unchecked")
        HttpResponse<String> response = (HttpResponse<String>) mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{not-json}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        AlphaVantageDailySeriesClient client = new AlphaVantageDailySeriesClient(
                new PricingProperties("test-key", "https://example.com/query", Duration.ofSeconds(1), Duration.ofSeconds(1)),
                requestLimiter,
                httpClient,
                retry(1),
                circuitBreaker(50.0f, 10, 5),
                timeLimiter(1)
        );

        SeriesFetchResult result = client.fetchDailyCloseSeries("AAPL");

        assertThat(result.status()).isEqualTo(SeriesFetchStatus.API_ERROR);
    }

    @Test
    void honorsRetryAfterOn429AndThenRecovers() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        AlphaVantageRequestLimiter requestLimiter = mock(AlphaVantageRequestLimiter.class);
        doReturn(false).when(requestLimiter).isDailyLimitReached();

        @SuppressWarnings("unchecked")
        HttpResponse<String> rateLimited = (HttpResponse<String>) mock(HttpResponse.class);
        when(rateLimited.statusCode()).thenReturn(429);
        when(rateLimited.headers()).thenReturn(HttpHeaders.of(Map.of("Retry-After", java.util.List.of("1")), (a, b) -> true));

        @SuppressWarnings("unchecked")
        HttpResponse<String> successResponse = (HttpResponse<String>) mock(HttpResponse.class);
        when(successResponse.statusCode()).thenReturn(200);
        when(successResponse.body()).thenReturn("""
                {
                  "Time Series (Daily)": {
                    "2026-01-03": {"4. close": "111.11"}
                  }
                }
                """);

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(rateLimited)
                .thenReturn(successResponse);

        AlphaVantageDailySeriesClient client = new AlphaVantageDailySeriesClient(
                new PricingProperties("test-key", "https://example.com/query", Duration.ofSeconds(1), Duration.ofSeconds(1)),
                requestLimiter,
                httpClient,
                retry(2),
                circuitBreaker(50.0f, 10, 5),
                timeLimiter(1)
        );

        SeriesFetchResult result = client.fetchDailyCloseSeries("AAPL");

        assertThat(result.status()).isEqualTo(SeriesFetchStatus.SUCCESS);
        assertThat(result.series()).containsEntry(LocalDate.of(2026, 1, 3), new java.math.BigDecimal("111.11"));
        verify(requestLimiter, times(1)).awaitRetryAfter(Duration.ofSeconds(1));
        verify(httpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    private Retry retry(int maxAttempts) {
        return Retry.of(
                "test-retry",
                RetryConfig.custom()
                        .maxAttempts(maxAttempts)
                        .waitDuration(Duration.ofMillis(1))
                        .retryExceptions(RuntimeException.class)
                        .build()
        );
    }

    private CircuitBreaker circuitBreaker(float failureRateThreshold, int slidingWindowSize, int minimumCalls) {
        return CircuitBreaker.of(
                "test-circuit-breaker",
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(failureRateThreshold)
                        .slidingWindowSize(slidingWindowSize)
                        .minimumNumberOfCalls(minimumCalls)
                        .waitDurationInOpenState(Duration.ofMinutes(1))
                        .build()
        );
    }

    private TimeLimiter timeLimiter(int timeoutSeconds) {
        return TimeLimiter.of(
                "test-time-limiter",
                TimeLimiterConfig.custom()
                        .timeoutDuration(Duration.ofSeconds(timeoutSeconds))
                        .build()
        );
    }
}
