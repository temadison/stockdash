package com.temadison.stockdash.backend.service.support;

import com.temadison.stockdash.backend.domain.TradeTransactionEntity;
import com.temadison.stockdash.backend.domain.TransactionType;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PositionAccumulator {

    private BigDecimal netQuantity = BigDecimal.ZERO;
    private BigDecimal totalFees = BigDecimal.ZERO;
    private BigDecimal lastKnownPrice = BigDecimal.ZERO;

    public void apply(TradeTransactionEntity transaction) {
        apply(transaction, BigDecimal.ONE);
    }

    public void apply(TradeTransactionEntity transaction, BigDecimal quantityFactor) {
        if (!transaction.getType().isSecurityTrade()) {
            return;
        }
        BigDecimal factor = quantityFactor == null ? BigDecimal.ONE : quantityFactor;
        BigDecimal quantity = transaction.getQuantity().multiply(factor);
        BigDecimal signedQuantity = transaction.getType() == TransactionType.BUY ? quantity : quantity.negate();
        netQuantity = netQuantity.add(signedQuantity);
        totalFees = totalFees.add(transaction.getFee());
        lastKnownPrice = adjustedPrice(transaction.getPrice(), factor);
    }

    public void applySplit(BigDecimal splitRatio) {
        if (splitRatio == null || splitRatio.compareTo(BigDecimal.ONE) == 0) {
            return;
        }
        netQuantity = netQuantity.multiply(splitRatio);
        if (lastKnownPrice.compareTo(BigDecimal.ZERO) != 0) {
            lastKnownPrice = adjustedPrice(lastKnownPrice, splitRatio);
        }
    }

    private BigDecimal adjustedPrice(BigDecimal price, BigDecimal factor) {
        if (factor.compareTo(BigDecimal.ONE) == 0) {
            return price;
        }
        return price.divide(factor, 6, RoundingMode.HALF_UP);
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
