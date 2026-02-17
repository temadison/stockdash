package com.temadison.stockdash.backend.pricing;

public class MarketPriceException extends RuntimeException {
    public MarketPriceException(String message) {
        super(message);
    }

    public MarketPriceException(String message, Throwable cause) {
        super(message, cause);
    }
}
