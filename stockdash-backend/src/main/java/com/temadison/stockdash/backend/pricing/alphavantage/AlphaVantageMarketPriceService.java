package com.temadison.stockdash.backend.pricing.alphavantage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.temadison.stockdash.backend.pricing.MarketPriceService;
import com.temadison.stockdash.backend.pricing.PricingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AlphaVantageMarketPriceService implements MarketPriceService {

    private static final Logger log = LoggerFactory.getLogger(AlphaVantageMarketPriceService.class);
    private static final String TIME_SERIES_KEY = "Time Series (Daily)";
    private static final String DAILY_CLOSE_KEY = "4. close";

    private final PricingProperties pricingProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final AlphaVantageRequestLimiter requestLimiter;
    private final Map<PriceLookupKey, Optional<BigDecimal>> closePriceCache;

    public AlphaVantageMarketPriceService(
            PricingProperties pricingProperties,
            AlphaVantageRequestLimiter requestLimiter,
            HttpClient alphaVantageHttpClient
    ) {
        this.pricingProperties = pricingProperties;
        this.requestLimiter = requestLimiter;
        this.objectMapper = new ObjectMapper();
        this.httpClient = alphaVantageHttpClient;
        this.closePriceCache = new ConcurrentHashMap<>();
    }

    @Override
    public Optional<BigDecimal> getClosePriceOnOrBefore(String symbol, LocalDate asOfDate) {
        if (!StringUtils.hasText(pricingProperties.alphaVantageApiKey())) {
            return Optional.empty();
        }
        if (requestLimiter.isDailyLimitReached()) {
            return Optional.empty();
        }
        PriceLookupKey cacheKey = new PriceLookupKey(symbol, asOfDate);
        Optional<BigDecimal> cachedClose = closePriceCache.get(cacheKey);
        if (cachedClose != null) {
            return cachedClose;
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
            requestLimiter.awaitTurn();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.warn("Price API request failed with HTTP {} for symbol {}; falling back to last known trade price.", response.statusCode(), symbol);
                Optional<BigDecimal> missing = Optional.empty();
                closePriceCache.put(cacheKey, missing);
                return missing;
            }
            Optional<BigDecimal> extractedClose = extractClosePrice(response.body(), symbol, asOfDate);
            closePriceCache.put(cacheKey, extractedClose);
            return extractedClose;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Price API request interrupted for symbol {}; falling back to last known trade price.", symbol, e);
            Optional<BigDecimal> missing = Optional.empty();
            closePriceCache.put(cacheKey, missing);
            return missing;
        } catch (IOException e) {
            log.warn("Unable to fetch market price for symbol {}; falling back to last known trade price.", symbol, e);
            Optional<BigDecimal> missing = Optional.empty();
            closePriceCache.put(cacheKey, missing);
            return missing;
        }
    }

    private Optional<BigDecimal> extractClosePrice(String payload, String symbol, LocalDate asOfDate) {
        try {
            JsonNode root = objectMapper.readTree(payload);

            if (root.has("Error Message")) {
                log.warn("Price API returned error for symbol {}: {}; falling back to last known trade price.",
                        symbol, root.path("Error Message").asText());
                return Optional.empty();
            }
            if (root.has("Note")) {
                String message = root.path("Note").asText();
                requestLimiter.recordRateLimitMessage(message);
                log.warn("Price API rate limit reached for symbol {}: {}; falling back to last known trade price.",
                        symbol, message);
                return Optional.empty();
            }
            if (root.has("Information")) {
                String message = root.path("Information").asText();
                requestLimiter.recordRateLimitMessage(message);
                log.warn("Price API returned info for symbol {}: {}; falling back to last known trade price.",
                        symbol, message);
                return Optional.empty();
            }

            JsonNode dailySeries = root.path(TIME_SERIES_KEY);
            if (dailySeries.isMissingNode() || !dailySeries.isObject()) {
                return Optional.empty();
            }

            LocalDate bestDate = null;
            BigDecimal bestClose = null;

            Iterator<Map.Entry<String, JsonNode>> fields = dailySeries.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                LocalDate priceDate = LocalDate.parse(entry.getKey());
                if (priceDate.isAfter(asOfDate)) {
                    continue;
                }

                JsonNode closeNode = entry.getValue().path(DAILY_CLOSE_KEY);
                if (closeNode.isMissingNode()) {
                    continue;
                }

                if (bestDate == null || priceDate.isAfter(bestDate)) {
                    bestDate = priceDate;
                    bestClose = new BigDecimal(closeNode.asText());
                }
            }

            return Optional.ofNullable(bestClose);
        } catch (IOException e) {
            log.warn("Unable to parse price API response for symbol {}; falling back to last known trade price.", symbol, e);
            return Optional.empty();
        }
    }

    private record PriceLookupKey(String symbol, LocalDate asOfDate) {
    }
}
