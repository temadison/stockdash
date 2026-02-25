package com.temadison.stockdash.backend.service;

import com.temadison.stockdash.backend.model.PriceSyncResult;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScheduledPriceSyncJobTest {

    @Test
    void doesNotRunSyncWhenNoSymbolsFound() {
        PortfolioSymbolQueryService portfolioSymbolService = mock(PortfolioSymbolQueryService.class);
        PriceSyncService dailyClosePriceSyncService = mock(PriceSyncService.class);
        when(portfolioSymbolService.symbols()).thenReturn(List.of());

        ScheduledPriceSyncJob job = new ScheduledPriceSyncJob(portfolioSymbolService, dailyClosePriceSyncService);
        job.runScheduledSync();

        verify(dailyClosePriceSyncService, never()).syncForStocks(anyList());
    }

    @Test
    void runsSyncWhenSymbolsExist() {
        PortfolioSymbolQueryService portfolioSymbolService = mock(PortfolioSymbolQueryService.class);
        PriceSyncService dailyClosePriceSyncService = mock(PriceSyncService.class);
        when(portfolioSymbolService.symbols()).thenReturn(List.of("AAPL", "MSFT"));
        when(dailyClosePriceSyncService.syncForStocks(List.of("AAPL", "MSFT")))
                .thenReturn(new PriceSyncResult(2, 2, 5, Map.of("AAPL", 3, "MSFT", 2), Map.of(), List.of()));

        ScheduledPriceSyncJob job = new ScheduledPriceSyncJob(portfolioSymbolService, dailyClosePriceSyncService);
        job.runScheduledSync();

        verify(dailyClosePriceSyncService, times(1)).syncForStocks(List.of("AAPL", "MSFT"));
    }

    @Test
    void skipsRunWhenAnotherSyncIsInProgress() throws Exception {
        PortfolioSymbolQueryService portfolioSymbolService = mock(PortfolioSymbolQueryService.class);
        PriceSyncService dailyClosePriceSyncService = mock(PriceSyncService.class);
        ScheduledPriceSyncJob job = new ScheduledPriceSyncJob(portfolioSymbolService, dailyClosePriceSyncService);

        Field field = ScheduledPriceSyncJob.class.getDeclaredField("syncInProgress");
        field.setAccessible(true);
        AtomicBoolean inProgress = (AtomicBoolean) field.get(job);
        inProgress.set(true);

        job.runScheduledSync();

        verify(portfolioSymbolService, never()).symbols();
        verify(dailyClosePriceSyncService, never()).syncForStocks(anyList());
    }
}
