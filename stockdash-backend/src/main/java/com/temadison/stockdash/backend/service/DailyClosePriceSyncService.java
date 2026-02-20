package com.temadison.stockdash.backend.service;

import com.temadison.stockdash.backend.domain.DailyClosePriceEntity;
import com.temadison.stockdash.backend.domain.TradeTransactionEntity;
import com.temadison.stockdash.backend.domain.TransactionType;
import com.temadison.stockdash.backend.model.PriceSyncResult;
import com.temadison.stockdash.backend.pricing.SymbolNormalizer;
import com.temadison.stockdash.backend.pricing.alphavantage.AlphaVantageDailySeriesClient;
import com.temadison.stockdash.backend.pricing.alphavantage.SeriesFetchResult;
import com.temadison.stockdash.backend.pricing.alphavantage.SeriesFetchStatus;
import com.temadison.stockdash.backend.repository.DailyClosePriceRepository;
import com.temadison.stockdash.backend.repository.TradeTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DailyClosePriceSyncService {

    private static final Logger log = LoggerFactory.getLogger(DailyClosePriceSyncService.class);

    private final TradeTransactionRepository tradeTransactionRepository;
    private final DailyClosePriceRepository dailyClosePriceRepository;
    private final AlphaVantageDailySeriesClient alphaVantageDailySeriesClient;
    private final Map<String, Object> symbolSyncLocks = new ConcurrentHashMap<>();

    public DailyClosePriceSyncService(
            TradeTransactionRepository tradeTransactionRepository,
            DailyClosePriceRepository dailyClosePriceRepository,
            AlphaVantageDailySeriesClient alphaVantageDailySeriesClient
    ) {
        this.tradeTransactionRepository = tradeTransactionRepository;
        this.dailyClosePriceRepository = dailyClosePriceRepository;
        this.alphaVantageDailySeriesClient = alphaVantageDailySeriesClient;
    }

    public PriceSyncResult syncForStocks(List<String> stocks) {
        List<String> normalizedSymbols = normalizeStocks(stocks);
        Map<String, LocalDate> firstBuyDateBySymbol = firstBuyDatesBySymbol(normalizedSymbols);

        Map<String, Integer> storedBySymbol = new LinkedHashMap<>();
        Map<String, String> statusBySymbol = new LinkedHashMap<>();
        List<String> skippedSymbols = new ArrayList<>();
        int pricesStored = 0;
        int symbolsWithPurchases = 0;

        for (String symbol : normalizedSymbols) {
            LocalDate firstBuyDate = firstBuyDateBySymbol.get(symbol);
            if (firstBuyDate == null) {
                skippedSymbols.add(symbol);
                statusBySymbol.put(symbol, "no_purchase_history");
                continue;
            }

            symbolsWithPurchases++;
            Object symbolLock = symbolSyncLocks.computeIfAbsent(symbol, ignored -> new Object());
            synchronized (symbolLock) {
                LocalDate latestStoredDate = dailyClosePriceRepository.findTopBySymbolOrderByPriceDateDesc(symbol)
                        .map(DailyClosePriceEntity::getPriceDate)
                        .orElse(null);
                if (latestStoredDate != null && !latestStoredDate.isBefore(LocalDate.now().minusDays(1))) {
                    storedBySymbol.put(symbol, 0);
                    statusBySymbol.put(symbol, "already_up_to_date");
                    continue;
                }

                SeriesFetchResult fetchResult = alphaVantageDailySeriesClient.fetchDailyCloseSeries(symbol);
                Map<LocalDate, BigDecimal> dailySeries = fetchResult.series();
                if (dailySeries.isEmpty()) {
                    storedBySymbol.put(symbol, 0);
                    statusBySymbol.put(symbol, mapFetchStatus(fetchResult.status()));
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

                int inserted = saveIgnoringDuplicates(symbol, toSave);
                storedBySymbol.put(symbol, inserted);
                statusBySymbol.put(symbol, inserted == 0 ? "no_new_rows" : "stored");
                pricesStored += inserted;
            }
        }

        return new PriceSyncResult(
                normalizedSymbols.size(),
                symbolsWithPurchases,
                pricesStored,
                storedBySymbol,
                statusBySymbol,
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
            normalized.add(SymbolNormalizer.normalize(stock));
        }

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("stocks is required and must contain at least one non-blank symbol.");
        }
        return new ArrayList<>(normalized);
    }

    private Map<String, LocalDate> firstBuyDatesBySymbol(List<String> symbols) {
        Set<String> lookupSymbols = new LinkedHashSet<>();
        for (String symbol : symbols) {
            lookupSymbols.addAll(SymbolNormalizer.lookupCandidatesForCanonical(symbol));
        }
        List<TradeTransactionEntity> buys = tradeTransactionRepository
                .findBySymbolInAndTypeOrderByTradeDateAscIdAsc(new ArrayList<>(lookupSymbols), TransactionType.BUY);
        Map<String, LocalDate> firstBySymbol = new LinkedHashMap<>();
        for (TradeTransactionEntity buy : buys) {
            String canonicalSymbol = SymbolNormalizer.normalize(buy.getSymbol());
            firstBySymbol.putIfAbsent(canonicalSymbol, buy.getTradeDate());
        }
        return firstBySymbol;
    }

    private String mapFetchStatus(SeriesFetchStatus status) {
        return switch (status) {
            case RATE_LIMITED -> "rate_limited";
            case INVALID_SYMBOL -> "invalid_symbol";
            case API_ERROR -> "api_error";
            case NO_DATA -> "no_data";
            case SUCCESS -> "no_data";
        };
    }

    private int saveIgnoringDuplicates(String symbol, List<DailyClosePriceEntity> toSave) {
        if (toSave.isEmpty()) {
            return 0;
        }

        try {
            dailyClosePriceRepository.saveAll(toSave);
            log.debug("Stored {} new close prices for {}.", toSave.size(), symbol);
            return toSave.size();
        } catch (DataIntegrityViolationException batchFailure) {
            int inserted = 0;
            for (DailyClosePriceEntity entity : toSave) {
                try {
                    dailyClosePriceRepository.saveAndFlush(entity);
                    inserted++;
                } catch (DataIntegrityViolationException duplicate) {
                    log.debug("Skipping duplicate close price row for {} on {}.", symbol, entity.getPriceDate());
                }
            }
            log.debug("Stored {} new close prices for {} after duplicate filtering.", inserted, symbol);
            return inserted;
        }
    }
}
