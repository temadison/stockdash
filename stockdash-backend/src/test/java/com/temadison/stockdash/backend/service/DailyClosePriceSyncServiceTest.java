package com.temadison.stockdash.backend.service;

import com.temadison.stockdash.backend.model.PriceSyncResult;
import com.temadison.stockdash.backend.pricing.alphavantage.AlphaVantageDailySeriesClient;
import com.temadison.stockdash.backend.repository.AccountRepository;
import com.temadison.stockdash.backend.repository.DailyClosePriceRepository;
import com.temadison.stockdash.backend.repository.TradeTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@SpringBootTest
class DailyClosePriceSyncServiceTest {

    @Autowired
    private DailyClosePriceSyncService dailyClosePriceSyncService;

    @Autowired
    private CsvTransactionImportService csvTransactionImportService;

    @Autowired
    private DailyClosePriceRepository dailyClosePriceRepository;

    @Autowired
    private TradeTransactionRepository tradeTransactionRepository;

    @Autowired
    private AccountRepository accountRepository;

    @MockBean
    private AlphaVantageDailySeriesClient alphaVantageDailySeriesClient;

    @BeforeEach
    void setUp() {
        dailyClosePriceRepository.deleteAll();
        tradeTransactionRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void syncForStocks_storesOnlyDatesAfterFirstPurchase_andIsIdempotent() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sync-fixture.csv",
                "text/csv",
                (
                        "trade_date,account,symbol,type,quantity,price,fee\n" +
                                "2026-01-01,IRA,AAPL,BUY,10,100,1\n" +
                                "2026-01-05,IRA,MSFT,BUY,2,200,1\n" +
                                "2026-01-08,IRA,AAPL,SELL,1,110,1\n"
                ).getBytes()
        );
        csvTransactionImportService.importCsv(file);

        given(alphaVantageDailySeriesClient.fetchDailyCloseSeries("AAPL")).willReturn(Map.of(
                LocalDate.of(2025, 12, 31), new BigDecimal("99.00"),
                LocalDate.of(2026, 1, 1), new BigDecimal("100.00"),
                LocalDate.of(2026, 1, 2), new BigDecimal("101.00"),
                LocalDate.of(2026, 1, 9), new BigDecimal("109.00")
        ));
        given(alphaVantageDailySeriesClient.fetchDailyCloseSeries("MSFT")).willReturn(Map.of(
                LocalDate.of(2026, 1, 4), new BigDecimal("198.00"),
                LocalDate.of(2026, 1, 5), new BigDecimal("200.00"),
                LocalDate.of(2026, 1, 6), new BigDecimal("202.00")
        ));

        PriceSyncResult first = dailyClosePriceSyncService.syncForStocks(List.of("aapl", "MSFT", "GOOG"));
        PriceSyncResult second = dailyClosePriceSyncService.syncForStocks(List.of("AAPL", "MSFT"));

        assertThat(first.symbolsRequested()).isEqualTo(3);
        assertThat(first.symbolsWithPurchases()).isEqualTo(2);
        assertThat(first.pricesStored()).isEqualTo(3);
        assertThat(first.storedBySymbol()).hasSize(2);
        assertThat(first.storedBySymbol()).containsEntry("AAPL", 2);
        assertThat(first.storedBySymbol()).containsEntry("MSFT", 1);
        assertThat(first.skippedSymbols()).containsExactly("GOOG");

        assertThat(second.pricesStored()).isZero();
        assertThat(second.storedBySymbol()).hasSize(2);
        assertThat(second.storedBySymbol()).containsEntry("AAPL", 0);
        assertThat(second.storedBySymbol()).containsEntry("MSFT", 0);

        assertThat(dailyClosePriceRepository.findBySymbolOrderByPriceDateAsc("AAPL"))
                .extracting(price -> price.getPriceDate().toString())
                .containsExactly("2026-01-02", "2026-01-09");
        assertThat(dailyClosePriceRepository.findBySymbolOrderByPriceDateAsc("MSFT"))
                .extracting(price -> price.getPriceDate().toString())
                .containsExactly("2026-01-06");
    }
}
