package com.temadison.stockdash.backend.service;

import com.temadison.stockdash.backend.domain.DailyClosePriceEntity;
import com.temadison.stockdash.backend.domain.TradeTransactionEntity;
import com.temadison.stockdash.backend.domain.TransactionType;
import com.temadison.stockdash.backend.model.PriceSyncResult;
import com.temadison.stockdash.backend.pricing.alphavantage.AlphaVantageDailySeriesClient;
import com.temadison.stockdash.backend.repository.DailyClosePriceRepository;
import com.temadison.stockdash.backend.repository.TradeTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DailyClosePriceSyncService {

    private final TradeTransactionRepository tradeTransactionRepository;
    private final DailyClosePriceRepository dailyClosePriceRepository;
    private final AlphaVantageDailySeriesClient alphaVantageDailySeriesClient;

    public DailyClosePriceSyncService(
            TradeTransactionRepository tradeTransactionRepository,
            DailyClosePriceRepository dailyClosePriceRepository,
            AlphaVantageDailySeriesClient alphaVantageDailySeriesClient
    ) {
        this.tradeTransactionRepository = tradeTransactionRepository;
        this.dailyClosePriceRepository = dailyClosePriceRepository;
        this.alphaVantageDailySeriesClient = alphaVantageDailySeriesClient;
    }

    @Transactional
    public PriceSyncResult syncForStocks(List<String> stocks) {
        List<String> normalizedSymbols = normalizeStocks(stocks);
        Map<String, LocalDate> firstBuyDateBySymbol = firstBuyDatesBySymbol(normalizedSymbols);

        Map<String, Integer> storedBySymbol = new LinkedHashMap<>();
        List<String> skippedSymbols = new ArrayList<>();
        int pricesStored = 0;
        int symbolsWithPurchases = 0;

        for (String symbol : normalizedSymbols) {
            LocalDate firstBuyDate = firstBuyDateBySymbol.get(symbol);
            if (firstBuyDate == null) {
                skippedSymbols.add(symbol);
                continue;
            }

            symbolsWithPurchases++;
            LocalDate latestStoredDate = dailyClosePriceRepository.findTopBySymbolOrderByPriceDateDesc(symbol)
                    .map(DailyClosePriceEntity::getPriceDate)
                    .orElse(null);
            if (latestStoredDate != null && !latestStoredDate.isBefore(LocalDate.now().minusDays(1))) {
                storedBySymbol.put(symbol, 0);
                continue;
            }

            Map<LocalDate, BigDecimal> dailySeries = alphaVantageDailySeriesClient.fetchDailyCloseSeries(symbol);
            if (dailySeries.isEmpty()) {
                storedBySymbol.put(symbol, 0);
                continue;
            }

            Set<LocalDate> existingDates = dailyClosePriceRepository.findBySymbolAndPriceDateAfter(symbol, firstBuyDate)
                    .stream()
                    .map(DailyClosePriceEntity::getPriceDate)
                    .collect(LinkedHashSet::new, Set::add, Set::addAll);

            List<DailyClosePriceEntity> toSave = new ArrayList<>();
            for (Map.Entry<LocalDate, BigDecimal> entry : dailySeries.entrySet()) {
                LocalDate priceDate = entry.getKey();
                if (!priceDate.isAfter(firstBuyDate) || existingDates.contains(priceDate)) {
                    continue;
                }
                DailyClosePriceEntity entity = new DailyClosePriceEntity();
                entity.setSymbol(symbol);
                entity.setPriceDate(priceDate);
                entity.setClosePrice(entry.getValue());
                toSave.add(entity);
            }

            if (!toSave.isEmpty()) {
                dailyClosePriceRepository.saveAll(toSave);
            }
            storedBySymbol.put(symbol, toSave.size());
            pricesStored += toSave.size();
        }

        return new PriceSyncResult(
                normalizedSymbols.size(),
                symbolsWithPurchases,
                pricesStored,
                storedBySymbol,
                skippedSymbols
        );
    }

    private List<String> normalizeStocks(List<String> stocks) {
        if (stocks == null || stocks.isEmpty()) {
            throw new IllegalArgumentException("stocks is required and must contain at least one symbol.");
        }

        Set<String> normalized = new LinkedHashSet<>();
        for (String stock : stocks) {
            if (stock == null || stock.isBlank()) {
                continue;
            }
            normalized.add(stock.trim().toUpperCase());
        }

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("stocks is required and must contain at least one non-blank symbol.");
        }
        return new ArrayList<>(normalized);
    }

    private Map<String, LocalDate> firstBuyDatesBySymbol(List<String> symbols) {
        List<TradeTransactionEntity> buys = tradeTransactionRepository
                .findBySymbolInAndTypeOrderByTradeDateAscIdAsc(symbols, TransactionType.BUY);
        Map<String, LocalDate> firstBySymbol = new LinkedHashMap<>();
        for (TradeTransactionEntity buy : buys) {
            firstBySymbol.putIfAbsent(buy.getSymbol(), buy.getTradeDate());
        }
        return firstBySymbol;
    }
}
