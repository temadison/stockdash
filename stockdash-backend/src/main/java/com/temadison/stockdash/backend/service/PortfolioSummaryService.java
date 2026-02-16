package com.temadison.stockdash.backend.service;

import com.temadison.stockdash.backend.domain.TradeTransactionEntity;
import com.temadison.stockdash.backend.domain.TransactionType;
import com.temadison.stockdash.backend.model.PortfolioSnapshot;
import com.temadison.stockdash.backend.model.PositionValue;
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
public class PortfolioSummaryService {

    private final TradeTransactionRepository tradeTransactionRepository;

    public PortfolioSummaryService(TradeTransactionRepository tradeTransactionRepository) {
        this.tradeTransactionRepository = tradeTransactionRepository;
    }

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

            BigDecimal quantity = transaction.getQuantity();
            BigDecimal signedQuantity = transaction.getType() == TransactionType.BUY ? quantity : quantity.negate();
            accumulator.netQuantity = accumulator.netQuantity.add(signedQuantity);
            accumulator.lastKnownPrice = transaction.getPrice();
            accumulator.totalFees = accumulator.totalFees.add(transaction.getFee());
        }

        List<PortfolioSnapshot> snapshots = new ArrayList<>();
        for (Map.Entry<String, Map<String, PositionAccumulator>> accountEntry : byAccount.entrySet()) {
            List<PositionValue> positions = accountEntry.getValue().entrySet().stream()
                    .map(entry -> {
                        PositionAccumulator acc = entry.getValue();
                        if (acc.netQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                            return null;
                        }
                        BigDecimal positionValue = acc.netQuantity
                                .multiply(acc.lastKnownPrice)
                                .subtract(acc.totalFees)
                                .setScale(2, RoundingMode.HALF_UP);
                        return new PositionValue(entry.getKey(), positionValue);
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

    private static class PositionAccumulator {
        private BigDecimal netQuantity = BigDecimal.ZERO;
        private BigDecimal lastKnownPrice = BigDecimal.ZERO;
        private BigDecimal totalFees = BigDecimal.ZERO;
    }
}
