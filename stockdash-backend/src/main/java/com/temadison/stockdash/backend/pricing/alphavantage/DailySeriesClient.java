package com.temadison.stockdash.backend.pricing.alphavantage;

public interface DailySeriesClient {

    SeriesFetchResult fetchDailyCloseSeries(String symbol);
}
