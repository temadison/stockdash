package com.temadison.stockdash.backend.service.support;

import com.temadison.stockdash.backend.domain.TradeTransactionEntity;
import com.temadison.stockdash.backend.domain.TransactionType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

public final class PositionAccumulator {

    private BigDecimal netQuantity = BigDecimal.ZERO;
    private BigDecimal totalFees = BigDecimal.ZERO;
    private BigDecimal lastKnownPrice = BigDecimal.ZERO;
    private BigDecimal costBasis = BigDecimal.ZERO;
    private LocalDate firstAcquiredDate;

    public void apply(TradeTransactionEntity transaction) {
        apply(transaction, BigDecimal.ONE);
    }

    public void apply(TradeTransactionEntity transaction, BigDecimal quantityFactor) {
        if (!transaction.getType().isSecurityTrade()) {
            return;
        }
        BigDecimal factor = quantityFactor == null ? BigDecimal.ONE : quantityFactor;
        BigDecimal quantity = transaction.getQuantity().multiply(factor);
        if (transaction.getType() == TransactionType.BUY) {
            if (netQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                firstAcquiredDate = transaction.getTradeDate();
            }
            netQuantity = netQuantity.add(quantity);
            costBasis = costBasis.add(transaction.getQuantity().multiply(transaction.getPrice()).add(transaction.getFee()));
        } else {
            BigDecimal quantityBeforeSell = netQuantity;
            netQuantity = netQuantity.subtract(quantity);
            if (quantityBeforeSell.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal soldRatio = quantity.divide(quantityBeforeSell, 12, RoundingMode.HALF_UP);
                costBasis = costBasis.subtract(costBasis.multiply(soldRatio));
            }
            if (netQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                costBasis = BigDecimal.ZERO;
                firstAcquiredDate = null;
            }
        }
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

    public BigDecimal costBasis() {
        return costBasis;
    }

    public LocalDate firstAcquiredDate() {
        return firstAcquiredDate;
    }
}
