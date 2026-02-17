package com.temadison.stockdash.backend.model;

import java.util.List;
import java.util.Map;

public record PriceSyncResult(
        int symbolsRequested,
        int symbolsWithPurchases,
        int pricesStored,
        Map<String, Integer> storedBySymbol,
        Map<String, String> statusBySymbol,
        List<String> skippedSymbols
) {
}
