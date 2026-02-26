package com.temadison.stockdash.backend.service;

import com.temadison.stockdash.backend.model.PriceSyncResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(prefix = "stockdash.pricing.sync", name = "enabled", havingValue = "true")
public class ScheduledPriceSyncJob {

    private static final Logger log = LoggerFactory.getLogger(ScheduledPriceSyncJob.class);

    private final PortfolioSymbolQueryService portfolioSymbolService;
    private final PriceSyncService dailyClosePriceSyncService;
    private final JobRunRecorder jobRunRecorder;
    private final AtomicBoolean syncInProgress = new AtomicBoolean(false);

    public ScheduledPriceSyncJob(
            PortfolioSymbolQueryService portfolioSymbolService,
            PriceSyncService dailyClosePriceSyncService,
            JobRunRecorder jobRunRecorder
    ) {
        this.portfolioSymbolService = portfolioSymbolService;
        this.dailyClosePriceSyncService = dailyClosePriceSyncService;
        this.jobRunRecorder = jobRunRecorder;
    }

    @Scheduled(
            cron = "${stockdash.pricing.sync.cron:0 0 22 * * MON-FRI}",
            zone = "${stockdash.pricing.sync.zone:UTC}"
    )
    public void runScheduledSync() {
        if (!syncInProgress.compareAndSet(false, true)) {
            log.warn("Skipping scheduled price sync because a prior run is still in progress.");
            return;
        }

        Long runId = null;
        try {
            List<String> symbols = portfolioSymbolService.symbols();
            runId = jobRunRecorder.start("scheduled_price_sync", "symbolsFound=" + symbols.size());
            if (symbols.isEmpty()) {
                log.info("Skipping scheduled price sync because no portfolio symbols were found.");
                jobRunRecorder.success(runId, 0, 0, 0, 0, "no symbols");
                return;
            }

            PriceSyncResult result = dailyClosePriceSyncService.syncForStocks(symbols);
            int failed = (int) result.statusBySymbol().values().stream()
                    .filter(status -> "rate_limited".equals(status)
                            || "circuit_open".equals(status)
                            || "api_error".equals(status)
                            || "invalid_symbol".equals(status))
                    .count();
            log.info(
                    "Scheduled price sync complete. requested={}, withPurchases={}, stored={}, skipped={}",
                    result.symbolsRequested(),
                    result.symbolsWithPurchases(),
                    result.pricesStored(),
                    result.skippedSymbols().size()
            );
            jobRunRecorder.success(
                    runId,
                    result.symbolsRequested(),
                    result.pricesStored(),
                    failed,
                    result.skippedSymbols().size(),
                    "scheduled sync complete"
            );
        } catch (Exception ex) {
            log.error("Scheduled price sync failed.", ex);
            if (runId == null) {
                runId = jobRunRecorder.start("scheduled_price_sync", "failed_before_sync");
            }
            jobRunRecorder.fail(runId, 0, 0, 1, 0, ex, "scheduled sync failed");
        } finally {
            syncInProgress.set(false);
        }
    }
}
