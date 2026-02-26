package com.temadison.stockdash.backend.api.mapper;

import com.temadison.stockdash.backend.api.dto.CsvUploadResultDto;
import com.temadison.stockdash.backend.api.dto.DailyClosePricePointDto;
import com.temadison.stockdash.backend.api.dto.PortfolioPerformancePointDto;
import com.temadison.stockdash.backend.api.dto.PortfolioSnapshotDto;
import com.temadison.stockdash.backend.api.dto.PositionValueDto;
import com.temadison.stockdash.backend.api.dto.PriceSyncResultDto;
import com.temadison.stockdash.backend.api.dto.StockPerformanceValueDto;
import com.temadison.stockdash.backend.model.CsvUploadResult;
import com.temadison.stockdash.backend.model.DailyClosePricePoint;
import com.temadison.stockdash.backend.model.PortfolioPerformancePoint;
import com.temadison.stockdash.backend.model.PortfolioSnapshot;
import com.temadison.stockdash.backend.model.PositionValue;
import com.temadison.stockdash.backend.model.PriceSyncResult;
import com.temadison.stockdash.backend.model.StockPerformanceValue;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PortfolioApiMapper {

    public CsvUploadResultDto toDto(CsvUploadResult source) {
        return new CsvUploadResultDto(source.importedCount(), source.skippedCount(), source.accountsAffected());
    }

    public PriceSyncResultDto toDto(PriceSyncResult source) {
        return new PriceSyncResultDto(
                source.symbolsRequested(),
                source.symbolsWithPurchases(),
                source.pricesStored(),
                source.storedBySymbol(),
                source.statusBySymbol(),
                source.skippedSymbols()
        );
    }

    public List<PortfolioSnapshotDto> toSnapshotDtos(List<PortfolioSnapshot> source) {
        return source.stream().map(this::toDto).toList();
    }

    public List<DailyClosePricePointDto> toHistoryDtos(List<DailyClosePricePoint> source) {
        return source.stream().map(this::toDto).toList();
    }

    public List<PortfolioPerformancePointDto> toPerformanceDtos(List<PortfolioPerformancePoint> source) {
        return source.stream().map(this::toDto).toList();
    }

    private PortfolioSnapshotDto toDto(PortfolioSnapshot source) {
        return new PortfolioSnapshotDto(
                source.accountName(),
                source.asOfDate(),
                source.totalValue(),
                source.positions().stream().map(this::toDto).toList()
        );
    }

    private PositionValueDto toDto(PositionValue source) {
        return new PositionValueDto(source.symbol(), source.quantity(), source.currentPrice(), source.marketValue());
    }

    private DailyClosePricePointDto toDto(DailyClosePricePoint source) {
        return new DailyClosePricePointDto(source.date(), source.closePrice());
    }

    private PortfolioPerformancePointDto toDto(PortfolioPerformancePoint source) {
        return new PortfolioPerformancePointDto(
                source.date(),
                source.totalValue(),
                source.stocks().stream().map(this::toDto).toList()
        );
    }

    private StockPerformanceValueDto toDto(StockPerformanceValue source) {
        return new StockPerformanceValueDto(source.symbol(), source.marketValue());
    }
}
