package com.temadison.stockdash.backend.service.support;

import com.temadison.stockdash.backend.domain.TradeTransactionEntity;
import com.temadison.stockdash.backend.domain.TransactionType;

import java.math.BigDecimal;

public final class PositionAccumulator {

    private BigDecimal netQuantity = BigDecimal.ZERO;
    private BigDecimal totalFees = BigDecimal.ZERO;
    private BigDecimal lastKnownPrice = BigDecimal.ZERO;

    public void apply(TradeTransactionEntity transaction) {
        BigDecimal quantity = transaction.getQuantity();
        BigDecimal signedQuantity = transaction.getType() == TransactionType.BUY ? quantity : quantity.negate();
        netQuantity = netQuantity.add(signedQuantity);
        totalFees = totalFees.add(transaction.getFee());
        lastKnownPrice = transaction.getPrice();
    }

    public BigDecimal netQuantity() {
        return netQuantity;
    }

    public BigDecimal totalFees() {
        return totalFees;
    }

    public BigDecimal lastKnownPrice() {
        return lastKnownPrice;
    }
}
