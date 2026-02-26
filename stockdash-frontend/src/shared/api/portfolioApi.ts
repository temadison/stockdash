import { httpGet, httpPostForm, httpPostJson } from './httpClient';
import type {
  CsvUploadResultDto,
  DailyClosePricePointDto,
  PortfolioPerformancePointDto,
  PortfolioSnapshotDto,
  PriceSyncResultDto
} from '../types/api';

export function getDailySummary(asOfDate: string) {
  return httpGet<PortfolioSnapshotDto[]>(`/api/portfolio/daily-summary?date=${encodeURIComponent(asOfDate)}`);
}

export function uploadTransactions(file: File) {
  const form = new FormData();
  form.append('file', file);
  return httpPostForm<CsvUploadResultDto>('/api/portfolio/transactions/upload', form);
}

export function syncPrices(stocks: string[]) {
  return httpPostJson<PriceSyncResultDto>('/api/portfolio/prices/sync', { stocks });
}

export function getSymbols() {
  return httpGet<string[]>('/api/portfolio/symbols');
}

export function getHistory(symbol: string, startDate?: string, endDate?: string) {
  const params = new URLSearchParams({ symbol });
  if (startDate) params.set('startDate', startDate);
  if (endDate) params.set('endDate', endDate);
  return httpGet<DailyClosePricePointDto[]>(`/api/portfolio/prices/history?${params.toString()}`);
}

export function getPerformance(account?: string, startDate?: string, endDate?: string) {
  const params = new URLSearchParams();
  if (account) params.set('account', account);
  if (startDate) params.set('startDate', startDate);
  if (endDate) params.set('endDate', endDate);
  return httpGet<PortfolioPerformancePointDto[]>(`/api/portfolio/performance?${params.toString()}`);
}
