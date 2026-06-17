package com.temadison.stockdash.backend.repository;

import com.temadison.stockdash.backend.domain.StockSplitEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StockSplitRepository extends JpaRepository<StockSplitEntity, Long> {
    List<StockSplitEntity> findBySymbolInAndSplitDateLessThanEqualOrderBySymbolAscSplitDateAsc(
            Collection<String> symbols,
            LocalDate splitDate
    );

    List<StockSplitEntity> findBySymbolAndSplitDateLessThanEqualOrderBySplitDateAsc(String symbol, LocalDate splitDate);

    Optional<StockSplitEntity> findBySymbolAndSplitDate(String symbol, LocalDate splitDate);
}
