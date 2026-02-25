package com.temadison.stockdash.backend.pricing.alphavantage;

import com.temadison.stockdash.backend.pricing.PricingProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlphaVantageMarketPriceServiceTest {

    @Test
    void returnsEmptyWhenApiKeyMissing() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        AlphaVantageRequestLimiter requestLimiter = mock(AlphaVantageRequestLimiter.class);
        AlphaVantageMarketPriceService service = new AlphaVantageMarketPriceService(
                new PricingProperties("", "https://example.com/query", Duration.ofSeconds(1), Duration.ofSeconds(1)),
                requestLimiter,
                httpClient
        );

        Optional<BigDecimal> result = service.getClosePriceOnOrBefore("AAPL", LocalDate.of(2026, 1, 2));

        assertThat(result).isEmpty();
        verify(httpClient, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void cachesMissingResultWhenHttpErrorOccurs() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        AlphaVantageRequestLimiter requestLimiter = mock(AlphaVantageRequestLimiter.class);
        doReturn(false).when(requestLimiter).isDailyLimitReached();

        @SuppressWarnings("unchecked")
        HttpResponse<String> response = (HttpResponse<String>) mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(500);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        AlphaVantageMarketPriceService service = new AlphaVantageMarketPriceService(
                new PricingProperties("test-key", "https://example.com/query", Duration.ofSeconds(1), Duration.ofSeconds(1)),
                requestLimiter,
                httpClient
        );

        Optional<BigDecimal> first = service.getClosePriceOnOrBefore("AAPL", LocalDate.of(2026, 1, 2));
        Optional<BigDecimal> second = service.getClosePriceOnOrBefore("AAPL", LocalDate.of(2026, 1, 2));

        assertThat(first).isEmpty();
        assertThat(second).isEmpty();
        verify(httpClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void returnsLatestCloseOnOrBeforeAsOfDate() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        AlphaVantageRequestLimiter requestLimiter = mock(AlphaVantageRequestLimiter.class);
        doReturn(false).when(requestLimiter).isDailyLimitReached();

        @SuppressWarnings("unchecked")
        HttpResponse<String> response = (HttpResponse<String>) mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {
                  "Time Series (Daily)": {
                    "2026-01-03": {"4. close": "103.00"},
                    "2026-01-02": {"4. close": "102.00"},
                    "2026-01-01": {"4. close": "101.00"}
                  }
                }
                """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        AlphaVantageMarketPriceService service = new AlphaVantageMarketPriceService(
                new PricingProperties("test-key", "https://example.com/query", Duration.ofSeconds(1), Duration.ofSeconds(1)),
                requestLimiter,
                httpClient
        );

        Optional<BigDecimal> result = service.getClosePriceOnOrBefore("AAPL", LocalDate.of(2026, 1, 2));

        assertThat(result).contains(new BigDecimal("102.00"));
    }

    @Test
    void recordsRateLimitMessageForNotePayload() throws Exception {
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

        AlphaVantageMarketPriceService service = new AlphaVantageMarketPriceService(
                new PricingProperties("test-key", "https://example.com/query", Duration.ofSeconds(1), Duration.ofSeconds(1)),
                requestLimiter,
                httpClient
        );

        Optional<BigDecimal> result = service.getClosePriceOnOrBefore("AAPL", LocalDate.of(2026, 1, 2));

        assertThat(result).isEmpty();
        verify(requestLimiter, times(1)).recordRateLimitMessage(any(String.class));
    }
}
