package com.temadison.stockdash.backend.service;

import com.temadison.stockdash.backend.domain.JobRunStatus;
import com.temadison.stockdash.backend.model.CsvUploadResult;
import com.temadison.stockdash.backend.repository.AccountRepository;
import com.temadison.stockdash.backend.repository.JobRunRepository;
import com.temadison.stockdash.backend.repository.TradeTransactionRepository;
import com.temadison.stockdash.backend.support.MySqlContainerBaseIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;

class CsvTransactionImportServiceIT extends MySqlContainerBaseIT {

    @Autowired
    private CsvTransactionImportService csvTransactionImportService;

    @Autowired
    private TradeTransactionRepository tradeTransactionRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JobRunRepository jobRunRepository;

    @BeforeEach
    void setUp() {
        tradeTransactionRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void importCsv_isIdempotentAndFlywayMigrationsAreApplied() {
        MockMultipartFile csv = new MockMultipartFile(
                "file",
                "transactions.csv",
                "text/csv",
                (
                        "trade_date,account,symbol,type,quantity,price,fee\n" +
                        "2026-01-01,IRA,AAPL,BUY,10,100,1\n" +
                        "2026-01-02,IRA,MSFT,BUY,5,200,1\n"
                ).getBytes()
        );

        CsvUploadResult first = csvTransactionImportService.importCsv(csv);
        CsvUploadResult second = csvTransactionImportService.importCsv(csv);

        Integer flywayVersionRows = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version = '1'",
                Integer.class
        );

        assertThat(flywayVersionRows).isEqualTo(1);
        assertThat(first.importedCount()).isEqualTo(2);
        assertThat(first.skippedCount()).isEqualTo(0);
        assertThat(second.importedCount()).isEqualTo(0);
        assertThat(second.skippedCount()).isEqualTo(2);
        assertThat(accountRepository.count()).isEqualTo(1);
        assertThat(tradeTransactionRepository.count()).isEqualTo(2);
        assertThat(jobRunRepository.count()).isEqualTo(2);
        assertThat(jobRunRepository.findAll())
                .extracting(run -> run.getStatus())
                .containsOnly(JobRunStatus.SUCCESS);
    }
}
