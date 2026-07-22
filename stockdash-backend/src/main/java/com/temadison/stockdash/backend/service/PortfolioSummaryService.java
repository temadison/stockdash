package com.temadison.stockdash.backend.service;

import com.temadison.stockdash.backend.domain.StockSplitEntity;
import com.temadison.stockdash.backend.domain.TradeTransactionEntity;
import com.temadison.stockdash.backend.model.PortfolioSnapshot;
import com.temadison.stockdash.backend.model.PositionValue;
import com.temadison.stockdash.backend.pricing.MarketPriceService;
import com.temadison.stockdash.backend.repository.DailyClosePriceRepository;
import com.temadison.stockdash.backend.repository.StockSplitRepository;
import com.temadison.stockdash.backend.repository.TradeTransactionRepository;
import com.temadison.stockdash.backend.service.support.PositionAccumulator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PortfolioSummaryService implements PortfolioSummaryQueryService {

    private final TradeTransactionRepository tradeTransactionRepository;
    private final DailyClosePriceRepository dailyClosePriceRepository;
    private final StockSplitRepository stockSplitRepository;
    private final MarketPriceService marketPriceService;

    public PortfolioSummaryService(
            TradeTransactionRepository tradeTransactionRepository,
            DailyClosePriceRepository dailyClosePriceRepository,
            StockSplitRepository stockSplitRepository,
            MarketPriceService marketPriceService
    ) {
        this.tradeTransactionRepository = tradeTransactionRepository;
        this.dailyClosePriceRepository = dailyClosePriceRepository;
        this.stockSplitRepository = stockSplitRepository;
        this.marketPriceService = marketPriceService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PortfolioSnapshot> getDailySummary(LocalDate asOfDate) {
        List<TradeTransactionEntity> transactions = tradeTransactionRepository
                .findByTradeDateLessThanEqualOrderByTradeDateAscIdAsc(asOfDate);
        Map<String, List<StockSplitEntity>> splitsBySymbol = splitsBySymbol(transactions, asOfDate);

        Map<String, Boolean> tracksCashByAccount = new HashMap<>();
        for (TradeTransactionEntity transaction : transactions) {
            if (transaction.getType().isExplicitCashTransaction()) {
                tracksCashByAccount.put(transaction.getAccount().getName(), true);
            }
        }

        Map<String, AccountSummaryAccumulator> byAccount = new HashMap<>();
        for (TradeTransactionEntity transaction : transactions) {
            String accountName = transaction.getAccount().getName();
            AccountSummaryAccumulator account = byAccount.computeIfAbsent(
                    accountName,
                    ignored -> new AccountSummaryAccumulator(Boolean.TRUE.equals(tracksCashByAccount.get(accountName)))
            );
            account.apply(transaction, splitsBySymbol, asOfDate);
        }

        Map<String, BigDecimal> closePriceBySymbol = new HashMap<>();
        List<PortfolioSnapshot> snapshots = new ArrayList<>();
        for (Map.Entry<String, AccountSummaryAccumulator> accountEntry : byAccount.entrySet()) {
            AccountSummaryAccumulator account = accountEntry.getValue();
            List<PositionValue> positions = account.positions().entrySet().stream()
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
                                .subtract(account.tracksCash() ? BigDecimal.ZERO : acc.totalFees())
                                .setScale(2, RoundingMode.HALF_UP);
                        BigDecimal costBasis = acc.costBasis().setScale(2, RoundingMode.HALF_UP);
                        BigDecimal gainLoss = positionValue.subtract(costBasis).setScale(2, RoundingMode.HALF_UP);
                        return new PositionValue(
                                entry.getKey(),
                                displayQuantity(acc.netQuantity()),
                                closePrice,
                                positionValue,
                                costBasis,
                                gainLoss,
                                rateOfReturn(costBasis, positionValue),
                                cagr(costBasis, positionValue, acc.firstAcquiredDate(), asOfDate)
                        );
                    })
                    .filter(position -> position != null)
                    .sorted(Comparator.comparing(PositionValue::symbol))
                    .toList();

            BigDecimal cashBalance = account.cashBalance().setScale(2, RoundingMode.HALF_UP);
            BigDecimal totalValue = positions.stream()
                    .map(PositionValue::marketValue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .add(cashBalance)
                    .setScale(2, RoundingMode.HALF_UP);

            snapshots.add(new PortfolioSnapshot(accountEntry.getKey(), asOfDate, totalValue, cashBalance, positions));
        }

        return snapshots.stream()
                .sorted(Comparator.comparing(PortfolioSnapshot::accountName))
                .toList();
    }

    private static BigDecimal grossAmount(TradeTransactionEntity transaction) {
        return transaction.getQuantity().multiply(transaction.getPrice());
    }

    private static BigDecimal rateOfReturn(BigDecimal costBasis, BigDecimal positionValue) {
        if (costBasis.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return positionValue.subtract(costBasis)
                .divide(costBasis, 8, RoundingMode.HALF_UP);
    }

    private static BigDecimal cagr(
            BigDecimal costBasis,
            BigDecimal positionValue,
            LocalDate firstAcquiredDate,
            LocalDate asOfDate
    ) {
        if (costBasis.compareTo(BigDecimal.ZERO) <= 0
                || positionValue.compareTo(BigDecimal.ZERO) <= 0
                || firstAcquiredDate == null
                || !asOfDate.isAfter(firstAcquiredDate)) {
            return null;
        }
        double years = ChronoUnit.DAYS.between(firstAcquiredDate, asOfDate) / 365.2425d;
        double annualizedReturn = Math.pow(positionValue.divide(costBasis, MathContext.DECIMAL64).doubleValue(), 1d / years) - 1d;
        return BigDecimal.valueOf(annualizedReturn).setScale(8, RoundingMode.HALF_UP);
    }

    private static final class AccountSummaryAccumulator {
        private final boolean tracksCash;
        private final Map<String, PositionAccumulator> positions = new HashMap<>();
        private BigDecimal cashBalance = BigDecimal.ZERO;

        private AccountSummaryAccumulator(boolean tracksCash) {
            this.tracksCash = tracksCash;
        }

        private void apply(
                TradeTransactionEntity transaction,
                Map<String, List<StockSplitEntity>> splitsBySymbol,
                LocalDate asOfDate
        ) {
            if (transaction.getType().isSecurityTrade()) {
                PositionAccumulator accumulator = positions.computeIfAbsent(transaction.getSymbol(), ignored -> new PositionAccumulator());
                accumulator.apply(transaction, splitFactor(transaction, splitsBySymbol, asOfDate));
            }

            if (tracksCash) {
                cashBalance = cashBalance.add(switch (transaction.getType()) {
                    case BUY -> grossAmount(transaction).add(transaction.getFee()).negate();
                    case SELL -> grossAmount(transaction).subtract(transaction.getFee());
                    case CASH_DEPOSIT, DIVIDEND, INTEREST -> grossAmount(transaction);
                    case CASH_WITHDRAWAL, CASH_FEE -> grossAmount(transaction).negate();
                });
            }
        }

        private boolean tracksCash() {
            return tracksCash;
        }

        private Map<String, PositionAccumulator> positions() {
            return positions;
        }

        private BigDecimal cashBalance() {
            return tracksCash ? cashBalance : BigDecimal.ZERO;
        }
    }

    private Map<String, List<StockSplitEntity>> splitsBySymbol(
            List<TradeTransactionEntity> transactions,
            LocalDate asOfDate
    ) {
        Set<String> symbols = transactions.stream()
                .filter(transaction -> transaction.getType().isSecurityTrade())
                .map(TradeTransactionEntity::getSymbol)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (symbols.isEmpty()) {
            return Map.of();
        }
        return stockSplitRepository.findBySymbolInAndSplitDateLessThanEqualOrderBySymbolAscSplitDateAsc(symbols, asOfDate)
                .stream()
                .collect(Collectors.groupingBy(StockSplitEntity::getSymbol));
    }

    private static BigDecimal splitFactor(
            TradeTransactionEntity transaction,
            Map<String, List<StockSplitEntity>> splitsBySymbol,
            LocalDate asOfDate
    ) {
        BigDecimal factor = BigDecimal.ONE;
        for (StockSplitEntity split : splitsBySymbol.getOrDefault(transaction.getSymbol(), List.of())) {
            if (split.getSplitDate().isAfter(transaction.getTradeDate()) && !split.getSplitDate().isAfter(asOfDate)) {
                factor = factor.multiply(split.getSplitRatio());
            }
        }
        return factor;
    }

    private static BigDecimal displayQuantity(BigDecimal quantity) {
        BigDecimal normalized = quantity.stripTrailingZeros();
        if (normalized.scale() < 0) {
            return normalized.setScale(0);
        }
        return normalized;
    }

}
