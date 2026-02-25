package com.temadison.stockdash.backend.service;

import com.temadison.stockdash.backend.model.PortfolioSnapshot;

import java.time.LocalDate;
import java.util.List;

public interface PortfolioSummaryQueryService {

    List<PortfolioSnapshot> getDailySummary(LocalDate asOfDate);
}
