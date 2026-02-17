package com.temadison.stockdash.backend.pricing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

public interface MarketPriceService {
    Optional<BigDecimal> getClosePriceOnOrBefore(String symbol, LocalDate asOfDate);
}
