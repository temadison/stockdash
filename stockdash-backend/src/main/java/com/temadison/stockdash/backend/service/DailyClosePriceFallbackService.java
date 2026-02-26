package com.temadison.stockdash.backend.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public interface DailyClosePriceFallbackService {

    Map<LocalDate, BigDecimal> generateSeries(String symbol, LocalDate firstBuyDate, int lookbackDays);
}
