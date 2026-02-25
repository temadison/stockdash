package com.temadison.stockdash.backend.pricing;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "stockdash.pricing")
public record PricingProperties(
        String alphaVantageApiKey,
        String alphaVantageBaseUrl,
        Duration alphaVantageConnectTimeout,
        Duration alphaVantageRequestTimeout
) {
    public Duration connectTimeoutOrDefault() {
        return alphaVantageConnectTimeout == null ? Duration.ofSeconds(10) : alphaVantageConnectTimeout;
    }

    public Duration requestTimeoutOrDefault() {
        return alphaVantageRequestTimeout == null ? Duration.ofSeconds(15) : alphaVantageRequestTimeout;
    }
}
