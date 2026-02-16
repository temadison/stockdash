package com.temadison.stockdash.backend.service;

import com.temadison.stockdash.backend.model.PortfolioSnapshot;
import com.temadison.stockdash.backend.model.PositionValue;
import com.temadison.stockdash.backend.repository.AccountRepository;
import com.temadison.stockdash.backend.repository.TradeTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PortfolioSummaryServiceTest {

    @Autowired
    private PortfolioSummaryService portfolioSummaryService;

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
    void dailySummary_aggregatesPersistedTransactionsAsOfDate() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "summary-fixture.csv",
                "text/csv",
                (
                        "trade_date,account,symbol,type,quantity,price,fee\n" +
                        "2026-01-01,IRA,AAPL,BUY,10,100,1\n" +
                        "2026-01-05,IRA,AAPL,BUY,5,120,1\n" +
                        "2026-01-10,IRA,AAPL,SELL,3,130,1\n" +
                        "2026-01-12,IRA,MSFT,BUY,2,200,2\n"
                ).getBytes()
        );
        csvTransactionImportService.importCsv(file);

        List<PortfolioSnapshot> beforeSellAndMsft = portfolioSummaryService.getDailySummary(LocalDate.of(2026, 1, 9));
        assertThat(beforeSellAndMsft).hasSize(1);

        PortfolioSnapshot snapshotBefore = beforeSellAndMsft.getFirst();
        assertThat(snapshotBefore.accountName()).isEqualTo("IRA");
        assertThat(snapshotBefore.totalValue()).isEqualByComparingTo(new BigDecimal("1798.00"));
        assertThat(snapshotBefore.positions()).containsExactly(new PositionValue("AAPL", new BigDecimal("1798.00")));

        List<PortfolioSnapshot> afterAllTrades = portfolioSummaryService.getDailySummary(LocalDate.of(2026, 1, 15));
        assertThat(afterAllTrades).hasSize(1);

        PortfolioSnapshot snapshotAfter = afterAllTrades.getFirst();
        assertThat(snapshotAfter.totalValue()).isEqualByComparingTo(new BigDecimal("1955.00"));
        assertThat(snapshotAfter.positions()).containsExactly(
                new PositionValue("AAPL", new BigDecimal("1557.00")),
                new PositionValue("MSFT", new BigDecimal("398.00"))
        );
    }

    @Test
    void dailySummary_returnsEmptyWhenNoTransactionsExist() {
        List<PortfolioSnapshot> snapshots = portfolioSummaryService.getDailySummary(LocalDate.of(2026, 2, 16));
        assertThat(snapshots).isEmpty();
    }
}
