package com.temadison.stockdash.backend.service;

import com.temadison.stockdash.backend.exception.CsvImportException;
import com.temadison.stockdash.backend.model.CsvUploadResult;
import com.temadison.stockdash.backend.repository.AccountRepository;
import com.temadison.stockdash.backend.repository.TradeTransactionRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class CsvTransactionImportServiceTest {

    @Autowired
    private CsvTransactionImportService csvTransactionImportService;

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
    void importCsv_persistsTransactionsAndAccounts_fromSampleCsvFile() throws IOException {
        byte[] csvBytes = new ClassPathResource("sample-transactions.csv").getInputStream().readAllBytes();
        SampleCsvExpectations expectations = parseExpectations(csvBytes);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample-transactions.csv",
                "text/csv",
                csvBytes
        );

        CsvUploadResult result = csvTransactionImportService.importCsv(file);

        assertThat(result.importedCount()).isEqualTo(expectations.transactionCount());
        assertThat(result.skippedCount()).isZero();
        assertThat(result.accountsAffected()).containsExactlyElementsOf(expectations.sortedAccounts());
        assertThat(tradeTransactionRepository.count()).isEqualTo(expectations.transactionCount());
        assertThat(accountRepository.count()).isEqualTo(expectations.sortedAccounts().size());
    }

    @Test
    void importCsv_secondImportSkipsDuplicates() throws IOException {
        byte[] csvBytes = new ClassPathResource("sample-transactions.csv").getInputStream().readAllBytes();
        SampleCsvExpectations expectations = parseExpectations(csvBytes);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample-transactions.csv",
                "text/csv",
                csvBytes
        );

        CsvUploadResult first = csvTransactionImportService.importCsv(file);
        CsvUploadResult second = csvTransactionImportService.importCsv(file);

        assertThat(first.importedCount()).isEqualTo(expectations.transactionCount());
        assertThat(first.skippedCount()).isZero();
        assertThat(second.importedCount()).isZero();
        assertThat(second.skippedCount()).isEqualTo(expectations.transactionCount());
        assertThat(tradeTransactionRepository.count()).isEqualTo(expectations.transactionCount());
        assertThat(accountRepository.count()).isEqualTo(expectations.sortedAccounts().size());
    }

    @Test
    void importCsv_throwsOnMissingHeader() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "transactions.csv",
                "text/csv",
                (
                        "trade_date,account,symbol,type,quantity,price\n" +
                        "2026-02-14,IRA,AAPL,BUY,10,185.10\n"
                ).getBytes()
        );

        assertThatThrownBy(() -> csvTransactionImportService.importCsv(file))
                .isInstanceOf(CsvImportException.class)
                .hasMessageContaining("CSV must include headers");

        assertThat(tradeTransactionRepository.count()).isZero();
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
