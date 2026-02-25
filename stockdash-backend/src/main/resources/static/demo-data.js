(() => {
  const transactions = [
    { date: "2025-10-03", account: "DEMO_GROWTH", symbol: "AAPL", type: "BUY", quantity: 40, price: 185.25, fee: 1.0 },
    { date: "2025-10-03", account: "DEMO_GROWTH", symbol: "MSFT", type: "BUY", quantity: 22, price: 430.1, fee: 1.0 },
    { date: "2025-10-03", account: "DEMO_GROWTH", symbol: "NVDA", type: "BUY", quantity: 35, price: 120.75, fee: 1.0 },
    { date: "2025-11-07", account: "DEMO_GROWTH", symbol: "SPY", type: "BUY", quantity: 18, price: 589.32, fee: 1.0 },
    { date: "2025-11-07", account: "DEMO_GROWTH", symbol: "VTI", type: "BUY", quantity: 30, price: 283.11, fee: 1.0 },
    { date: "2025-12-12", account: "DEMO_GROWTH", symbol: "AAPL", type: "BUY", quantity: 10, price: 192.4, fee: 1.0 },
    { date: "2025-12-12", account: "DEMO_GROWTH", symbol: "NVDA", type: "SELL", quantity: 8, price: 132.05, fee: 1.0 },
    { date: "2026-01-17", account: "DEMO_GROWTH", symbol: "MSFT", type: "BUY", quantity: 6, price: 444.88, fee: 1.0 },
    { date: "2026-01-17", account: "DEMO_GROWTH", symbol: "SPY", type: "BUY", quantity: 4, price: 603.47, fee: 1.0 },
    { date: "2026-02-11", account: "DEMO_GROWTH", symbol: "VTI", type: "BUY", quantity: 6, price: 296.2, fee: 1.0 },
    { date: "2025-10-10", account: "DEMO_INCOME", symbol: "JNJ", type: "BUY", quantity: 38, price: 162.93, fee: 1.0 },
    { date: "2025-10-10", account: "DEMO_INCOME", symbol: "KO", type: "BUY", quantity: 80, price: 63.44, fee: 1.0 },
    { date: "2025-10-10", account: "DEMO_INCOME", symbol: "PG", type: "BUY", quantity: 26, price: 171.55, fee: 1.0 },
    { date: "2025-11-21", account: "DEMO_INCOME", symbol: "XLU", type: "BUY", quantity: 60, price: 74.32, fee: 1.0 },
    { date: "2025-11-21", account: "DEMO_INCOME", symbol: "SCHD", type: "BUY", quantity: 42, price: 83.9, fee: 1.0 },
    { date: "2025-12-19", account: "DEMO_INCOME", symbol: "KO", type: "BUY", quantity: 20, price: 64.15, fee: 1.0 },
    { date: "2025-12-19", account: "DEMO_INCOME", symbol: "JNJ", type: "SELL", quantity: 6, price: 165.02, fee: 1.0 },
    { date: "2026-01-24", account: "DEMO_INCOME", symbol: "PG", type: "BUY", quantity: 8, price: 174.3, fee: 1.0 },
    { date: "2026-01-24", account: "DEMO_INCOME", symbol: "SCHD", type: "BUY", quantity: 10, price: 85.1, fee: 1.0 },
    { date: "2026-02-12", account: "DEMO_INCOME", symbol: "XLU", type: "BUY", quantity: 12, price: 76.04, fee: 1.0 },
  ];

  const pricesBySymbol = {
    AAPL: [
      { date: "2025-10-03", closePrice: 185.25 },
      { date: "2025-11-07", closePrice: 190.8 },
      { date: "2025-12-12", closePrice: 192.4 },
      { date: "2026-01-17", closePrice: 198.1 },
      { date: "2026-02-12", closePrice: 201.6 },
      { date: "2026-02-20", closePrice: 203.4 },
    ],
    MSFT: [
      { date: "2025-10-03", closePrice: 430.1 },
      { date: "2025-11-07", closePrice: 438.7 },
      { date: "2025-12-12", closePrice: 441.5 },
      { date: "2026-01-17", closePrice: 444.88 },
      { date: "2026-02-12", closePrice: 452.2 },
      { date: "2026-02-20", closePrice: 456.9 },
    ],
    NVDA: [
      { date: "2025-10-03", closePrice: 120.75 },
      { date: "2025-11-07", closePrice: 126.4 },
      { date: "2025-12-12", closePrice: 132.05 },
      { date: "2026-01-17", closePrice: 136.8 },
      { date: "2026-02-12", closePrice: 141.3 },
      { date: "2026-02-20", closePrice: 139.9 },
    ],
    SPY: [
      { date: "2025-10-03", closePrice: 583.2 },
      { date: "2025-11-07", closePrice: 589.32 },
      { date: "2025-12-12", closePrice: 598.6 },
      { date: "2026-01-17", closePrice: 603.47 },
      { date: "2026-02-12", closePrice: 610.8 },
      { date: "2026-02-20", closePrice: 612.1 },
    ],
    VTI: [
      { date: "2025-10-03", closePrice: 279.4 },
      { date: "2025-11-07", closePrice: 283.11 },
      { date: "2025-12-12", closePrice: 289.9 },
      { date: "2026-01-17", closePrice: 293.4 },
      { date: "2026-02-12", closePrice: 296.2 },
      { date: "2026-02-20", closePrice: 297.6 },
    ],
    JNJ: [
      { date: "2025-10-10", closePrice: 162.93 },
      { date: "2025-11-21", closePrice: 164.1 },
      { date: "2025-12-19", closePrice: 165.02 },
      { date: "2026-01-24", closePrice: 166.4 },
      { date: "2026-02-12", closePrice: 167.2 },
      { date: "2026-02-20", closePrice: 166.8 },
    ],
    KO: [
      { date: "2025-10-10", closePrice: 63.44 },
      { date: "2025-11-21", closePrice: 63.8 },
      { date: "2025-12-19", closePrice: 64.15 },
      { date: "2026-01-24", closePrice: 64.7 },
      { date: "2026-02-12", closePrice: 65.1 },
      { date: "2026-02-20", closePrice: 65.4 },
    ],
    PG: [
      { date: "2025-10-10", closePrice: 171.55 },
      { date: "2025-11-21", closePrice: 172.2 },
      { date: "2025-12-19", closePrice: 173.4 },
      { date: "2026-01-24", closePrice: 174.3 },
      { date: "2026-02-12", closePrice: 175.6 },
      { date: "2026-02-20", closePrice: 176.1 },
    ],
    XLU: [
      { date: "2025-10-10", closePrice: 73.9 },
      { date: "2025-11-21", closePrice: 74.32 },
      { date: "2025-12-19", closePrice: 75.1 },
      { date: "2026-01-24", closePrice: 75.7 },
      { date: "2026-02-12", closePrice: 76.04 },
      { date: "2026-02-20", closePrice: 76.3 },
    ],
    SCHD: [
      { date: "2025-10-10", closePrice: 82.7 },
      { date: "2025-11-21", closePrice: 83.9 },
      { date: "2025-12-19", closePrice: 84.4 },
      { date: "2026-01-24", closePrice: 85.1 },
      { date: "2026-02-12", closePrice: 85.6 },
      { date: "2026-02-20", closePrice: 85.9 },
    ],
  };

  const accounts = Array.from(new Set(transactions.map((t) => t.account))).sort();
  const allPriceDates = Array.from(
    new Set(Object.values(pricesBySymbol).flatMap((series) => series.map((p) => p.date)))
  ).sort();

  function normalizeSymbol(symbol) {
    return String(symbol || "").trim().toUpperCase();
  }

  function roundMoney(value) {
    return Math.round((Number(value) + Number.EPSILON) * 100) / 100;
  }

  function closeOnOrBefore(symbol, date) {
    const series = pricesBySymbol[symbol] || [];
    let latest = null;
    for (const point of series) {
      if (point.date <= date) latest = point.closePrice;
      else break;
    }
    return latest;
  }

  function lastTradePriceOnOrBefore(symbol, date) {
    let latest = null;
    for (const tx of transactions) {
      if (tx.symbol === symbol && tx.date <= date) latest = tx.price;
    }
    return latest;
  }

  function positionsForAccountAsOf(accountName, date) {
    const bySymbol = new Map();
    for (const tx of transactions) {
      if (tx.account !== accountName || tx.date > date) continue;
      const current = bySymbol.get(tx.symbol) || { quantity: 0, fees: 0 };
      const signedQty = tx.type === "BUY" ? tx.quantity : -tx.quantity;
      current.quantity += signedQty;
      current.fees += tx.fee;
      bySymbol.set(tx.symbol, current);
    }

    const positions = [];
    for (const [symbol, pos] of bySymbol.entries()) {
      if (pos.quantity <= 0) continue;
      const close = closeOnOrBefore(symbol, date);
      const fallback = lastTradePriceOnOrBefore(symbol, date);
      const currentPrice = close ?? fallback ?? 0;
      const marketValue = roundMoney(pos.quantity * currentPrice - pos.fees);
      positions.push({
        symbol,
        quantity: roundMoney(pos.quantity),
        currentPrice: roundMoney(currentPrice),
        marketValue,
      });
    }
    positions.sort((a, b) => a.symbol.localeCompare(b.symbol));
    return positions;
  }

  function dailySummary(date) {
    const asOfDate = date || allPriceDates[allPriceDates.length - 1];
    return accounts.map((accountName) => {
      const positions = positionsForAccountAsOf(accountName, asOfDate);
      const totalValue = roundMoney(positions.reduce((sum, p) => sum + p.marketValue, 0));
      return { accountName, asOfDate, totalValue, positions };
    });
  }

  function performance(account, startDate, endDate) {
    const accountFilter = (account || "TOTAL").trim().toUpperCase();
    const start = startDate || allPriceDates[0];
    const end = endDate || allPriceDates[allPriceDates.length - 1];
    const dates = allPriceDates.filter((d) => d >= start && d <= end);

    return dates.map((date) => {
      const selectedAccounts = accountFilter === "TOTAL"
        ? accounts
        : accounts.filter((a) => a.toUpperCase() === accountFilter);
      const stockTotals = new Map();
      for (const accountName of selectedAccounts) {
        for (const position of positionsForAccountAsOf(accountName, date)) {
          stockTotals.set(position.symbol, roundMoney((stockTotals.get(position.symbol) || 0) + position.marketValue));
        }
      }
      const stocks = Array.from(stockTotals.entries())
        .map(([symbol, marketValue]) => ({ symbol, marketValue: roundMoney(marketValue) }))
        .sort((a, b) => a.symbol.localeCompare(b.symbol));
      const totalValue = roundMoney(stocks.reduce((sum, s) => sum + s.marketValue, 0));
      return { date, totalValue, stocks };
    });
  }

  function history(symbol, startDate, endDate) {
    const normalized = normalizeSymbol(symbol);
    const series = pricesBySymbol[normalized] || [];
    return series
      .filter((p) => (!startDate || p.date >= startDate) && (!endDate || p.date <= endDate))
      .slice()
      .reverse();
  }

  function sync(stocks) {
    const normalized = Array.from(new Set((stocks || []).map(normalizeSymbol).filter(Boolean)));
    const buySymbols = new Set(transactions.filter((t) => t.type === "BUY").map((t) => t.symbol));
    const statusBySymbol = {};
    const storedBySymbol = {};
    const skippedSymbols = [];
    let symbolsWithPurchases = 0;

    for (const symbol of normalized) {
      storedBySymbol[symbol] = 0;
      if (!buySymbols.has(symbol)) {
        statusBySymbol[symbol] = "no_purchase_history";
        skippedSymbols.push(symbol);
        continue;
      }
      symbolsWithPurchases++;
      statusBySymbol[symbol] = "already_up_to_date";
    }

    return {
      symbolsRequested: normalized.length,
      symbolsWithPurchases,
      pricesStored: 0,
      storedBySymbol,
      statusBySymbol,
      skippedSymbols,
    };
  }

  function uploadUnavailable() {
    return {
      importedCount: 0,
      skippedCount: 0,
      accountsAffected: [],
      message: "CSV upload is disabled in portfolio demo mode.",
    };
  }

  function defaultSymbols() {
    return Array.from(new Set(transactions.map((t) => t.symbol))).sort();
  }

  window.StockdashDemoData = {
    dailySummary,
    performance,
    history,
    sync,
    defaultSymbols,
    uploadUnavailable,
  };
})();
