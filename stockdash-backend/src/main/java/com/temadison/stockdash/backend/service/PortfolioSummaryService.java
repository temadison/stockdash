package com.temadison.stockdash.backend.service;

import com.temadison.stockdash.backend.domain.TradeTransactionEntity;
import com.temadison.stockdash.backend.model.PortfolioSnapshot;
import com.temadison.stockdash.backend.model.PositionValue;
import com.temadison.stockdash.backend.pricing.MarketPriceService;
import com.temadison.stockdash.backend.repository.DailyClosePriceRepository;
import com.temadison.stockdash.backend.repository.TradeTransactionRepository;
import com.temadison.stockdash.backend.service.support.PositionAccumulator;
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
import java.util.Optional;

@Service
public class PortfolioSummaryService implements PortfolioSummaryQueryService {

    private final TradeTransactionRepository tradeTransactionRepository;
    private final DailyClosePriceRepository dailyClosePriceRepository;
    private final MarketPriceService marketPriceService;

    public PortfolioSummaryService(
            TradeTransactionRepository tradeTransactionRepository,
            DailyClosePriceRepository dailyClosePriceRepository,
            MarketPriceService marketPriceService
    ) {
        this.tradeTransactionRepository = tradeTransactionRepository;
        this.dailyClosePriceRepository = dailyClosePriceRepository;
        this.marketPriceService = marketPriceService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PortfolioSnapshot> getDailySummary(LocalDate asOfDate) {
        List<TradeTransactionEntity> transactions = tradeTransactionRepository
                .findByTradeDateLessThanEqualOrderByTradeDateAscIdAsc(asOfDate);

        Map<String, Map<String, PositionAccumulator>> byAccount = new HashMap<>();
        for (TradeTransactionEntity transaction : transactions) {
            String accountName = transaction.getAccount().getName();
            String symbol = transaction.getSymbol();

            Map<String, PositionAccumulator> bySymbol = byAccount.computeIfAbsent(accountName, ignored -> new HashMap<>());
            PositionAccumulator accumulator = bySymbol.computeIfAbsent(symbol, ignored -> new PositionAccumulator());
            accumulator.apply(transaction);
        }

        Map<String, BigDecimal> closePriceBySymbol = new HashMap<>();
        List<PortfolioSnapshot> snapshots = new ArrayList<>();
        for (Map.Entry<String, Map<String, PositionAccumulator>> accountEntry : byAccount.entrySet()) {
            List<PositionValue> positions = accountEntry.getValue().entrySet().stream()
                    .map(entry -> {
                        PositionAccumulator acc = entry.getValue();
                        if (acc.netQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                            return null;
                        }

                        BigDecimal closePrice = closePriceBySymbol.computeIfAbsent(entry.getKey(), symbol -> {
                            Optional<BigDecimal> storedClose = dailyClosePriceRepository
                                    .findTopBySymbolAndPriceDateLessThanEqualOrderByPriceDateDesc(symbol, asOfDate)
                                    .map(price -> price.getClosePrice());
                            if (storedClose.isPresent()) {
                                return storedClose.get();
                            }
                            Optional<BigDecimal> marketClose = marketPriceService.getClosePriceOnOrBefore(symbol, asOfDate);
                            return marketClose.orElse(acc.lastKnownPrice());
                        });

                        BigDecimal positionValue = acc.netQuantity()
                                .multiply(closePrice)
                                .subtract(acc.totalFees())
                                .setScale(2, RoundingMode.HALF_UP);
                        return new PositionValue(entry.getKey(), acc.netQuantity().longValueExact(), closePrice, positionValue);
                    })
                    .filter(position -> position != null)
                    .sorted(Comparator.comparing(PositionValue::symbol))
                    .toList();

            BigDecimal totalValue = positions.stream()
                    .map(PositionValue::marketValue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);

            snapshots.add(new PortfolioSnapshot(accountEntry.getKey(), asOfDate, totalValue, positions));
        }

        return snapshots.stream()
                .sorted(Comparator.comparing(PortfolioSnapshot::accountName))
                .toList();
    }

}
