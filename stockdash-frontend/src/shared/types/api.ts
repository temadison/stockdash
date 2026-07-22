export type PositionValueDto = {
  symbol: string;
  quantity: number;
  currentPrice: number;
  marketValue: number;
  costBasis: number;
  gainLoss: number;
  totalReturn: number | null;
  cagr: number | null;
};

export type PortfolioSnapshotDto = {
  accountName: string;
  asOfDate: string;
  totalValue: number;
  cashBalance: number;
  positions: PositionValueDto[];
};

export type CsvUploadResultDto = {
  importedCount: number;
  skippedCount: number;
  accountsAffected: string[];
};

export type PriceSyncResultDto = {
  symbolsRequested: number;
  symbolsWithPurchases: number;
  pricesStored: number;
  storedBySymbol: Record<string, number>;
  statusBySymbol: Record<string, string>;
  skippedSymbols: string[];
};

export type DailyClosePricePointDto = {
  date: string;
  closePrice: number;
};

export type StockPerformanceValueDto = {
  symbol: string;
  marketValue: number;
};

export type PortfolioPerformancePointDto = {
  date: string;
  totalValue: number;
  netAmountSpent: number;
  cashBalance: number;
  stocks: StockPerformanceValueDto[];
};

export type ProblemDetails = {
  title?: string;
  detail?: string;
  status?: number;
  errors?: string[];
};
