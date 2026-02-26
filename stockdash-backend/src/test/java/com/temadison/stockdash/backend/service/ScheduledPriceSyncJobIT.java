package com.temadison.stockdash.backend.service;

import com.temadison.stockdash.backend.domain.JobRunStatus;
import com.temadison.stockdash.backend.model.PriceSyncResult;
import com.temadison.stockdash.backend.repository.JobRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@SpringBootTest(properties = "stockdash.pricing.sync.enabled=true")
class ScheduledPriceSyncJobIT {

    @Autowired
    private ScheduledPriceSyncJob scheduledPriceSyncJob;

    @Autowired
    private JobRunRepository jobRunRepository;

    @MockitoBean
    private PortfolioSymbolQueryService portfolioSymbolQueryService;

    @MockitoBean
    private PriceSyncService priceSyncService;

    @BeforeEach
    void setUp() {
        jobRunRepository.deleteAll();
    }

    @Test
    void runScheduledSync_recordsSuccessfulRunWithCounts() {
        given(portfolioSymbolQueryService.symbols()).willReturn(List.of("AAPL", "MSFT"));
        given(priceSyncService.syncForStocks(List.of("AAPL", "MSFT")))
                .willReturn(new PriceSyncResult(
                        2,
                        2,
                        4,
                        Map.of("AAPL", 2, "MSFT", 2),
                        Map.of("AAPL", "stored", "MSFT", "stored"),
                        List.of()
                ));

        scheduledPriceSyncJob.runScheduledSync();

        assertThat(jobRunRepository.count()).isEqualTo(1);
        var run = jobRunRepository.findAll().get(0);
        assertThat(run.getStatus()).isEqualTo(JobRunStatus.SUCCESS);
        assertThat(run.getRequestedCount()).isEqualTo(2);
        assertThat(run.getProcessedCount()).isEqualTo(4);
        assertThat(run.getFailedCount()).isEqualTo(0);
        assertThat(run.getSkippedCount()).isEqualTo(0);
    }
}
