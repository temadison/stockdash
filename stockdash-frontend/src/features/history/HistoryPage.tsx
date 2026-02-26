import { useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import type { DailyClosePricePointDto } from '../../shared/types/api';
import { getHistory } from '../../shared/api/portfolioApi';
import { money } from '../../shared/utils/format';
import { PageShell } from '../../shared/ui/PageShell';

export function HistoryPage() {
  const [params] = useSearchParams();
  const initialSymbol = useMemo(() => params.get('symbol') ?? 'AAPL', [params]);

  const [symbol, setSymbol] = useState(initialSymbol.toUpperCase());
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [rows, setRows] = useState<DailyClosePricePointDto[]>([]);
  const [error, setError] = useState('');

  const load = async () => {
    try {
      setError('');
      setRows(await getHistory(symbol, startDate || undefined, endDate || undefined));
    } catch (e) {
      setRows([]);
      setError((e as Error).message);
    }
  };

  return (
    <PageShell
      title="Price History"
      subtitle="Raw close-price list from /api/portfolio/prices/history"
      actions={
        <div className="inline">
          <input value={symbol} onChange={(e) => setSymbol(e.target.value.toUpperCase())} placeholder="Symbol" />
          <input type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} />
          <input type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} />
          <button onClick={() => void load()}>Load</button>
        </div>
      }
    >
      {error ? <p className="error">{error}</p> : null}
      <table>
        <thead>
          <tr><th>Date</th><th>Close</th></tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.date}>
              <td>{row.date}</td>
              <td>{money.format(row.closePrice)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </PageShell>
  );
}
