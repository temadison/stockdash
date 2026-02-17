package com.temadison.stockdash.backend.repository;

import com.temadison.stockdash.backend.domain.DailyClosePriceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyClosePriceRepository extends JpaRepository<DailyClosePriceEntity, Long> {
    List<DailyClosePriceEntity> findBySymbolAndPriceDateAfter(String symbol, LocalDate priceDate);

    List<DailyClosePriceEntity> findBySymbolOrderByPriceDateAsc(String symbol);
    List<DailyClosePriceEntity> findBySymbolOrderByPriceDateDesc(String symbol);
    List<DailyClosePriceEntity> findBySymbolAndPriceDateGreaterThanEqualAndPriceDateLessThanEqualOrderByPriceDateDesc(
            String symbol,
            LocalDate startDate,
            LocalDate endDate
    );
    List<DailyClosePriceEntity> findBySymbolAndPriceDateGreaterThanEqualOrderByPriceDateDesc(String symbol, LocalDate startDate);
    List<DailyClosePriceEntity> findBySymbolAndPriceDateLessThanEqualOrderByPriceDateDesc(String symbol, LocalDate endDate);

    Optional<DailyClosePriceEntity> findTopBySymbolOrderByPriceDateDesc(String symbol);

    Optional<DailyClosePriceEntity> findTopBySymbolAndPriceDateLessThanEqualOrderByPriceDateDesc(String symbol, LocalDate priceDate);
}
