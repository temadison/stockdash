package com.temadison.stockdash.backend.pricing.alphavantage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.temadison.stockdash.backend.pricing.PricingProperties;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.Callable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Component
public class AlphaVantageDailySeriesClient implements DailySeriesClient {

    private static final Logger log = LoggerFactory.getLogger(AlphaVantageDailySeriesClient.class);
    private static final String RESILIENCE_INSTANCE_NAME = "alphaVantageDailySeries";
    private static final String TIME_SERIES_KEY = "Time Series (Daily)";
    private static final String DAILY_CLOSE_KEY = "4. close";

    private final PricingProperties pricingProperties;
    private final HttpClient httpClient;
    private final AlphaVantageRequestLimiter requestLimiter;
    private final ObjectMapper objectMapper;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;
    private final TimeLimiter timeLimiter;

    @Autowired
    public AlphaVantageDailySeriesClient(
            PricingProperties pricingProperties,
            AlphaVantageRequestLimiter requestLimiter,
            HttpClient alphaVantageHttpClient,
            RetryRegistry retryRegistry,
            CircuitBreakerRegistry circuitBreakerRegistry,
            TimeLimiterRegistry timeLimiterRegistry
    ) {
        this(
                pricingProperties,
                requestLimiter,
                alphaVantageHttpClient,
                retryRegistry.retry(RESILIENCE_INSTANCE_NAME),
                circuitBreakerRegistry.circuitBreaker(RESILIENCE_INSTANCE_NAME),
                timeLimiterRegistry.timeLimiter(RESILIENCE_INSTANCE_NAME)
        );
    }

    AlphaVantageDailySeriesClient(
            PricingProperties pricingProperties,
            AlphaVantageRequestLimiter requestLimiter,
            HttpClient httpClient,
            Retry retry,
            CircuitBreaker circuitBreaker,
            TimeLimiter timeLimiter
    ) {
        this.pricingProperties = pricingProperties;
        this.requestLimiter = requestLimiter;
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
        this.retry = retry;
        this.circuitBreaker = circuitBreaker;
        this.timeLimiter = timeLimiter;
    }

    @Override
    public SeriesFetchResult fetchDailyCloseSeries(String symbol) {
        if (!StringUtils.hasText(pricingProperties.alphaVantageApiKey())) {
            return new SeriesFetchResult(Map.of(), SeriesFetchStatus.NO_DATA);
        }
        if (requestLimiter.isDailyLimitReached()) {
            return new SeriesFetchResult(Map.of(), SeriesFetchStatus.RATE_LIMITED);
        }

        String baseUrl = StringUtils.hasText(pricingProperties.alphaVantageBaseUrl())
                ? pricingProperties.alphaVantageBaseUrl()
                : "https://www.alphavantage.co/query";
        String encodedSymbol = URLEncoder.encode(symbol, StandardCharsets.UTF_8);
        String encodedApiKey = URLEncoder.encode(pricingProperties.alphaVantageApiKey(), StandardCharsets.UTF_8);
        String requestUrl = baseUrl
                + "?function=TIME_SERIES_DAILY"
                + "&symbol=" + encodedSymbol
                + "&outputsize=compact"
                + "&apikey=" + encodedApiKey;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(requestUrl))
                .timeout(pricingProperties.requestTimeoutOrDefault())
                .GET()
                .build();

