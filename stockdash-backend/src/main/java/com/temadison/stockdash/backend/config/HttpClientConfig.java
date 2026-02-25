package com.temadison.stockdash.backend.config;

import com.temadison.stockdash.backend.pricing.PricingProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;

@Configuration
public class HttpClientConfig {

    @Bean
    public HttpClient alphaVantageHttpClient(PricingProperties pricingProperties) {
        return HttpClient.newBuilder()
                .connectTimeout(pricingProperties.connectTimeoutOrDefault())
                .build();
    }
}
