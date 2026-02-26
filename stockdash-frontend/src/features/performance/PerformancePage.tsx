import { useState } from 'react';
import type { PortfolioPerformancePointDto } from '../../shared/types/api';
import { getPerformance } from '../../shared/api/portfolioApi';
import { PageShell } from '../../shared/ui/PageShell';
import { money, todayIso } from '../../shared/utils/format';

export function PerformancePage() {
  const [account, setAccount] = useState('');
  const [startDate, setStartDate] = useState('2026-01-01');
  const [endDate, setEndDate] = useState(todayIso());
  const [rows, setRows] = useState<PortfolioPerformancePointDto[]>([]);
  const [error, setError] = useState('');

  const load = async () => {
    try {
      setError('');
      setRows(await getPerformance(account || undefined, startDate || undefined, endDate || undefined));
    } catch (e) {
      setRows([]);
      setError((e as Error).message);
    }
  };

  return (
    <PageShell
      title="Performance"
      subtitle="Raw series view from /api/portfolio/performance"
      actions={
        <div className="inline">
          <input placeholder="Account (optional)" value={account} onChange={(e) => setAccount(e.target.value)} />
          <input type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} />
          <input type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} />
          <button onClick={() => void load()}>Load</button>
        </div>
      }
    >
      {error ? <p className="error">{error}</p> : null}
      <table>
        <thead>
          <tr><th>Date</th><th>Total</th><th>Stocks (count)</th></tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.date}>
              <td>{row.date}</td>
              <td>{money.format(row.totalValue)}</td>
              <td>{row.stocks.length}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </PageShell>
  );
}
