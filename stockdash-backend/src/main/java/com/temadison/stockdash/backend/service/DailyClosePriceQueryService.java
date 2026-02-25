package com.temadison.stockdash.backend.service;

import com.temadison.stockdash.backend.model.DailyClosePricePoint;
import com.temadison.stockdash.backend.pricing.SymbolNormalizer;
import com.temadison.stockdash.backend.repository.DailyClosePriceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class DailyClosePriceQueryService implements PriceHistoryService {

    private final DailyClosePriceRepository dailyClosePriceRepository;

    public DailyClosePriceQueryService(DailyClosePriceRepository dailyClosePriceRepository) {
        this.dailyClosePriceRepository = dailyClosePriceRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DailyClosePricePoint> history(String rawSymbol, LocalDate startDate, LocalDate endDate) {
        if (rawSymbol == null || rawSymbol.isBlank()) {
            throw new IllegalArgumentException("symbol is required.");
        }
        String symbol = SymbolNormalizer.normalize(rawSymbol);
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("startDate must be on or before endDate.");
        }

        if (startDate != null && endDate != null) {
            return dailyClosePriceRepository
                    .findBySymbolAndPriceDateGreaterThanEqualAndPriceDateLessThanEqualOrderByPriceDateDesc(symbol, startDate, endDate)
                    .stream()
                    .map(entity -> new DailyClosePricePoint(entity.getPriceDate(), entity.getClosePrice()))
                    .toList();
        }
        if (startDate != null) {
            return dailyClosePriceRepository.findBySymbolAndPriceDateGreaterThanEqualOrderByPriceDateDesc(symbol, startDate)
                    .stream()
                    .map(entity -> new DailyClosePricePoint(entity.getPriceDate(), entity.getClosePrice()))
                    .toList();
        }
        if (endDate != null) {
            return dailyClosePriceRepository.findBySymbolAndPriceDateLessThanEqualOrderByPriceDateDesc(symbol, endDate)
                    .stream()
                    .map(entity -> new DailyClosePricePoint(entity.getPriceDate(), entity.getClosePrice()))
                    .toList();
        }

        return dailyClosePriceRepository.findBySymbolOrderByPriceDateDesc(symbol)
                .stream()
                .map(entity -> new DailyClosePricePoint(entity.getPriceDate(), entity.getClosePrice()))
                .toList();
    }
}
