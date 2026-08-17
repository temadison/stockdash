import { useEffect, useState } from 'react';
import { getSymbols, syncPrices } from '../../shared/api/portfolioApi';
import type { PriceSyncResultDto } from '../../shared/types/api';

type SyncPanelProps = {
  onDone?: () => void | Promise<void>;
};

export function SyncPanel({ onDone }: SyncPanelProps) {
  const [symbolsText, setSymbolsText] = useState('AAPL,MSFT');
  const [status, setStatus] = useState('');
  const [syncing, setSyncing] = useState(false);
  const [result, setResult] = useState<PriceSyncResultDto | null>(null);

  useEffect(() => {
    const loadDefaultSymbols = async () => {
      try {
        const symbols = await getSymbols();
        if (symbols.length) setSymbolsText(symbols.join(','));
      } catch {
        // Keep manual input defaults.
      }
    };
    void loadDefaultSymbols();
  }, []);

  const onSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    const stocks = symbolsText
      .split(',')
      .map((value) => value.trim())
      .filter(Boolean);

    if (!stocks.length) {
      setStatus('Provide at least one symbol.');
      setResult(null);
      return;
    }

    try {
      setSyncing(true);
      setResult(null);
      setStatus(`Syncing ${stocks.length} symbol${stocks.length === 1 ? '' : 's'}...`);
      const syncResult = await syncPrices(stocks);
      setResult(syncResult);
      await onDone?.();
      setStatus(
        syncResult.pricesStored > 0
          ? `Changed ${syncResult.pricesStored} close-price rows across ${syncResult.symbolsWithPurchases}/${syncResult.symbolsRequested} symbols.`
          : `No close-price rows changed across ${syncResult.symbolsWithPurchases}/${syncResult.symbolsRequested} symbols.`
      );
    } catch (error) {
      setStatus((error as Error).message);
      setResult(null);
    } finally {
      setSyncing(false);
    }
  };

  return (
    <form onSubmit={onSubmit} className="stack gap-sm">
      <label className="field">
        <span>Sync Symbols</span>
        <input value={symbolsText} onChange={(e) => setSymbolsText(e.target.value)} placeholder="AAPL,MSFT,ASML" />
      </label>
      <button type="submit" disabled={syncing}>{syncing ? 'Syncing...' : 'Run Sync'}</button>
      {status ? <p className={syncing ? 'sync-status syncing' : 'muted'}>{status}</p> : null}
      {result ? (
        <div className="summary-grid">
          <article className="summary-card">
            <div className="summary-label">Symbols Requested</div>
            <div className="summary-value">{result.symbolsRequested}</div>
          </article>
          <article className="summary-card">
            <div className="summary-label">With Purchase History</div>
            <div className="summary-value">{result.symbolsWithPurchases}</div>
          </article>
          <article className="summary-card">
            <div className="summary-label">Rows Stored</div>
            <div className={`summary-value ${result.pricesStored > 0 ? 'status-ok' : ''}`}>{result.pricesStored}</div>
          </article>
        </div>
      ) : null}
      {result ? (
        <table>
          <thead>
            <tr><th>Symbol</th><th>Changed</th><th>Status</th></tr>
          </thead>
          <tbody>
            {Array.from(new Set([
              ...Object.keys(result.statusBySymbol ?? {}),
              ...Object.keys(result.storedBySymbol ?? {}),
              ...(result.skippedSymbols ?? [])
            ]))
              .sort()
              .map((symbol) => (
                <tr key={symbol}>
                  <td>{symbol}</td>
                  <td>{result.storedBySymbol?.[symbol] ?? 0}</td>
                  <td>{result.statusBySymbol?.[symbol] ?? (result.skippedSymbols?.includes(symbol) ? 'no_purchase_history' : 'unknown')}</td>
                </tr>
              ))}
          </tbody>
        </table>
      ) : null}
    </form>
  );
}
