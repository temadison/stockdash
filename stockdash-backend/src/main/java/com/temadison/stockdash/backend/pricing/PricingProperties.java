package com.temadison.stockdash.backend.pricing;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stockdash.pricing")
public record PricingProperties(
        String alphaVantageApiKey,
        String alphaVantageBaseUrl
) {
}
