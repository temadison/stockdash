package com.temadison.stockdash.backend.service;

import com.temadison.stockdash.backend.domain.DailyClosePriceEntity;
import com.temadison.stockdash.backend.domain.TradeTransactionEntity;
import com.temadison.stockdash.backend.domain.TransactionType;
import com.temadison.stockdash.backend.model.PortfolioPerformancePoint;
import com.temadison.stockdash.backend.model.StockPerformanceValue;
import com.temadison.stockdash.backend.repository.DailyClosePriceRepository;
import com.temadison.stockdash.backend.repository.TradeTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PortfolioPerformanceService {

    private final TradeTransactionRepository tradeTransactionRepository;
    private final DailyClosePriceRepository dailyClosePriceRepository;

    public PortfolioPerformanceService(
            TradeTransactionRepository tradeTransactionRepository,
            DailyClosePriceRepository dailyClosePriceRepository
    ) {
        this.tradeTransactionRepository = tradeTransactionRepository;
        this.dailyClosePriceRepository = dailyClosePriceRepository;
    }

    @Transactional(readOnly = true)
    public List<PortfolioPerformancePoint> performance(String accountName, LocalDate startDate, LocalDate endDate) {
        LocalDate resolvedEnd = endDate == null ? LocalDate.now() : endDate;
        List<TradeTransactionEntity> transactions = tradeTransactionRepository
                .findByTradeDateLessThanEqualOrderByTradeDateAscIdAsc(resolvedEnd)
                .stream()
                .filter(tx -> includeTransactionForAccount(tx, accountName))
                .toList();
        if (transactions.isEmpty()) {
            return List.of();
        }

        LocalDate earliestTradeDate = transactions.get(0).getTradeDate();
        LocalDate resolvedStart = startDate == null ? earliestTradeDate : startDate;
        if (resolvedStart.isAfter(resolvedEnd)) {
            throw new IllegalArgumentException("startDate must be on or before endDate.");
        }

        Map<String, List<DailyClosePriceEntity>> closesBySymbol = preloadClosesBySymbol(transactions, resolvedEnd);
        Map<String, Integer> closeCursorBySymbol = new HashMap<>();
        Map<String, BigDecimal> latestCloseBySymbol = new HashMap<>();

        Map<String, PositionAccumulator> positions = new HashMap<>();
        int txIndex = 0;
        List<PortfolioPerformancePoint> points = new ArrayList<>();

        for (LocalDate day = resolvedStart; !day.isAfter(resolvedEnd); day = day.plusDays(1)) {
            while (txIndex < transactions.size() && !transactions.get(txIndex).getTradeDate().isAfter(day)) {
                TradeTransactionEntity tx = transactions.get(txIndex);
                PositionAccumulator acc = positions.computeIfAbsent(tx.getSymbol(), ignored -> new PositionAccumulator());
                BigDecimal signedQty = tx.getType() == TransactionType.BUY ? tx.getQuantity() : tx.getQuantity().negate();
                acc.netQuantity = acc.netQuantity.add(signedQty);
                acc.totalFees = acc.totalFees.add(tx.getFee());
                acc.lastKnownPrice = tx.getPrice();
                txIndex++;
            }

            List<StockPerformanceValue> stocks = new ArrayList<>();
            BigDecimal total = BigDecimal.ZERO;
            for (Map.Entry<String, PositionAccumulator> entry : positions.entrySet()) {
                if (entry.getValue().netQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                String symbol = entry.getKey();
                BigDecimal close = closeOnOrBefore(day, symbol, closesBySymbol, closeCursorBySymbol, latestCloseBySymbol);
                if (close == null) {
                    close = entry.getValue().lastKnownPrice;
                }

                BigDecimal value = entry.getValue().netQuantity
                        .multiply(close)
                        .subtract(entry.getValue().totalFees)
                        .setScale(2, RoundingMode.HALF_UP);
                stocks.add(new StockPerformanceValue(symbol, value));
                total = total.add(value);
            }

            stocks.sort(Comparator.comparing(StockPerformanceValue::symbol));
            points.add(new PortfolioPerformancePoint(day, total.setScale(2, RoundingMode.HALF_UP), stocks));
        }

        return points;
    }

    private boolean includeTransactionForAccount(TradeTransactionEntity tx, String accountName) {
        if (accountName == null || accountName.isBlank() || "TOTAL".equalsIgnoreCase(accountName)) {
            return true;
        }
        return tx.getAccount().getName().equalsIgnoreCase(accountName.trim());
    }

    private Map<String, List<DailyClosePriceEntity>> preloadClosesBySymbol(List<TradeTransactionEntity> transactions, LocalDate endDate) {
        Map<String, List<DailyClosePriceEntity>> closesBySymbol = new HashMap<>();
        transactions.stream()
                .map(TradeTransactionEntity::getSymbol)
                .distinct()
                .forEach(symbol -> closesBySymbol.put(
                        symbol,
                        dailyClosePriceRepository
                                .findBySymbolAndPriceDateLessThanEqualOrderByPriceDateDesc(symbol, endDate)
                                .stream()
                                .sorted(Comparator.comparing(DailyClosePriceEntity::getPriceDate))
                                .toList()
                ));
        return closesBySymbol;
    }

    private BigDecimal closeOnOrBefore(
            LocalDate day,
            String symbol,
            Map<String, List<DailyClosePriceEntity>> closesBySymbol,
            Map<String, Integer> closeCursorBySymbol,
            Map<String, BigDecimal> latestCloseBySymbol
    ) {
        List<DailyClosePriceEntity> closes = closesBySymbol.getOrDefault(symbol, List.of());
        int cursor = closeCursorBySymbol.getOrDefault(symbol, 0);
        while (cursor < closes.size() && !closes.get(cursor).getPriceDate().isAfter(day)) {
            latestCloseBySymbol.put(symbol, closes.get(cursor).getClosePrice());
            cursor++;
        }
        closeCursorBySymbol.put(symbol, cursor);
        return latestCloseBySymbol.get(symbol);
    }

    private static class PositionAccumulator {
        private BigDecimal netQuantity = BigDecimal.ZERO;
        private BigDecimal totalFees = BigDecimal.ZERO;
        private BigDecimal lastKnownPrice = BigDecimal.ZERO;
    }
}
