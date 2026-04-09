package com.temadison.stockdash.backend.service;

import com.temadison.stockdash.backend.domain.DailyClosePriceEntity;
import com.temadison.stockdash.backend.model.PortfolioSnapshot;
import com.temadison.stockdash.backend.model.PositionValue;
import com.temadison.stockdash.backend.pricing.MarketPriceService;
import com.temadison.stockdash.backend.repository.AccountRepository;
import com.temadison.stockdash.backend.repository.DailyClosePriceRepository;
import com.temadison.stockdash.backend.repository.TradeTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

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

    @Autowired
    private DailyClosePriceRepository dailyClosePriceRepository;

    @MockitoBean
    private MarketPriceService marketPriceService;

    @BeforeEach
    void setUp() {
        dailyClosePriceRepository.deleteAll();
        tradeTransactionRepository.deleteAll();
        accountRepository.deleteAll();
        given(marketPriceService.getClosePriceOnOrBefore(any(), any())).willReturn(Optional.empty());
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

        PortfolioSnapshot snapshotBefore = beforeSellAndMsft.get(0);
        assertThat(snapshotBefore.accountName()).isEqualTo("IRA");
        assertThat(snapshotBefore.totalValue()).isEqualByComparingTo(new BigDecimal("1798.00"));
        assertThat(snapshotBefore.cashBalance()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(snapshotBefore.positions()).containsExactly(new PositionValue(
                "AAPL",
                15L,
                new BigDecimal("120.000000"),
                new BigDecimal("1798.00")
        ));

        List<PortfolioSnapshot> afterAllTrades = portfolioSummaryService.getDailySummary(LocalDate.of(2026, 1, 15));
        assertThat(afterAllTrades).hasSize(1);

        PortfolioSnapshot snapshotAfter = afterAllTrades.get(0);
        assertThat(snapshotAfter.totalValue()).isEqualByComparingTo(new BigDecimal("1955.00"));
        assertThat(snapshotAfter.cashBalance()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(snapshotAfter.positions()).containsExactly(
                new PositionValue("AAPL", 12L, new BigDecimal("130.000000"), new BigDecimal("1557.00")),
                new PositionValue("MSFT", 2L, new BigDecimal("200.000000"), new BigDecimal("398.00"))
        );
    }

    @Test
    void dailySummary_usesMarketClosePriceWhenAvailable() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "summary-fixture.csv",
                "text/csv",
                (
                        "trade_date,account,symbol,type,quantity,price,fee\n" +
                        "2026-01-01,IRA,AAPL,BUY,10,100,1\n" +
                        "2026-01-05,IRA,AAPL,BUY,5,120,1\n" +
                        "2026-01-10,IRA,AAPL,SELL,3,130,1\n"
                ).getBytes()
        );
        csvTransactionImportService.importCsv(file);

        given(marketPriceService.getClosePriceOnOrBefore(eq("AAPL"), eq(LocalDate.of(2026, 1, 15))))
                .willReturn(Optional.of(new BigDecimal("140.00")));

        List<PortfolioSnapshot> snapshots = portfolioSummaryService.getDailySummary(LocalDate.of(2026, 1, 15));
        PortfolioSnapshot snapshot = snapshots.get(0);

        // 12 shares * market close 140 - total fees 3
        assertThat(snapshot.positions()).containsExactly(new PositionValue(
                "AAPL",
                12L,
                new BigDecimal("140.00"),
                new BigDecimal("1677.00")
        ));
        assertThat(snapshot.cashBalance()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(snapshot.totalValue()).isEqualByComparingTo(new BigDecimal("1677.00"));
    }

    @Test
    void dailySummary_returnsEmptyWhenNoTransactionsExist() {
        List<PortfolioSnapshot> snapshots = portfolioSummaryService.getDailySummary(LocalDate.of(2026, 2, 16));
        assertThat(snapshots).isEmpty();
    }

    @Test
    void dailySummary_usesStoredClosePriceHistoryForSelectedDate() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "summary-fixture.csv",
                "text/csv",
                (
                        "trade_date,account,symbol,type,quantity,price,fee\n" +
                                "2026-01-01,IRA,AAPL,BUY,10,100,1\n"
                ).getBytes()
        );
        csvTransactionImportService.importCsv(file);

        DailyClosePriceEntity jan2 = new DailyClosePriceEntity();
        jan2.setSymbol("AAPL");
        jan2.setPriceDate(LocalDate.of(2026, 1, 2));
        jan2.setClosePrice(new BigDecimal("120.00"));
        DailyClosePriceEntity jan5 = new DailyClosePriceEntity();
        jan5.setSymbol("AAPL");
        jan5.setPriceDate(LocalDate.of(2026, 1, 5));
        jan5.setClosePrice(new BigDecimal("140.00"));
        dailyClosePriceRepository.saveAll(List.of(jan2, jan5));

        PortfolioSnapshot jan3 = portfolioSummaryService.getDailySummary(LocalDate.of(2026, 1, 3)).get(0);
        PortfolioSnapshot jan6 = portfolioSummaryService.getDailySummary(LocalDate.of(2026, 1, 6)).get(0);

        assertThat(jan3.positions()).containsExactly(new PositionValue(
                "AAPL",
                10L,
                new BigDecimal("120.000000"),
                new BigDecimal("1199.00")
        ));
        assertThat(jan6.positions()).containsExactly(new PositionValue(
                "AAPL",
                10L,
                new BigDecimal("140.000000"),
                new BigDecimal("1399.00")
        ));
        assertThat(jan3.cashBalance()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(jan6.cashBalance()).isEqualByComparingTo(new BigDecimal("0.00"));
    }

    @Test
    void dailySummary_excludesTradesAfterAsOfDate() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "future-trade-fixture.csv",
                "text/csv",
                (
                        "trade_date,account,symbol,type,quantity,price,fee\n" +
                                "2026-03-06,IRA,AAPL,BUY,10,100,1\n" +
                                "2026-03-09,IRA,NVDA,BUY,5,120,1\n"
                ).getBytes()
        );
        csvTransactionImportService.importCsv(file);

        PortfolioSnapshot snapshotAsOfMar6 = portfolioSummaryService.getDailySummary(LocalDate.of(2026, 3, 6)).get(0);
        PortfolioSnapshot snapshotAsOfMar9 = portfolioSummaryService.getDailySummary(LocalDate.of(2026, 3, 9)).get(0);

        assertThat(snapshotAsOfMar6.positions()).extracting(PositionValue::symbol).containsExactly("AAPL");
        assertThat(snapshotAsOfMar9.positions()).extracting(PositionValue::symbol).containsExactly("AAPL", "NVDA");
    }

    @Test
    void dailySummary_includesCashBalanceForCashAwareAccounts() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "cash-fixture.csv",
                "text/csv",
                (
                        "trade_date,account,symbol,type,quantity,price,fee\n" +
                        "2026-01-01,IRA,CASH,CASH_DEPOSIT,10000,1,0\n" +
                        "2026-01-02,IRA,AAPL,BUY,10,100,1\n" +
                        "2026-01-03,IRA,CASH,DIVIDEND,50,1,0\n" +
                        "2026-01-04,IRA,CASH,CASH_WITHDRAWAL,100,1,0\n"
                ).getBytes()
        );
        csvTransactionImportService.importCsv(file);

        PortfolioSnapshot snapshot = portfolioSummaryService.getDailySummary(LocalDate.of(2026, 1, 4)).get(0);

        assertThat(snapshot.cashBalance()).isEqualByComparingTo(new BigDecimal("8949.00"));
        assertThat(snapshot.positions()).containsExactly(new PositionValue(
                "AAPL",
                10L,
                new BigDecimal("100.000000"),
                new BigDecimal("1000.00")
        ));
        assertThat(snapshot.totalValue()).isEqualByComparingTo(new BigDecimal("9949.00"));
    }
}
