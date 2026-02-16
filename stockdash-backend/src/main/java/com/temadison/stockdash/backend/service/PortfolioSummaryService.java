package com.temadison.stockdash.backend.service;

import com.temadison.stockdash.backend.model.PortfolioSnapshot;
import com.temadison.stockdash.backend.model.PositionValue;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class PortfolioSummaryService {

    public List<PortfolioSnapshot> getDailySummary(LocalDate asOfDate) {
        // Temporary in-memory sample data while CSV import and price integrations are built.
        PortfolioSnapshot accountA = buildSnapshot("Account A", asOfDate, Map.of(
                "AAPL", new BigDecimal("12450.25"),
                "MSFT", new BigDecimal("9520.40"),
                "VOO", new BigDecimal("14880.15")
        ));

        PortfolioSnapshot accountB = buildSnapshot("Account B", asOfDate, Map.of(
                "NVDA", new BigDecimal("11320.50"),
                "AMZN", new BigDecimal("6225.75"),
                "VTI", new BigDecimal("10440.95")
        ));

        return List.of(accountA, accountB);
    }

    private PortfolioSnapshot buildSnapshot(String accountName, LocalDate asOfDate, Map<String, BigDecimal> valueBySymbol) {
        List<PositionValue> positions = valueBySymbol.entrySet()
                .stream()
                .map(entry -> new PositionValue(entry.getKey(), entry.getValue()))
                .toList();

        BigDecimal totalValue = positions.stream()
                .map(PositionValue::marketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new PortfolioSnapshot(accountName, asOfDate, totalValue, positions);
    }
}
