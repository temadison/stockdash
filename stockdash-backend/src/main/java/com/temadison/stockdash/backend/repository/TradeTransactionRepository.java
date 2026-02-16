package com.temadison.stockdash.backend.repository;

import com.temadison.stockdash.backend.domain.TradeTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeTransactionRepository extends JpaRepository<TradeTransactionEntity, Long> {
}
