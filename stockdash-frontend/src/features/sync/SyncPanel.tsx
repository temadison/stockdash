import { useState } from 'react';
import { syncPrices } from '../../shared/api/portfolioApi';

export function SyncPanel() {
  const [symbolsText, setSymbolsText] = useState('AAPL,MSFT');
  const [status, setStatus] = useState('');

  const onSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    const stocks = symbolsText
      .split(',')
      .map((value) => value.trim())
      .filter(Boolean);

    if (!stocks.length) {
      setStatus('Provide at least one symbol.');
      return;
    }

    try {
      const result = await syncPrices(stocks);
      setStatus(`Stored ${result.pricesStored} prices across ${result.symbolsWithPurchases}/${result.symbolsRequested} symbols.`);
    } catch (error) {
      setStatus((error as Error).message);
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
    </form>
  );
}
