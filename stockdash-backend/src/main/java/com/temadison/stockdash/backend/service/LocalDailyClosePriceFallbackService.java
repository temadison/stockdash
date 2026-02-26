package com.temadison.stockdash.backend.service;

import com.temadison.stockdash.backend.domain.TradeTransactionEntity;
import com.temadison.stockdash.backend.repository.TradeTransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LocalDailyClosePriceFallbackService implements DailyClosePriceFallbackService {

    private final TradeTransactionRepository tradeTransactionRepository;

    public LocalDailyClosePriceFallbackService(TradeTransactionRepository tradeTransactionRepository) {
        this.tradeTransactionRepository = tradeTransactionRepository;
    }

    @Override
    public Map<LocalDate, BigDecimal> generateSeries(String symbol, LocalDate firstBuyDate, int lookbackDays) {
        List<TradeTransactionEntity> trades = tradeTransactionRepository.findBySymbolOrderByTradeDateAscIdAsc(symbol);
        if (trades.isEmpty()) {
            return Map.of();
        }

        LocalDate startDate = firstBuyDate.plusDays(1);
        LocalDate endDate = LocalDate.now().minusDays(1);
        if (startDate.isAfter(endDate)) {
            return Map.of();
        }

        LocalDate minDate = LocalDate.now().minusDays(Math.max(1, lookbackDays));
        if (startDate.isBefore(minDate)) {
            startDate = minDate;
        }
        if (startDate.isAfter(endDate)) {
            return Map.of();
        }

        BigDecimal currentPrice = null;
        int index = 0;
        while (index < trades.size() && !trades.get(index).getTradeDate().isAfter(startDate)) {
            currentPrice = trades.get(index).getPrice();
            index++;
        }

        Map<LocalDate, BigDecimal> series = new LinkedHashMap<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            while (index < trades.size() && trades.get(index).getTradeDate().isEqual(date)) {
                currentPrice = trades.get(index).getPrice();
                index++;
            }
            if (currentPrice != null) {
                series.put(date, currentPrice);
            }
        }
        return series;
    }
}
