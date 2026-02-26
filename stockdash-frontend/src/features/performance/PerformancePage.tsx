import { useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import type { PortfolioPerformancePointDto } from '../../shared/types/api';
import { getPerformance } from '../../shared/api/portfolioApi';
import { PageShell } from '../../shared/ui/PageShell';
import { money, percent } from '../../shared/utils/format';
import { computeCagr, computeReturn } from '../../shared/utils/analytics';

export function PerformancePage() {
  const [params] = useSearchParams();
  const queryAccount = useMemo(() => params.get('account') ?? '', [params]);
  const queryStart = useMemo(() => params.get('startDate') ?? '', [params]);
  const queryEnd = useMemo(() => params.get('endDate') ?? '', [params]);

  const [account, setAccount] = useState(queryAccount);
  const [startDate, setStartDate] = useState(queryStart);
  const [endDate, setEndDate] = useState(queryEnd);
  const [rows, setRows] = useState<PortfolioPerformancePointDto[]>([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const load = async (targetAccount = account, targetStartDate = startDate, targetEndDate = endDate) => {
    try {
      setLoading(true);
      setError('');
      const response = await getPerformance(targetAccount || undefined, targetStartDate || undefined, targetEndDate || undefined);
      setRows(response);
      if (!targetStartDate && response.length > 0) {
        setStartDate(response[0].date);
      }
      if (!targetEndDate && response.length > 0) {
        setEndDate(response[response.length - 1].date);
      }
    } catch (e) {
      setRows([]);
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    setAccount(queryAccount);
    setStartDate(queryStart);
    setEndDate(queryEnd);
    void load(queryAccount, queryStart, queryEnd);
  }, [queryAccount, queryStart, queryEnd]);

  const symbols = Array.from(new Set(rows.flatMap((row) => row.stocks.map((stock) => stock.symbol)))).sort();
  const startValue = rows[0]?.totalValue ?? 0;
  const endValue = rows[rows.length - 1]?.totalValue ?? 0;
  const net = endValue - startValue;
  const totalReturn = rows.length > 1 ? computeReturn(startValue, endValue) : null;
  const cagr = rows.length > 1 ? computeCagr(startValue, endValue, rows[0].date, rows[rows.length - 1].date) : null;

  const max = Math.max(...rows.map((row) => row.totalValue), 1);
  const pointsBySymbol = symbols.map((symbol) => {
    const values = rows.map((row) => row.stocks.find((stock) => stock.symbol === symbol)?.marketValue ?? 0);
    const points = values.map((value, index) => {
      const baseline = symbols
        .slice(0, symbols.indexOf(symbol))
        .reduce((sum, priorSymbol) => sum + (rows[index].stocks.find((stock) => stock.symbol === priorSymbol)?.marketValue ?? 0), 0);
      const stackedValue = value + baseline;
      const x = rows.length <= 1 ? 0 : (index / (rows.length - 1)) * 100;
      const y = (1 - stackedValue / max) * 100;
      return `${x},${y}`;
    });
    return { symbol, polyline: points.join(' ') };
  });

  const colors = ['#0f766e', '#2563eb', '#d97706', '#be185d', '#16a34a', '#9333ea', '#0284c7'];

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
      {!error && loading ? <p className="muted">Loading performance...</p> : null}
      {!error && !loading && rows.length === 0 ? <p className="muted">No performance rows returned.</p> : null}
      {rows.length > 0 ? (
        <>
          <div className="summary-grid">
            <article className="summary-card">
              <div className="summary-label">Net Gain/Loss</div>
              <div className={`summary-value ${net >= 0 ? 'status-ok' : 'status-bad'}`}>
                {net >= 0 ? '+' : '-'}{money.format(Math.abs(net))}
              </div>
            </article>
            <article className="summary-card">
              <div className="summary-label">Return</div>
              <div className={`summary-value ${(totalReturn ?? 0) >= 0 ? 'status-ok' : 'status-bad'}`}>
                {totalReturn == null ? 'N/A' : `${totalReturn >= 0 ? '+' : '-'}${percent.format(Math.abs(totalReturn))}`}
              </div>
            </article>
            <article className="summary-card">
              <div className="summary-label">CAGR</div>
              <div className={`summary-value ${(cagr ?? 0) >= 0 ? 'status-ok' : 'status-bad'}`}>
                {cagr == null ? 'N/A' : `${cagr >= 0 ? '+' : '-'}${percent.format(Math.abs(cagr))}`}
              </div>
            </article>
          </div>

          <div className="legend">
            {symbols.map((symbol, index) => (
              <Link
                key={symbol}
                className="legend-item"
                to={`/history?symbol=${encodeURIComponent(symbol)}&startDate=${encodeURIComponent(rows[0].date)}&endDate=${encodeURIComponent(rows[rows.length - 1].date)}&account=${encodeURIComponent(account || 'TOTAL')}`}
              >
                <span className="legend-dot" style={{ background: colors[index % colors.length] }} />
                {symbol}
              </Link>
            ))}
          </div>

          <div className="chart-wrap">
            <svg viewBox="0 0 100 100" preserveAspectRatio="none" className="chart-svg">
              {pointsBySymbol.map((series, index) => (
                <polyline
                  key={series.symbol}
                  points={series.polyline}
                  fill="none"
                  stroke={colors[index % colors.length]}
                  strokeWidth="1.2"
                  vectorEffect="non-scaling-stroke"
                />
              ))}
            </svg>
          </div>

          <table>
            <thead>
              <tr><th>Date</th><th>Total</th><th>Stocks</th></tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.date}>
                  <td>{row.date}</td>
                  <td>{money.format(row.totalValue)}</td>
                  <td>
                    {row.stocks.map((stock) => (
                      <span key={stock.symbol}>
                        <Link to={`/history?symbol=${encodeURIComponent(stock.symbol)}`}>{stock.symbol}</Link>{' '}
                      </span>
                    ))}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      ) : null}
    </PageShell>
  );
}
