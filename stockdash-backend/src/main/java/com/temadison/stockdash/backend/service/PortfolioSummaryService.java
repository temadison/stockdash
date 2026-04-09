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
            account.apply(transaction);
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
                        return new PositionValue(entry.getKey(), acc.netQuantity().longValueExact(), closePrice, positionValue);
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

    private static final class AccountSummaryAccumulator {
        private final boolean tracksCash;
        private final Map<String, PositionAccumulator> positions = new HashMap<>();
        private BigDecimal cashBalance = BigDecimal.ZERO;

        private AccountSummaryAccumulator(boolean tracksCash) {
            this.tracksCash = tracksCash;
        }

        private void apply(TradeTransactionEntity transaction) {
            if (transaction.getType().isSecurityTrade()) {
                PositionAccumulator accumulator = positions.computeIfAbsent(transaction.getSymbol(), ignored -> new PositionAccumulator());
                accumulator.apply(transaction);
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

}
