package com.temadison.stockdash.backend.service;

import com.temadison.stockdash.backend.repository.TradeTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PortfolioSymbolService implements PortfolioSymbolQueryService {

    private final TradeTransactionRepository tradeTransactionRepository;

    public PortfolioSymbolService(TradeTransactionRepository tradeTransactionRepository) {
        this.tradeTransactionRepository = tradeTransactionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> symbols() {
        return tradeTransactionRepository.findDistinctSymbolsOrderBySymbolAsc();
    }
}
