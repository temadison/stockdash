package com.temadison.stockdash.backend.service;

import com.temadison.stockdash.backend.model.DailyClosePricePoint;

import java.time.LocalDate;
import java.util.List;

public interface PriceHistoryService {

    List<DailyClosePricePoint> history(String rawSymbol, LocalDate startDate, LocalDate endDate);
}
