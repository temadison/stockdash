package com.temadison.stockdash.backend.pricing.alphavantage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.temadison.stockdash.backend.pricing.PricingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Component
public class AlphaVantageDailySeriesClient {

    private static final Logger log = LoggerFactory.getLogger(AlphaVantageDailySeriesClient.class);
    private static final String TIME_SERIES_KEY = "Time Series (Daily)";
    private static final String DAILY_CLOSE_KEY = "4. close";
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_BACKOFF_MS = 2000L;

    private final PricingProperties pricingProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final AlphaVantageRequestLimiter requestLimiter;

    public AlphaVantageDailySeriesClient(
            PricingProperties pricingProperties,
            AlphaVantageRequestLimiter requestLimiter
    ) {
        this.pricingProperties = pricingProperties;
        this.requestLimiter = requestLimiter;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public SeriesFetchResult fetchDailyCloseSeries(String symbol) {
        if (!StringUtils.hasText(pricingProperties.alphaVantageApiKey())) {
            return new SeriesFetchResult(Map.of(), SeriesFetchStatus.NO_DATA);
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
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                requestLimiter.awaitTurn();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 400) {
                    log.warn("Price series API request failed with HTTP {} for symbol {}.", response.statusCode(), symbol);
                    return new SeriesFetchResult(Map.of(), SeriesFetchStatus.API_ERROR);
                }

                SeriesResponse seriesResponse = extractSeries(response.body(), symbol);
                if (!seriesResponse.retryableRateLimit() || attempt == MAX_ATTEMPTS) {
                    return new SeriesFetchResult(seriesResponse.series(), seriesResponse.status());
                }
                Thread.sleep(RETRY_BACKOFF_MS * attempt);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Price series request interrupted for symbol {}.", symbol, e);
                return new SeriesFetchResult(Map.of(), SeriesFetchStatus.API_ERROR);
            } catch (IOException e) {
                log.warn("Unable to fetch price series for symbol {}.", symbol, e);
                return new SeriesFetchResult(Map.of(), SeriesFetchStatus.API_ERROR);
            }
        }

        return new SeriesFetchResult(Map.of(), SeriesFetchStatus.RATE_LIMITED);
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
                log.warn("Price series API rate limit reached for symbol {}: {}.", symbol, root.path("Note").asText());
                return new SeriesResponse(Map.of(), true, SeriesFetchStatus.RATE_LIMITED);
            }
            if (root.has("Information")) {
                log.warn("Price series API returned info for symbol {}: {}.", symbol, root.path("Information").asText());
                return new SeriesResponse(Map.of(), true, SeriesFetchStatus.RATE_LIMITED);
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
}
