package com.temadison.stockdash.backend.pricing.alphavantage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public record SeriesFetchResult(Map<LocalDate, BigDecimal> series, SeriesFetchStatus status) {
}
