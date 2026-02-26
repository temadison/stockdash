package com.temadison.stockdash.backend.service;

import com.temadison.stockdash.backend.model.PriceSyncResult;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
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
        JobRunRecorder jobRunRecorder = mock(JobRunRecorder.class);
        when(portfolioSymbolService.symbols()).thenReturn(List.of());
        when(jobRunRecorder.start("scheduled_price_sync", "symbolsFound=0")).thenReturn(1L);

        ScheduledPriceSyncJob job = new ScheduledPriceSyncJob(portfolioSymbolService, dailyClosePriceSyncService, jobRunRecorder);
        job.runScheduledSync();

        verify(dailyClosePriceSyncService, never()).syncForStocks(anyList());
        verify(jobRunRecorder, times(1)).success(1L, 0, 0, 0, 0, "no symbols");
    }

    @Test
    void runsSyncWhenSymbolsExist() {
        PortfolioSymbolQueryService portfolioSymbolService = mock(PortfolioSymbolQueryService.class);
        PriceSyncService dailyClosePriceSyncService = mock(PriceSyncService.class);
        JobRunRecorder jobRunRecorder = mock(JobRunRecorder.class);
        when(portfolioSymbolService.symbols()).thenReturn(List.of("AAPL", "MSFT"));
        when(jobRunRecorder.start("scheduled_price_sync", "symbolsFound=2")).thenReturn(2L);
        when(dailyClosePriceSyncService.syncForStocks(List.of("AAPL", "MSFT")))
                .thenReturn(new PriceSyncResult(2, 2, 5, Map.of("AAPL", 3, "MSFT", 2), Map.of(), List.of()));

        ScheduledPriceSyncJob job = new ScheduledPriceSyncJob(portfolioSymbolService, dailyClosePriceSyncService, jobRunRecorder);
        job.runScheduledSync();

        verify(dailyClosePriceSyncService, times(1)).syncForStocks(List.of("AAPL", "MSFT"));
        verify(jobRunRecorder, times(1)).success(2L, 2, 5, 0, 0, "scheduled sync complete");
    }

    @Test
    void skipsRunWhenAnotherSyncIsInProgress() throws Exception {
        PortfolioSymbolQueryService portfolioSymbolService = mock(PortfolioSymbolQueryService.class);
        PriceSyncService dailyClosePriceSyncService = mock(PriceSyncService.class);
        JobRunRecorder jobRunRecorder = mock(JobRunRecorder.class);
        ScheduledPriceSyncJob job = new ScheduledPriceSyncJob(portfolioSymbolService, dailyClosePriceSyncService, jobRunRecorder);

        Field field = ScheduledPriceSyncJob.class.getDeclaredField("syncInProgress");
        field.setAccessible(true);
        AtomicBoolean inProgress = (AtomicBoolean) field.get(job);
        inProgress.set(true);

        job.runScheduledSync();

        verify(portfolioSymbolService, never()).symbols();
        verify(dailyClosePriceSyncService, never()).syncForStocks(anyList());
        verify(jobRunRecorder, never()).start(anyString(), anyString());
    }
}
