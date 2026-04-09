package com.temadison.stockdash.backend.service;

import com.temadison.stockdash.backend.domain.DailyClosePriceEntity;
import com.temadison.stockdash.backend.domain.TradeTransactionEntity;
import com.temadison.stockdash.backend.domain.TransactionType;
import com.temadison.stockdash.backend.model.PortfolioPerformancePoint;
import com.temadison.stockdash.backend.model.StockPerformanceValue;
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

@Service
public class PortfolioPerformanceService implements PortfolioPerformanceQueryService {

    private final TradeTransactionRepository tradeTransactionRepository;
    private final DailyClosePriceRepository dailyClosePriceRepository;

    public PortfolioPerformanceService(
            TradeTransactionRepository tradeTransactionRepository,
            DailyClosePriceRepository dailyClosePriceRepository
    ) {
        this.tradeTransactionRepository = tradeTransactionRepository;
        this.dailyClosePriceRepository = dailyClosePriceRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PortfolioPerformancePoint> performance(String accountName, LocalDate startDate, LocalDate endDate) {
        LocalDate resolvedEnd = endDate == null ? LocalDate.now() : endDate;
        List<TradeTransactionEntity> transactions = loadTransactions(accountName, resolvedEnd);
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
        Map<String, Boolean> tracksCashByAccount = new HashMap<>();
        for (TradeTransactionEntity transaction : transactions) {
            if (transaction.getType().isExplicitCashTransaction()) {
                tracksCashByAccount.put(transaction.getAccount().getName(), true);
            }
        }

        Map<String, Map<String, PositionAccumulator>> positionsByAccount = new HashMap<>();
        Map<String, BigDecimal> cashBalanceByAccount = new HashMap<>();
        int txIndex = 0;
        BigDecimal netAmountSpent = BigDecimal.ZERO;
        List<PortfolioPerformancePoint> points = new ArrayList<>();

        for (LocalDate day = resolvedStart; !day.isAfter(resolvedEnd); day = day.plusDays(1)) {
            while (txIndex < transactions.size() && !transactions.get(txIndex).getTradeDate().isAfter(day)) {
                TradeTransactionEntity tx = transactions.get(txIndex);
                BigDecimal gross = tx.getQuantity().multiply(tx.getPrice());
                String txAccountName = tx.getAccount().getName();
                boolean tracksCash = Boolean.TRUE.equals(tracksCashByAccount.get(txAccountName));
                if (tx.getType().isSecurityTrade()) {
                    if (!tracksCash) {
                        if (tx.getType() == TransactionType.BUY) {
                            netAmountSpent = netAmountSpent.add(gross).add(tx.getFee());
                        } else {
                            netAmountSpent = netAmountSpent.subtract(gross).add(tx.getFee());
                        }
                    }
                    Map<String, PositionAccumulator> positions = positionsByAccount.computeIfAbsent(txAccountName, ignored -> new HashMap<>());
                    PositionAccumulator acc = positions.computeIfAbsent(tx.getSymbol(), ignored -> new PositionAccumulator());
                    acc.apply(tx);
                    if (tracksCash) {
                        BigDecimal delta = tx.getType() == TransactionType.BUY
                                ? gross.add(tx.getFee()).negate()
                                : gross.subtract(tx.getFee());
                        cashBalanceByAccount.merge(txAccountName, delta, BigDecimal::add);
                    }
                } else if (tracksCash) {
                    BigDecimal cashDelta = switch (tx.getType()) {
                        case CASH_DEPOSIT -> {
                            netAmountSpent = netAmountSpent.add(gross);
                            yield gross;
                        }
                        case CASH_WITHDRAWAL -> {
                            netAmountSpent = netAmountSpent.subtract(gross);
                            yield gross.negate();
                        }
                        case DIVIDEND, INTEREST -> gross;
                        case CASH_FEE -> gross.negate();
                        default -> BigDecimal.ZERO;
                    };
                    cashBalanceByAccount.merge(txAccountName, cashDelta, BigDecimal::add);
                }
                txIndex++;
            }

            Map<String, BigDecimal> stockValueBySymbol = new HashMap<>();
            BigDecimal total = BigDecimal.ZERO;
            for (Map.Entry<String, Map<String, PositionAccumulator>> accountEntry : positionsByAccount.entrySet()) {
                boolean tracksCash = Boolean.TRUE.equals(tracksCashByAccount.get(accountEntry.getKey()));
                for (Map.Entry<String, PositionAccumulator> entry : accountEntry.getValue().entrySet()) {
                    if (entry.getValue().netQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                        continue;
                    }
                    String symbol = entry.getKey();
                    BigDecimal close = closeOnOrBefore(day, symbol, closesBySymbol, closeCursorBySymbol, latestCloseBySymbol);
                    if (close == null) {
                        close = entry.getValue().lastKnownPrice();
                    }

                    BigDecimal value = entry.getValue().netQuantity()
                            .multiply(close)
                            .subtract(tracksCash ? BigDecimal.ZERO : entry.getValue().totalFees())
                            .setScale(2, RoundingMode.HALF_UP);
                    stockValueBySymbol.merge(symbol, value, BigDecimal::add);
                    total = total.add(value);
                }
            }

            BigDecimal cashBalance = cashBalanceByAccount.values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);

            List<StockPerformanceValue> stocks = stockValueBySymbol.entrySet().stream()
                    .map(entry -> new StockPerformanceValue(entry.getKey(), entry.getValue().setScale(2, RoundingMode.HALF_UP)))
                    .sorted(Comparator.comparing(StockPerformanceValue::symbol))
                    .toList();
            points.add(new PortfolioPerformancePoint(
                    day,
                    total.add(cashBalance).setScale(2, RoundingMode.HALF_UP),
                    netAmountSpent.setScale(2, RoundingMode.HALF_UP),
                    cashBalance,
                    stocks
            ));
        }

        return points;
    }

    private List<TradeTransactionEntity> loadTransactions(String accountName, LocalDate resolvedEnd) {
        if (accountName == null || accountName.isBlank() || "TOTAL".equalsIgnoreCase(accountName)) {
            return tradeTransactionRepository.findByTradeDateLessThanEqualOrderByTradeDateAscIdAsc(resolvedEnd);
        }
        return tradeTransactionRepository
                .findByTradeDateLessThanEqualAndAccount_NameIgnoreCaseOrderByTradeDateAscIdAsc(resolvedEnd, accountName.trim());
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
}
