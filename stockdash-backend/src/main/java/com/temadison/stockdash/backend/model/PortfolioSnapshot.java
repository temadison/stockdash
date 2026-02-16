package com.temadison.stockdash.backend.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PortfolioSnapshot(
        String accountName,
        LocalDate asOfDate,
        BigDecimal totalValue,
        List<PositionValue> positions
) {
}
