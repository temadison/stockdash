package com.temadison.stockdash.backend.service;

import com.temadison.stockdash.backend.model.PortfolioPerformancePoint;

import java.time.LocalDate;
import java.util.List;

public interface PortfolioPerformanceQueryService {

    List<PortfolioPerformancePoint> performance(String accountName, LocalDate startDate, LocalDate endDate);
}
