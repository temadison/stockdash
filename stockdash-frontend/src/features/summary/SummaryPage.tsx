import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import type { PortfolioPerformancePointDto, PortfolioSnapshotDto } from '../../shared/types/api';
import { getDailySummary, getPerformance } from '../../shared/api/portfolioApi';
import { money, percent, quantity, todayIso } from '../../shared/utils/format';
import { computeCagr, computeReturn } from '../../shared/utils/analytics';
import { PageShell } from '../../shared/ui/PageShell';
import { UploadPanel } from '../upload/UploadPanel';
import { SyncPanel } from '../sync/SyncPanel';

function signedMoney(value: number): string {
  return `${value >= 0 ? '+' : '-'}${money.format(Math.abs(value))}`;
}

function signedPercent(value: number | null): string {
  if (value == null) return 'N/A';
  return `${value >= 0 ? '+' : '-'}${percent.format(Math.abs(value))}`;
}

export function SummaryPage() {
  const [date, setDate] = useState(todayIso());
  const [loadedDate, setLoadedDate] = useState(todayIso());
  const [snapshots, setSnapshots] = useState<PortfolioSnapshotDto[]>([]);
  const [performanceRows, setPerformanceRows] = useState<PortfolioPerformancePointDto[]>([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const load = async (targetDate = date) => {
    try {
      setLoading(true);
      setError('');
      const [nextSnapshots, nextPerformanceRows] = await Promise.all([
        getDailySummary(targetDate),
        getPerformance(undefined, undefined, targetDate)
      ]);
      setSnapshots(nextSnapshots);
      setPerformanceRows(nextPerformanceRows);
      setLoadedDate(targetDate);
    } catch (e) {
      setError((e as Error).message);
      setSnapshots([]);
      setPerformanceRows([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load(date);
  }, []);

  const latestPerformanceRow = performanceRows[performanceRows.length - 1] ?? null;
  const portfolioValue = latestPerformanceRow?.totalValue ?? 0;
  const netAmountSpent = latestPerformanceRow?.netAmountSpent ?? 0;
  const net = portfolioValue - netAmountSpent;
  const totalReturn = latestPerformanceRow ? computeReturn(netAmountSpent, portfolioValue) : null;
  const cagr = performanceRows.length > 1 && latestPerformanceRow
    ? computeCagr(netAmountSpent, portfolioValue, performanceRows[0].date, latestPerformanceRow.date)
    : null;

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
        {!error && latestPerformanceRow ? (
          <div className="summary-grid">
            <article className="summary-card">
              <div className="summary-label">Net Gain/Loss</div>
              <div className={`summary-value ${net >= 0 ? 'status-ok' : 'status-bad'}`}>
                {signedMoney(net)}
              </div>
            </article>
            <article className="summary-card">
              <div className="summary-label">Return</div>
              <div className={`summary-value ${(totalReturn ?? 0) >= 0 ? 'status-ok' : 'status-bad'}`}>
                {signedPercent(totalReturn)}
              </div>
            </article>
            <article className="summary-card">
              <div className="summary-label">CAGR</div>
              <div className={`summary-value ${(cagr ?? 0) >= 0 ? 'status-ok' : 'status-bad'}`}>
                {signedPercent(cagr)}
              </div>
            </article>
          </div>
        ) : null}
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
                <tr>
                  <th>Symbol</th>
                  <th>Qty</th>
                  <th>Price</th>
                  <th>Value</th>
                  <th>Cost Basis</th>
                  <th>Gain/Loss</th>
                  <th>Return</th>
                  <th>CAGR</th>
                </tr>
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
                    <td>{money.format(position.costBasis)}</td>
                    <td className={position.gainLoss >= 0 ? 'status-ok' : 'status-bad'}>{signedMoney(position.gainLoss)}</td>
                    <td className={(position.totalReturn ?? 0) >= 0 ? 'status-ok' : 'status-bad'}>{signedPercent(position.totalReturn)}</td>
                    <td className={(position.cagr ?? 0) >= 0 ? 'status-ok' : 'status-bad'}>{signedPercent(position.cagr)}</td>
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
