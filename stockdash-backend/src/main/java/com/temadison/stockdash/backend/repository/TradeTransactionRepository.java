package com.temadison.stockdash.backend.repository;

import com.temadison.stockdash.backend.domain.AccountEntity;
import com.temadison.stockdash.backend.domain.TradeTransactionEntity;
import com.temadison.stockdash.backend.domain.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface TradeTransactionRepository extends JpaRepository<TradeTransactionEntity, Long> {
    boolean existsByAccountAndTradeDateAndSymbolAndTypeAndQuantityAndPriceAndFee(
            AccountEntity account,
            LocalDate tradeDate,
            String symbol,
            TransactionType type,
            BigDecimal quantity,
            BigDecimal price,
            BigDecimal fee
    );

    List<TradeTransactionEntity> findByTradeDateLessThanEqualOrderByTradeDateAscIdAsc(LocalDate tradeDate);
}
