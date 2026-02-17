package com.temadison.stockdash.backend.model;

import java.util.List;

public record PriceSyncRequest(List<String> stocks) {
}
