package com.temadison.stockdash.backend.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PortfolioSnapshotDto(
        String accountName,
        LocalDate asOfDate,
        BigDecimal totalValue,
        List<PositionValueDto> positions
) {
}
