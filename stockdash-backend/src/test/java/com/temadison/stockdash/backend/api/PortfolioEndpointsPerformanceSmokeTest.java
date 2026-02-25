package com.temadison.stockdash.backend.api;

import com.temadison.stockdash.backend.repository.AccountRepository;
import com.temadison.stockdash.backend.repository.DailyClosePriceRepository;
import com.temadison.stockdash.backend.repository.TradeTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "stockdash.pricing.alpha-vantage-api-key=")
@AutoConfigureMockMvc
class PortfolioEndpointsPerformanceSmokeTest {

    private static final Duration SUMMARY_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration PERFORMANCE_TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TradeTransactionRepository tradeTransactionRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private DailyClosePriceRepository dailyClosePriceRepository;

    @BeforeEach
    void setUp() throws Exception {
        dailyClosePriceRepository.deleteAll();
        tradeTransactionRepository.deleteAll();
        accountRepository.deleteAll();

        byte[] csvBytes = new ClassPathResource("sample-transactions.csv").getInputStream().readAllBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample-transactions.csv",
                "text/csv",
                csvBytes
        );
        mockMvc.perform(multipart("/api/portfolio/transactions/upload").file(file))
                .andExpect(status().isOk());
    }

    @Test
    void dailySummary_endpointRespondsWithinSmokeThreshold() {
        assertTimeout(SUMMARY_TIMEOUT, () ->
                mockMvc.perform(get("/api/portfolio/daily-summary")
                                .param("date", "2026-02-16"))
                        .andExpect(status().isOk())
        );
    }

    @Test
    void performance_endpointRespondsWithinSmokeThreshold() {
        assertTimeout(PERFORMANCE_TIMEOUT, () ->
                mockMvc.perform(get("/api/portfolio/performance")
                                .param("startDate", "2026-01-01")
                                .param("endDate", "2026-02-16"))
                        .andExpect(status().isOk())
        );
    }
}
