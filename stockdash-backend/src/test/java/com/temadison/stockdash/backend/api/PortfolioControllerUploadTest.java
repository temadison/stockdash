package com.temadison.stockdash.backend.api;

import com.temadison.stockdash.backend.repository.AccountRepository;
import com.temadison.stockdash.backend.repository.TradeTransactionRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    void uploadTransactions_returnsSummaryAndPersistsData_fromSampleCsvFile() throws Exception {
        byte[] csvBytes = readResource("sample-transactions.csv");
        SampleCsvExpectations expectations = parseExpectations(csvBytes);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample-transactions.csv",
                "text/csv",
                csvBytes
        );

        mockMvc.perform(multipart("/api/portfolio/transactions/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedCount").value(expectations.transactionCount()))
                .andExpect(jsonPath("$.skippedCount").value(0))
                .andExpect(jsonPath("$.accountsAffected", hasSize(expectations.sortedAccounts().size())))
                .andExpect(jsonPath("$.accountsAffected", containsInAnyOrder(expectations.sortedAccounts().toArray())));

        assertThat(tradeTransactionRepository.count()).isEqualTo(expectations.transactionCount());
        assertThat(accountRepository.count()).isEqualTo(expectations.sortedAccounts().size());
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
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail", containsString("type must be BUY or SELL")));
    }

    @Test
    void syncPrices_returnsValidationProblemForMissingStocks() throws Exception {
        mockMvc.perform(post("/api/portfolio/prices/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail", containsString("stocks is required")))
                .andExpect(jsonPath("$.errors", hasSize(1)));
    }

    @Test
    void symbols_returnsDistinctSymbolsFromTransactions() throws Exception {
        byte[] csvBytes = readResource("sample-transactions.csv");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample-transactions.csv",
                "text/csv",
                csvBytes
        );

        mockMvc.perform(multipart("/api/portfolio/transactions/upload").file(file))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/portfolio/symbols"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasItems("AAPL", "MSFT", "SPY")))
                .andExpect(jsonPath("$", hasSize(10)));
    }

    @Test
    void priceHistory_returnsValidationProblemForBlankSymbol() throws Exception {
        mockMvc.perform(get("/api/portfolio/prices/history")
                        .param("symbol", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.errors", hasSize(1)));
    }

    @Test
    void priceHistory_returnsBadRequestForInvalidDateRange() throws Exception {
        mockMvc.perform(get("/api/portfolio/prices/history")
                        .param("symbol", "AAPL")
                        .param("startDate", "2026-02-16")
                        .param("endDate", "2026-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail", containsString("startDate must be on or before endDate")));
    }

    @Test
    void priceHistory_returnsEmptyArrayWhenSymbolHasNoStoredHistory() throws Exception {
        mockMvc.perform(get("/api/portfolio/prices/history")
                        .param("symbol", "ZZZZ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void performance_returnsBadRequestForInvalidDateRange() throws Exception {
        byte[] csvBytes = readResource("sample-transactions.csv");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample-transactions.csv",
                "text/csv",
                csvBytes
        );
        mockMvc.perform(multipart("/api/portfolio/transactions/upload").file(file))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/portfolio/performance")
                        .param("startDate", "2026-02-16")
                        .param("endDate", "2026-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail", containsString("startDate must be on or before endDate")));
    }

    @Test
    void symbols_returnsEmptyArrayWhenNoTransactionsExist() throws Exception {
        mockMvc.perform(get("/api/portfolio/symbols"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    private byte[] readResource(String resourceName) throws IOException {
        return new ClassPathResource(resourceName).getInputStream().readAllBytes();
    }

    private SampleCsvExpectations parseExpectations(byte[] csvBytes) throws IOException {
        int transactionCount = 0;
        Set<String> accounts = new TreeSet<>();

        try (CSVParser parser = CSVParser.parse(
                new String(csvBytes, StandardCharsets.UTF_8),
                CSVFormat.DEFAULT.builder()
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .setIgnoreHeaderCase(true)
                        .setTrim(true)
                        .build()
        )) {
            for (CSVRecord record : parser) {
                transactionCount++;
                accounts.add(record.get("account"));
            }
        }

        return new SampleCsvExpectations(transactionCount, accounts);
    }

    private record SampleCsvExpectations(int transactionCount, Set<String> sortedAccounts) {
    }
}
