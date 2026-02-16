package com.temadison.stockdash.backend.api;

import com.temadison.stockdash.backend.repository.AccountRepository;
import com.temadison.stockdash.backend.repository.TradeTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PortfolioControllerUploadTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TradeTransactionRepository tradeTransactionRepository;

    @Autowired
    private AccountRepository accountRepository;

    @BeforeEach
    void setUp() {
        tradeTransactionRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void uploadTransactions_returnsSummaryAndPersistsData() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "transactions.csv",
                "text/csv",
                (
                        "trade_date,account,symbol,type,quantity,price,fee\n" +
                        "2026-02-14,IRA,AAPL,BUY,10,185.10,1.00\n" +
                        "2026-02-15,IRA,MSFT,SELL,5,420.00,0.75\n"
                ).getBytes()
        );

        mockMvc.perform(multipart("/api/portfolio/transactions/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedCount").value(2))
                .andExpect(jsonPath("$.accountsAffected[0]").value("IRA"));

        assertThat(tradeTransactionRepository.count()).isEqualTo(2);
        assertThat(accountRepository.count()).isEqualTo(1);
    }

    @Test
    void uploadTransactions_returnsBadRequestForInvalidType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "transactions.csv",
                "text/csv",
                (
                        "trade_date,account,symbol,type,quantity,price,fee\n" +
                        "2026-02-14,IRA,AAPL,HOLD,10,185.10,1.00\n"
                ).getBytes()
        );

        mockMvc.perform(multipart("/api/portfolio/transactions/upload").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("type must be BUY or SELL")));
    }
}
