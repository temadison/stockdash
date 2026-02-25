package com.temadison.stockdash.backend.pricing.alphavantage;

public enum SeriesFetchStatus {
    SUCCESS,
    RATE_LIMITED,
    CIRCUIT_OPEN,
    INVALID_SYMBOL,
    API_ERROR,
    NO_DATA
}
