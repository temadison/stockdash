package com.temadison.stockdash.backend.api;

import com.temadison.stockdash.backend.model.PortfolioSnapshot;
import com.temadison.stockdash.backend.model.CsvUploadResult;
import com.temadison.stockdash.backend.service.CsvTransactionImportService;
import com.temadison.stockdash.backend.service.PortfolioSummaryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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

    public PortfolioController(
            PortfolioSummaryService portfolioSummaryService,
            CsvTransactionImportService csvTransactionImportService
    ) {
        this.portfolioSummaryService = portfolioSummaryService;
        this.csvTransactionImportService = csvTransactionImportService;
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
}
