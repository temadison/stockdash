package com.temadison.stockdash.backend.api;

import com.temadison.stockdash.backend.model.PortfolioSnapshot;
import com.temadison.stockdash.backend.model.CsvUploadResult;
import com.temadison.stockdash.backend.model.DailyClosePricePoint;
import com.temadison.stockdash.backend.model.PortfolioPerformancePoint;
import com.temadison.stockdash.backend.model.PriceSyncRequest;
import com.temadison.stockdash.backend.model.PriceSyncResult;
import com.temadison.stockdash.backend.service.CsvTransactionImportService;
import com.temadison.stockdash.backend.service.DailyClosePriceQueryService;
import com.temadison.stockdash.backend.service.DailyClosePriceSyncService;
import com.temadison.stockdash.backend.service.PortfolioPerformanceService;
import com.temadison.stockdash.backend.service.PortfolioSummaryService;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/api/portfolio")
public class PortfolioController {

    private final PortfolioSummaryService portfolioSummaryService;
    private final CsvTransactionImportService csvTransactionImportService;
    private final DailyClosePriceSyncService dailyClosePriceSyncService;
    private final DailyClosePriceQueryService dailyClosePriceQueryService;
    private final PortfolioPerformanceService portfolioPerformanceService;

    public PortfolioController(
            PortfolioSummaryService portfolioSummaryService,
            CsvTransactionImportService csvTransactionImportService,
            DailyClosePriceSyncService dailyClosePriceSyncService,
            DailyClosePriceQueryService dailyClosePriceQueryService,
            PortfolioPerformanceService portfolioPerformanceService
    ) {
        this.portfolioSummaryService = portfolioSummaryService;
        this.csvTransactionImportService = csvTransactionImportService;
        this.dailyClosePriceSyncService = dailyClosePriceSyncService;
        this.dailyClosePriceQueryService = dailyClosePriceQueryService;
        this.portfolioPerformanceService = portfolioPerformanceService;
    }

    @GetMapping("/daily-summary")
    public List<PortfolioSnapshot> dailySummary(
            @RequestParam(name = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date
    ) {
        LocalDate asOfDate = date == null ? LocalDate.now() : date;
        return portfolioSummaryService.getDailySummary(asOfDate);
    }

    @PostMapping("/transactions/upload")
    public CsvUploadResult uploadTransactions(@RequestParam("file") MultipartFile file) {
        return csvTransactionImportService.importCsv(file);
    }

    @PostMapping("/prices/sync")
    public PriceSyncResult syncDailyClosePrices(@RequestBody PriceSyncRequest request) {
        return dailyClosePriceSyncService.syncForStocks(request.stocks());
    }

    @GetMapping("/prices/history")
    public List<DailyClosePricePoint> dailyClosePriceHistory(
            @RequestParam("symbol") String symbol,
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return dailyClosePriceQueryService.history(symbol, startDate, endDate);
    }

    @GetMapping("/performance")
    public List<PortfolioPerformancePoint> performance(
            @RequestParam(name = "account", required = false) String account,
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return portfolioPerformanceService.performance(account, startDate, endDate);
    }
}
