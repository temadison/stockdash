package com.temadison.stockdash.backend.service;

import com.temadison.stockdash.backend.model.PriceSyncResult;
import com.temadison.stockdash.backend.pricing.alphavantage.DailySeriesClient;
import com.temadison.stockdash.backend.pricing.alphavantage.SeriesFetchResult;
import com.temadison.stockdash.backend.pricing.alphavantage.SeriesFetchStatus;
import com.temadison.stockdash.backend.repository.AccountRepository;
import com.temadison.stockdash.backend.repository.DailyClosePriceRepository;
import com.temadison.stockdash.backend.repository.TradeTransactionRepository;
import com.temadison.stockdash.backend.support.MySqlContainerBaseIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

class DailyClosePriceSyncServiceIT extends MySqlContainerBaseIT {

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

    @MockitoBean
    private DailySeriesClient dailySeriesClient;

    @BeforeEach
    void setUp() {
        dailyClosePriceRepository.deleteAll();
        tradeTransactionRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void syncForStocks_usesMySqlAndRemainsIdempotent() {
        MockMultipartFile csv = new MockMultipartFile(
                "file",
                "transactions.csv",
                "text/csv",
                (
                        "trade_date,account,symbol,type,quantity,price,fee\n" +
                        "2026-01-01,IRA,AAPL,BUY,10,100,1\n"
                ).getBytes()
        );
        csvTransactionImportService.importCsv(csv);

        given(dailySeriesClient.fetchDailyCloseSeries("AAPL"))
                .willReturn(new SeriesFetchResult(
                        Map.of(
                                LocalDate.of(2025, 12, 31), new BigDecimal("99.00"),
                                LocalDate.of(2026, 1, 1), new BigDecimal("100.00"),
                                LocalDate.of(2026, 1, 2), new BigDecimal("101.00")
                        ),
                        SeriesFetchStatus.SUCCESS
                ));

        PriceSyncResult first = dailyClosePriceSyncService.syncForStocks(List.of("AAPL"));
        PriceSyncResult second = dailyClosePriceSyncService.syncForStocks(List.of("AAPL"));

        assertThat(first.pricesStored()).isEqualTo(1);
        assertThat(first.storedBySymbol()).containsEntry("AAPL", 1);
        assertThat(first.statusBySymbol()).containsEntry("AAPL", "stored");

        assertThat(second.pricesStored()).isEqualTo(0);
        assertThat(second.storedBySymbol()).containsEntry("AAPL", 0);
        assertThat(second.statusBySymbol()).containsEntry("AAPL", "no_new_rows");

        assertThat(dailyClosePriceRepository.findBySymbolOrderByPriceDateAsc("AAPL"))
                .extracting(price -> price.getPriceDate().toString())
                .containsExactly("2026-01-02");
    }

    @Test
    void syncForStocks_marksSymbolsWithoutBuyHistoryAsSkipped() {
        PriceSyncResult result = dailyClosePriceSyncService.syncForStocks(List.of("AAPL"));

        assertThat(result.symbolsRequested()).isEqualTo(1);
        assertThat(result.symbolsWithPurchases()).isEqualTo(0);
        assertThat(result.pricesStored()).isEqualTo(0);
        assertThat(result.statusBySymbol()).containsEntry("AAPL", "no_purchase_history");
        assertThat(result.skippedSymbols()).containsExactly("AAPL");
        assertThat(dailyClosePriceRepository.count()).isZero();
    }
}
