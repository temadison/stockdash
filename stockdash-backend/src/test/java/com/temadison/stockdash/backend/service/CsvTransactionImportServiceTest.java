package com.temadison.stockdash.backend.service;

import com.temadison.stockdash.backend.exception.CsvImportException;
import com.temadison.stockdash.backend.model.CsvUploadResult;
import com.temadison.stockdash.backend.repository.AccountRepository;
import com.temadison.stockdash.backend.repository.TradeTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

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
    void importCsv_persistsTransactionsAndAccounts() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "transactions.csv",
                "text/csv",
                (
                        "trade_date,account,symbol,type,quantity,price,fee\n" +
                        "2026-02-14,IRA,AAPL,BUY,10,185.10,1.00\n" +
                        "2026-02-15,IRA,MSFT,SELL,5,420.00,0.75\n" +
                        "2026-02-15,Brokerage,NVDA,BUY,2,800.00,0.50\n"
                ).getBytes()
        );

        CsvUploadResult result = csvTransactionImportService.importCsv(file);

        assertThat(result.importedCount()).isEqualTo(3);
        assertThat(result.accountsAffected()).containsExactly("Brokerage", "IRA");
        assertThat(tradeTransactionRepository.count()).isEqualTo(3);
        assertThat(accountRepository.count()).isEqualTo(2);
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
}
