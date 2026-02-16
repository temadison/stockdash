package com.temadison.stockdash.backend.seed;

import com.temadison.stockdash.backend.repository.AccountRepository;
import com.temadison.stockdash.backend.repository.TradeTransactionRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "stockdash.seed.enabled=true",
        "stockdash.seed.csv-resource=sample-transactions.csv"
})
class SeedDataRunnerTest {

    @Autowired
    private TradeTransactionRepository tradeTransactionRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void startupSeed_importsCsvFromClasspath() throws IOException {
        byte[] csvBytes = new ClassPathResource("sample-transactions.csv").getInputStream().readAllBytes();
        SampleCsvExpectations expectations = parseExpectations(csvBytes);

        assertThat(tradeTransactionRepository.count()).isEqualTo(expectations.transactionCount());
        assertThat(accountRepository.count()).isEqualTo(expectations.sortedAccounts().size());
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
