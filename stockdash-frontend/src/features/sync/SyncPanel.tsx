import { useEffect, useState } from 'react';
import { getSymbols, syncPrices } from '../../shared/api/portfolioApi';
import type { PriceSyncResultDto } from '../../shared/types/api';

export function SyncPanel() {
  const [symbolsText, setSymbolsText] = useState('AAPL,MSFT');
  const [status, setStatus] = useState('');
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
      const syncResult = await syncPrices(stocks);
      setResult(syncResult);
      setStatus(`Stored ${syncResult.pricesStored} prices across ${syncResult.symbolsWithPurchases}/${syncResult.symbolsRequested} symbols.`);
    } catch (error) {
      setStatus((error as Error).message);
      setResult(null);
    }
  };

  return (
    <form onSubmit={onSubmit} className="stack gap-sm">
      <label className="field">
        <span>Sync Symbols</span>
        <input value={symbolsText} onChange={(e) => setSymbolsText(e.target.value)} placeholder="AAPL,MSFT,ASML" />
      </label>
      <button type="submit">Run Sync</button>
      {status ? <p className="muted">{status}</p> : null}
      {result ? (
        <table>
          <thead>
            <tr><th>Symbol</th><th>Inserted</th><th>Status</th></tr>
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
