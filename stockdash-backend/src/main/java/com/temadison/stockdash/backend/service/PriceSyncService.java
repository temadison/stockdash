package com.temadison.stockdash.backend.service;

import com.temadison.stockdash.backend.model.PriceSyncResult;

import java.util.List;

public interface PriceSyncService {

    PriceSyncResult syncForStocks(List<String> stocks);
}
