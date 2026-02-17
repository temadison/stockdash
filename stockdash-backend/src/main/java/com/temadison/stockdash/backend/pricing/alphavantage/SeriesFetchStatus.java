package com.temadison.stockdash.backend.pricing.alphavantage;

public enum SeriesFetchStatus {
    SUCCESS,
    RATE_LIMITED,
    INVALID_SYMBOL,
    API_ERROR,
    NO_DATA
}
