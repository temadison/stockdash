package com.temadison.stockdash.backend.seed;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stockdash.seed")
public record SeedProperties(boolean enabled, String csvResource) {
}
