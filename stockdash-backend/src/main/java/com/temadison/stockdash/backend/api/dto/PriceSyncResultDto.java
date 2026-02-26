package com.temadison.stockdash.backend.api.dto;

import java.util.List;
import java.util.Map;

public record PriceSyncResultDto(
        int symbolsRequested,
        int symbolsWithPurchases,
        int pricesStored,
        Map<String, Integer> storedBySymbol,
        Map<String, String> statusBySymbol,
        List<String> skippedSymbols
) {
}