        try {
            Callable<SeriesFetchResult> resilientCall = TimeLimiter.decorateFutureSupplier(
                    timeLimiter,
                    () -> CompletableFuture.supplyAsync(() ->
                            CircuitBreaker.decorateSupplier(
                                    circuitBreaker,
                                    Retry.decorateSupplier(retry, () -> fetchSeries(request, symbol))
                            ).get()
                    )
            );
            return resilientCall.call();
        } catch (CallNotPermittedException ex) {
            log.warn("Circuit breaker open for Alpha Vantage daily series call. symbol={}", symbol);
            return new SeriesFetchResult(Map.of(), SeriesFetchStatus.CIRCUIT_OPEN);
        } catch (Exception ex) {
            Throwable root = unwrap(ex);
            if (root instanceof RetryableSeriesFetchException retryable) {
                log.warn("Daily series call failed after retries. symbol={} status={}", symbol, retryable.status(), retryable);
                return new SeriesFetchResult(Map.of(), retryable.status());
            }
            if (root instanceof TimeoutException) {
                log.warn("Daily series call timed out. symbol={}", symbol, root);
                return new SeriesFetchResult(Map.of(), SeriesFetchStatus.API_ERROR);
            }
            if (root instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                log.warn("Daily series call interrupted. symbol={}", symbol, root);
                return new SeriesFetchResult(Map.of(), SeriesFetchStatus.API_ERROR);
            }
            log.warn("Daily series call failed unexpectedly. symbol={}", symbol, root);
            return new SeriesFetchResult(Map.of(), SeriesFetchStatus.API_ERROR);
        }
    }

    private SeriesFetchResult fetchSeries(HttpRequest request, String symbol) {
        try {
            requestLimiter.awaitTurn();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) {
                Duration retryAfter = retryAfter(response);
                if (retryAfter != null) {
                    requestLimiter.awaitRetryAfter(retryAfter);
                }
                throw new RetryableSeriesFetchException(
                        SeriesFetchStatus.RATE_LIMITED,
                        "Price series API rate limited HTTP 429 for symbol " + symbol + "."
                );
            }
            if (response.statusCode() >= 500) {
                throw new RetryableSeriesFetchException(
                        SeriesFetchStatus.API_ERROR,
                        "Price series API transient failure HTTP " + response.statusCode() + " for symbol " + symbol + "."
                );
            }
            if (response.statusCode() >= 400) {
                log.warn("Price series API request failed with HTTP {} for symbol {}.", response.statusCode(), symbol);
                return new SeriesFetchResult(Map.of(), SeriesFetchStatus.API_ERROR);
            }

            SeriesResponse seriesResponse = extractSeries(response.body(), symbol);
            if (seriesResponse.retryableRateLimit()) {
                throw new RetryableSeriesFetchException(
                        SeriesFetchStatus.RATE_LIMITED,
                        "Price series API temporarily rate limited for symbol " + symbol + "."
                );
            }
            return new SeriesFetchResult(seriesResponse.series(), seriesResponse.status());
        } catch (IOException e) {
            throw new RetryableSeriesFetchException(SeriesFetchStatus.API_ERROR, "I/O failure when fetching daily series for symbol " + symbol + ".", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RetryableSeriesFetchException(SeriesFetchStatus.API_ERROR, "Interrupted while fetching daily series for symbol " + symbol + ".", e);
        }
    }

    private Duration retryAfter(HttpResponse<String> response) {
        return response.headers().firstValue("Retry-After")
                .map(this::parseRetryAfter)
                .orElse(null);
    }

    private Duration parseRetryAfter(String retryAfterHeader) {
        String header = retryAfterHeader == null ? "" : retryAfterHeader.trim();
        if (header.isEmpty()) {
            return null;
        }
        try {
            long seconds = Long.parseLong(header);
            return Duration.ofSeconds(Math.max(0, seconds));
        } catch (NumberFormatException ignored) {
            // continue to HTTP-date parsing
        }
        try {
            Instant until = ZonedDateTime.parse(header, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            Duration duration = Duration.between(Instant.now(), until);
            return duration.isNegative() ? Duration.ZERO : duration;
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof ExecutionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private SeriesResponse extractSeries(String payload, String symbol) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (root.has("Error Message")) {
                log.warn("Price series API returned error for symbol {}: {}.", symbol, root.path("Error Message").asText());
                String message = root.path("Error Message").asText();
                if (message.toLowerCase().contains("invalid api call")) {
                    return new SeriesResponse(Map.of(), false, SeriesFetchStatus.INVALID_SYMBOL);
                }
                return new SeriesResponse(Map.of(), false, SeriesFetchStatus.API_ERROR);
            }
            if (root.has("Note")) {
                String message = root.path("Note").asText();
                requestLimiter.recordRateLimitMessage(message);
                log.warn("Price series API rate limit reached for symbol {}: {}.", symbol, message);
                return new SeriesResponse(Map.of(), !requestLimiter.isDailyLimitReached(), SeriesFetchStatus.RATE_LIMITED);
            }
            if (root.has("Information")) {
                String message = root.path("Information").asText();
                requestLimiter.recordRateLimitMessage(message);
                log.warn("Price series API returned info for symbol {}: {}.", symbol, message);
                return new SeriesResponse(Map.of(), !requestLimiter.isDailyLimitReached(), SeriesFetchStatus.RATE_LIMITED);
            }

            JsonNode dailySeries = root.path(TIME_SERIES_KEY);
            if (dailySeries.isMissingNode() || !dailySeries.isObject()) {
                return new SeriesResponse(Map.of(), false, SeriesFetchStatus.NO_DATA);
            }

            Map<LocalDate, BigDecimal> result = new HashMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = dailySeries.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                LocalDate date = LocalDate.parse(entry.getKey());
                JsonNode closeNode = entry.getValue().path(DAILY_CLOSE_KEY);
                if (closeNode.isMissingNode()) {
                    continue;
                }
                result.put(date, new BigDecimal(closeNode.asText()));
            }
            return new SeriesResponse(result, false, SeriesFetchStatus.SUCCESS);
        } catch (IOException e) {
            log.warn("Unable to parse price series response for symbol {}.", symbol, e);
            return new SeriesResponse(Map.of(), false, SeriesFetchStatus.API_ERROR);
        }
    }

    private record SeriesResponse(Map<LocalDate, BigDecimal> series, boolean retryableRateLimit, SeriesFetchStatus status) {
    }

    private static final class RetryableSeriesFetchException extends RuntimeException {
        private final SeriesFetchStatus status;

        private RetryableSeriesFetchException(SeriesFetchStatus status, String message) {
            super(message);
            this.status = status;
        }

        private RetryableSeriesFetchException(SeriesFetchStatus status, String message, Throwable cause) {
            super(message, cause);
            this.status = status;
        }

        private SeriesFetchStatus status() {
            return status;
        }
    }
}
