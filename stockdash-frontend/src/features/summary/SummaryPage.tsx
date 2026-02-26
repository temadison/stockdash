import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import type { PortfolioSnapshotDto } from '../../shared/types/api';
import { getDailySummary } from '../../shared/api/portfolioApi';
import { money, todayIso } from '../../shared/utils/format';
import { PageShell } from '../../shared/ui/PageShell';
import { UploadPanel } from '../upload/UploadPanel';
import { SyncPanel } from '../sync/SyncPanel';

export function SummaryPage() {
  const [date, setDate] = useState(todayIso());
  const [snapshots, setSnapshots] = useState<PortfolioSnapshotDto[]>([]);
  const [error, setError] = useState('');

  const load = async () => {
    try {
      setError('');
      setSnapshots(await getDailySummary(date));
    } catch (e) {
      setError((e as Error).message);
      setSnapshots([]);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  return (
    <PageShell
      title="Portfolio Summary"
      subtitle="Simple, modular React client against the Spring Boot API"
      actions={
        <div className="inline">
          <input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
          <button onClick={() => void load()}>Load</button>
        </div>
      }
    >
      <div className="grid two">
        <article className="card">
          <UploadPanel onDone={load} />
        </article>
        <article className="card">
          <SyncPanel />
        </article>
      </div>

      {error ? <p className="error">{error}</p> : null}

      <div className="stack gap-md">
        {snapshots.map((snapshot) => (
          <article key={snapshot.accountName} className="card">
            <div className="inline spread">
              <h2>{snapshot.accountName}</h2>
              <strong>{money.format(snapshot.totalValue)}</strong>
            </div>
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
                    <td>{position.quantity}</td>
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
