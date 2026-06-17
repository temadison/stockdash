import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import type { PortfolioSnapshotDto } from '../../shared/types/api';
import { getDailySummary } from '../../shared/api/portfolioApi';
import { money, quantity, todayIso } from '../../shared/utils/format';
import { PageShell } from '../../shared/ui/PageShell';
import { UploadPanel } from '../upload/UploadPanel';
import { SyncPanel } from '../sync/SyncPanel';

export function SummaryPage() {
  const [date, setDate] = useState(todayIso());
  const [loadedDate, setLoadedDate] = useState(todayIso());
  const [snapshots, setSnapshots] = useState<PortfolioSnapshotDto[]>([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const load = async (targetDate = date) => {
    try {
      setLoading(true);
      setError('');
      setSnapshots(await getDailySummary(targetDate));
      setLoadedDate(targetDate);
    } catch (e) {
      setError((e as Error).message);
      setSnapshots([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load(date);
  }, []);

  return (
    <PageShell
      title="Portfolio Summary"
      subtitle="Simple, modular React client against the Spring Boot API"
      actions={
        <div className="inline">
          <input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
          <button onClick={() => void load(date)}>Load</button>
          <Link to="/performance?account=TOTAL" className="inline-link">Total Portfolio</Link>
        </div>
      }
    >
      <div className="grid two">
        <article className="card">
          <UploadPanel onDone={() => load(date)} />
        </article>
        <article className="card">
          <SyncPanel />
        </article>
      </div>

      {error ? <p className="error">{error}</p> : null}
      {!error && loading ? <p className="muted">Loading summary...</p> : null}
      {!error && !loading && snapshots.length === 0 ? (
        <p className="muted">No account data found for the selected date.</p>
      ) : null}
      {!error && snapshots.length > 0 ? <p className="muted">Showing as-of {loadedDate}.</p> : null}

      <div className="stack gap-md">
        {snapshots.map((snapshot) => (
          <article key={snapshot.accountName} className="card">
            <div className="inline spread">
              <h2>
                <Link
                  className="inline-link"
                  to={`/performance?account=${encodeURIComponent(snapshot.accountName)}&endDate=${encodeURIComponent(loadedDate)}`}
                >
                  {snapshot.accountName}
                </Link>
              </h2>
              <strong>{money.format(snapshot.totalValue)}</strong>
            </div>
            <p className="muted">Cash balance: {money.format(snapshot.cashBalance)}</p>
            <p className="muted">
              Account link opens performance filtered to this account.
            </p>
            <table>
              <thead>
                <tr><th>Symbol</th><th>Qty</th><th>Price</th><th>Value</th></tr>
              </thead>
              <tbody>
                {snapshot.positions.map((position) => (
                  <tr key={position.symbol}>
                    <td>
                      <Link to={`/history?symbol=${encodeURIComponent(position.symbol)}`}>{position.symbol}</Link>
                    </td>
                    <td>{quantity.format(position.quantity)}</td>
                    <td>{money.format(position.currentPrice)}</td>
                    <td>{money.format(position.marketValue)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </article>
        ))}
      </div>
    </PageShell>
  );
}
