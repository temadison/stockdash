package com.temadison.stockdash.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(
        name = "stock_splits",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_stock_splits_symbol_date",
                columnNames = {"symbol", "split_date"}
        )
)
public class StockSplitEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String symbol;

    @Column(name = "split_date", nullable = false)
    private LocalDate splitDate;

    @Column(name = "split_ratio", nullable = false, precision = 19, scale = 6)
    private BigDecimal splitRatio;

    public Long getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public LocalDate getSplitDate() {
        return splitDate;
    }

    public void setSplitDate(LocalDate splitDate) {
        this.splitDate = splitDate;
    }

    public BigDecimal getSplitRatio() {
        return splitRatio;
    }

    public void setSplitRatio(BigDecimal splitRatio) {
        this.splitRatio = splitRatio;
    }
}
