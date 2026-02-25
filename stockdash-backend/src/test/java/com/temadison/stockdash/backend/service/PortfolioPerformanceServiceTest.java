package com.temadison.stockdash.backend.service;

import com.temadison.stockdash.backend.domain.AccountEntity;
import com.temadison.stockdash.backend.domain.TradeTransactionEntity;
import com.temadison.stockdash.backend.domain.TransactionType;
import com.temadison.stockdash.backend.model.PortfolioPerformancePoint;
import com.temadison.stockdash.backend.repository.DailyClosePriceRepository;
import com.temadison.stockdash.backend.repository.TradeTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioPerformanceServiceTest {

    @Mock
    private TradeTransactionRepository tradeTransactionRepository;

    @Mock
    private DailyClosePriceRepository dailyClosePriceRepository;

    @Test
    void throwsWhenStartDateIsAfterEndDate() {
        PortfolioPerformanceService service = new PortfolioPerformanceService(
                tradeTransactionRepository,
                dailyClosePriceRepository
        );
        when(tradeTransactionRepository.findByTradeDateLessThanEqualOrderByTradeDateAscIdAsc(any()))
                .thenReturn(List.of(trade("IRA", "AAPL", TransactionType.BUY, "10", "100.00", "1.00", LocalDate.of(2026, 1, 1))));

        assertThatThrownBy(() -> service.performance("IRA", LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("startDate must be on or before endDate");
    }

    @Test
    void filtersByAccountAndFallsBackToLastKnownTradePrice() {
        PortfolioPerformanceService service = new PortfolioPerformanceService(
                tradeTransactionRepository,
                dailyClosePriceRepository
        );

        TradeTransactionEntity iraBuy = trade("IRA", "AAPL", TransactionType.BUY, "10", "100.00", "1.00", LocalDate.of(2026, 1, 1));
        TradeTransactionEntity brokerBuy = trade("BROKER", "AAPL", TransactionType.BUY, "5", "200.00", "1.00", LocalDate.of(2026, 1, 1));

        when(tradeTransactionRepository.findByTradeDateLessThanEqualOrderByTradeDateAscIdAsc(any()))
                .thenReturn(List.of(iraBuy, brokerBuy));
        when(dailyClosePriceRepository.findBySymbolAndPriceDateLessThanEqualOrderByPriceDateDesc(anyString(), any()))
                .thenReturn(List.of());

        List<PortfolioPerformancePoint> iraOnly = service.performance("IRA", null, LocalDate.of(2026, 1, 1));
        List<PortfolioPerformancePoint> total = service.performance("TOTAL", null, LocalDate.of(2026, 1, 1));

        assertThat(iraOnly).hasSize(1);
        assertThat(iraOnly.get(0).totalValue()).isEqualByComparingTo("999.00");

        assertThat(total).hasSize(1);
        assertThat(total.get(0).totalValue()).isEqualByComparingTo("2998.00");
    }

    private TradeTransactionEntity trade(
            String accountName,
            String symbol,
            TransactionType type,
            String quantity,
            String price,
            String fee,
            LocalDate tradeDate
    ) {
        AccountEntity account = new AccountEntity();
        account.setName(accountName);

        TradeTransactionEntity trade = new TradeTransactionEntity();
        trade.setAccount(account);
        trade.setSymbol(symbol);
        trade.setType(type);
        trade.setQuantity(new BigDecimal(quantity));
        trade.setPrice(new BigDecimal(price));
        trade.setFee(new BigDecimal(fee));
        trade.setTradeDate(tradeDate);
        return trade;
    }
}
