package com.temadison.stockdash.backend.service;

import com.temadison.stockdash.backend.domain.DailyClosePriceEntity;
import com.temadison.stockdash.backend.domain.StockSplitEntity;
import com.temadison.stockdash.backend.model.DailyClosePricePoint;
import com.temadison.stockdash.backend.pricing.SymbolNormalizer;
import com.temadison.stockdash.backend.repository.DailyClosePriceRepository;
import com.temadison.stockdash.backend.repository.StockSplitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
public class DailyClosePriceQueryService implements PriceHistoryService {

    private final DailyClosePriceRepository dailyClosePriceRepository;
    private final StockSplitRepository stockSplitRepository;

    public DailyClosePriceQueryService(
            DailyClosePriceRepository dailyClosePriceRepository,
            StockSplitRepository stockSplitRepository
    ) {
        this.dailyClosePriceRepository = dailyClosePriceRepository;
        this.stockSplitRepository = stockSplitRepository;
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

        List<DailyClosePriceEntity> prices;
        if (startDate != null && endDate != null) {
            prices = dailyClosePriceRepository
                    .findBySymbolAndPriceDateGreaterThanEqualAndPriceDateLessThanEqualOrderByPriceDateDesc(symbol, startDate, endDate);
        } else if (startDate != null) {
            prices = dailyClosePriceRepository.findBySymbolAndPriceDateGreaterThanEqualOrderByPriceDateDesc(symbol, startDate);
        } else if (endDate != null) {
            prices = dailyClosePriceRepository.findBySymbolAndPriceDateLessThanEqualOrderByPriceDateDesc(symbol, endDate);
        } else {
            prices = dailyClosePriceRepository.findBySymbolOrderByPriceDateDesc(symbol);
        }

        if (prices.isEmpty()) {
            return List.of();
        }

        LocalDate adjustmentEndDate = endDate == null ? prices.get(0).getPriceDate() : endDate;
        List<StockSplitEntity> splits = stockSplitRepository
                .findBySymbolAndSplitDateLessThanEqualOrderBySplitDateAsc(symbol, adjustmentEndDate);

        return prices
                .stream()
                .map(entity -> new DailyClosePricePoint(
                        entity.getPriceDate(),
                        splitAdjustedClose(entity.getClosePrice(), entity.getPriceDate(), splits)
                ))
                .toList();
    }

    private BigDecimal splitAdjustedClose(
            BigDecimal rawClose,
            LocalDate priceDate,
            List<StockSplitEntity> splits
    ) {
        BigDecimal factor = BigDecimal.ONE;
        for (StockSplitEntity split : splits) {
            if (split.getSplitDate().isAfter(priceDate)) {
                factor = factor.multiply(split.getSplitRatio());
            }
        }
        if (factor.compareTo(BigDecimal.ONE) == 0) {
            return rawClose;
        }
        return rawClose.divide(factor, 6, RoundingMode.HALF_UP);
    }
}
