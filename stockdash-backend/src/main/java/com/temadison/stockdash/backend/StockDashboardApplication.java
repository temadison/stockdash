package com.temadison.stockdash.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class StockDashboardApplication {
    public static void main(String[] args) {
        SpringApplication.run(StockDashboardApplication.class, args);
    }
}
