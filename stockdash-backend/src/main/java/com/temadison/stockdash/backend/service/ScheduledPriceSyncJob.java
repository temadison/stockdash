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
    private final AtomicBoolean syncInProgress = new AtomicBoolean(false);

    public ScheduledPriceSyncJob(
            PortfolioSymbolQueryService portfolioSymbolService,
            PriceSyncService dailyClosePriceSyncService
    ) {
        this.portfolioSymbolService = portfolioSymbolService;
        this.dailyClosePriceSyncService = dailyClosePriceSyncService;
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

        try {
            List<String> symbols = portfolioSymbolService.symbols();
            if (symbols.isEmpty()) {
                log.info("Skipping scheduled price sync because no portfolio symbols were found.");
                return;
            }

            PriceSyncResult result = dailyClosePriceSyncService.syncForStocks(symbols);
            log.info(
                    "Scheduled price sync complete. requested={}, withPurchases={}, stored={}, skipped={}",
                    result.symbolsRequested(),
                    result.symbolsWithPurchases(),
                    result.pricesStored(),
                    result.skippedSymbols().size()
            );
        } catch (Exception ex) {
            log.error("Scheduled price sync failed.", ex);
        } finally {
            syncInProgress.set(false);
        }
    }
}
