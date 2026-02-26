package com.temadison.stockdash.backend.api;

import com.temadison.stockdash.backend.api.dto.CsvUploadResultDto;
import com.temadison.stockdash.backend.api.dto.DailyClosePricePointDto;
import com.temadison.stockdash.backend.api.dto.PortfolioPerformancePointDto;
import com.temadison.stockdash.backend.api.dto.PortfolioSnapshotDto;
import com.temadison.stockdash.backend.api.dto.PriceSyncRequestDto;
import com.temadison.stockdash.backend.api.dto.PriceSyncResultDto;
import com.temadison.stockdash.backend.api.mapper.PortfolioApiMapper;
import com.temadison.stockdash.backend.service.CsvImportService;
import com.temadison.stockdash.backend.service.PortfolioPerformanceQueryService;
import com.temadison.stockdash.backend.service.PortfolioSummaryQueryService;
import com.temadison.stockdash.backend.service.PortfolioSymbolQueryService;
import com.temadison.stockdash.backend.service.PriceHistoryService;
import com.temadison.stockdash.backend.service.PriceSyncService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@RestController
@Validated
@RequestMapping("/api/portfolio")
public class PortfolioController {

    private final PortfolioSummaryQueryService portfolioSummaryService;
    private final CsvImportService csvTransactionImportService;
    private final PriceSyncService dailyClosePriceSyncService;
    private final PriceHistoryService dailyClosePriceQueryService;
    private final PortfolioPerformanceQueryService portfolioPerformanceService;
    private final PortfolioSymbolQueryService portfolioSymbolService;
    private final PortfolioApiMapper portfolioApiMapper;

    public PortfolioController(
            PortfolioSummaryQueryService portfolioSummaryService,
            CsvImportService csvTransactionImportService,
            PriceSyncService dailyClosePriceSyncService,
            PriceHistoryService dailyClosePriceQueryService,
            PortfolioPerformanceQueryService portfolioPerformanceService,
            PortfolioSymbolQueryService portfolioSymbolService,
            PortfolioApiMapper portfolioApiMapper
    ) {
        this.portfolioSummaryService = portfolioSummaryService;
        this.csvTransactionImportService = csvTransactionImportService;
        this.dailyClosePriceSyncService = dailyClosePriceSyncService;
        this.dailyClosePriceQueryService = dailyClosePriceQueryService;
        this.portfolioPerformanceService = portfolioPerformanceService;
        this.portfolioSymbolService = portfolioSymbolService;
        this.portfolioApiMapper = portfolioApiMapper;
    }

    @GetMapping("/daily-summary")
    public List<PortfolioSnapshotDto> dailySummary(
            @RequestParam(name = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date
    ) {
        LocalDate asOfDate = date == null ? LocalDate.now() : date;
        return portfolioApiMapper.toSnapshotDtos(portfolioSummaryService.getDailySummary(asOfDate));
    }

    @PostMapping("/transactions/upload")
    public CsvUploadResultDto uploadTransactions(@RequestParam("file") MultipartFile file) {
        return portfolioApiMapper.toDto(csvTransactionImportService.importCsv(file));
    }

    @PostMapping("/prices/sync")
    public PriceSyncResultDto syncDailyClosePrices(@Valid @RequestBody PriceSyncRequestDto request) {
        return portfolioApiMapper.toDto(dailyClosePriceSyncService.syncForStocks(request.stocks()));
    }

    @GetMapping("/symbols")
    public List<String> symbols() {
        return portfolioSymbolService.symbols();
    }

    @GetMapping("/prices/history")
    public List<DailyClosePricePointDto> dailyClosePriceHistory(
            @RequestParam("symbol") @NotBlank(message = "symbol is required.") String symbol,
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return portfolioApiMapper.toHistoryDtos(dailyClosePriceQueryService.history(symbol, startDate, endDate));
    }

    @GetMapping("/performance")
    public List<PortfolioPerformancePointDto> performance(
            @RequestParam(name = "account", required = false) String account,
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return portfolioApiMapper.toPerformanceDtos(portfolioPerformanceService.performance(account, startDate, endDate));
    }
}
