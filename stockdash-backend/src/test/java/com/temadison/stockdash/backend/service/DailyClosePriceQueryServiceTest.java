package com.temadison.stockdash.backend.service;

import com.temadison.stockdash.backend.domain.DailyClosePriceEntity;
import com.temadison.stockdash.backend.domain.StockSplitEntity;
import com.temadison.stockdash.backend.model.DailyClosePricePoint;
import com.temadison.stockdash.backend.repository.DailyClosePriceRepository;
import com.temadison.stockdash.backend.repository.StockSplitRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyClosePriceQueryServiceTest {

    @Mock
    private DailyClosePriceRepository dailyClosePriceRepository;

    @Mock
    private StockSplitRepository stockSplitRepository;

    @Test
    void historyReturnsSplitAdjustedClosesForChartContinuity() {
        DailyClosePriceQueryService service = new DailyClosePriceQueryService(
                dailyClosePriceRepository,
                stockSplitRepository
        );
        LocalDate startDate = LocalDate.of(2025, 6, 10);
        LocalDate endDate = LocalDate.of(2025, 6, 13);

        when(dailyClosePriceRepository.findBySymbolAndPriceDateGreaterThanEqualAndPriceDateLessThanEqualOrderByPriceDateDesc(
                "KLAC",
                startDate,
                endDate
        )).thenReturn(List.of(
                price("KLAC", "2025-06-13", "88.00"),
                price("KLAC", "2025-06-12", "87.00"),
                price("KLAC", "2025-06-11", "860.00"),
                price("KLAC", "2025-06-10", "850.00")
        ));
        when(stockSplitRepository.findBySymbolAndSplitDateLessThanEqualOrderBySplitDateAsc("KLAC", endDate))
                .thenReturn(List.of(split("KLAC", "2025-06-12", "10.000000")));

        List<DailyClosePricePoint> history = service.history("klac", startDate, endDate);

        assertThat(history).containsExactly(
                new DailyClosePricePoint(LocalDate.of(2025, 6, 13), new BigDecimal("88.00")),
                new DailyClosePricePoint(LocalDate.of(2025, 6, 12), new BigDecimal("87.00")),
                new DailyClosePricePoint(LocalDate.of(2025, 6, 11), new BigDecimal("86.000000")),
                new DailyClosePricePoint(LocalDate.of(2025, 6, 10), new BigDecimal("85.000000"))
        );
    }

    @Test
    void historyDoesNotApplyFutureSplitsOutsideRequestedRange() {
        DailyClosePriceQueryService service = new DailyClosePriceQueryService(
                dailyClosePriceRepository,
                stockSplitRepository
        );
        LocalDate startDate = LocalDate.of(2025, 6, 10);
        LocalDate endDate = LocalDate.of(2025, 6, 11);

        when(dailyClosePriceRepository.findBySymbolAndPriceDateGreaterThanEqualAndPriceDateLessThanEqualOrderByPriceDateDesc(
                "KLAC",
                startDate,
                endDate
        )).thenReturn(List.of(
                price("KLAC", "2025-06-11", "860.00"),
                price("KLAC", "2025-06-10", "850.00")
        ));
        when(stockSplitRepository.findBySymbolAndSplitDateLessThanEqualOrderBySplitDateAsc("KLAC", endDate))
                .thenReturn(List.of());

        List<DailyClosePricePoint> history = service.history("KLAC", startDate, endDate);

        assertThat(history).containsExactly(
                new DailyClosePricePoint(LocalDate.of(2025, 6, 11), new BigDecimal("860.00")),
                new DailyClosePricePoint(LocalDate.of(2025, 6, 10), new BigDecimal("850.00"))
        );
    }

    private DailyClosePriceEntity price(String symbol, String priceDate, String closePrice) {
        DailyClosePriceEntity entity = new DailyClosePriceEntity();
        entity.setSymbol(symbol);
        entity.setPriceDate(LocalDate.parse(priceDate));
        entity.setClosePrice(new BigDecimal(closePrice));
        return entity;
    }

    private StockSplitEntity split(String symbol, String splitDate, String splitRatio) {
        StockSplitEntity entity = new StockSplitEntity();
        entity.setSymbol(symbol);
        entity.setSplitDate(LocalDate.parse(splitDate));
        entity.setSplitRatio(new BigDecimal(splitRatio));
        return entity;
    }
}
