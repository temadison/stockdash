import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import type { PortfolioPerformancePointDto } from '../../shared/types/api';
import { getPerformance } from '../../shared/api/portfolioApi';
import { PageShell } from '../../shared/ui/PageShell';
import { money, percent } from '../../shared/utils/format';
import { computeCagr, computeReturn } from '../../shared/utils/analytics';

const MAX_CHART_POINTS = 600;
const MAX_TABLE_ROWS = 500;

function sampleRows(rows: PortfolioPerformancePointDto[], maxPoints: number): PortfolioPerformancePointDto[] {
  if (rows.length <= maxPoints) return rows;
  const step = (rows.length - 1) / (maxPoints - 1);
  const sampled: PortfolioPerformancePointDto[] = [];
  for (let i = 0; i < maxPoints; i += 1) {
    sampled.push(rows[Math.round(i * step)]);
  }
  return sampled;
}

export function PerformancePage() {
  const navigate = useNavigate();
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

  const chartRows = useMemo(() => sampleRows(rows, MAX_CHART_POINTS), [rows]);
  const tableRows = useMemo(
    () => (rows.length > MAX_TABLE_ROWS ? rows.slice(rows.length - MAX_TABLE_ROWS) : rows),
    [rows]
  );

  const symbols = useMemo(
    () => Array.from(new Set(rows.flatMap((row) => row.stocks.map((stock) => stock.symbol)))).sort(),
    [rows]
  );
  const endValue = rows[rows.length - 1]?.totalValue ?? 0;
  const netAmountSpent = rows[rows.length - 1]?.netAmountSpent ?? 0;
  const net = endValue - netAmountSpent;
  const totalReturn = computeReturn(netAmountSpent, endValue);
  const cagr = rows.length > 1 ? computeCagr(netAmountSpent, endValue, rows[0].date, rows[rows.length - 1].date) : null;

  const max = useMemo(() => {
    let currentMax = 1;
    for (const row of chartRows) {
      if (row.totalValue > currentMax) currentMax = row.totalValue;
    }
    return currentMax;
  }, [chartRows]);

  const pointsBySymbol = useMemo(() => {
    const symbolIndexes = new Map<string, number>();
    symbols.forEach((symbol, index) => symbolIndexes.set(symbol, index));

    return symbols.map((symbol) => {
      const symbolIndex = symbolIndexes.get(symbol) ?? 0;
      const points = chartRows.map((row, index) => {
        let baseline = 0;
        let value = 0;
        for (const stock of row.stocks) {
          const indexForStock = symbolIndexes.get(stock.symbol);
          if (indexForStock == null) continue;
          if (indexForStock < symbolIndex) baseline += stock.marketValue;
          if (stock.symbol === symbol) value = stock.marketValue;
        }
        const stackedValue = value + baseline;
        const x = chartRows.length <= 1 ? 0 : (index / (chartRows.length - 1)) * 100;
        const y = (1 - stackedValue / max) * 100;
        return `${x},${y}`;
      });
      return { symbol, polyline: points.join(' ') };
    });
  }, [chartRows, max, symbols]);

  const colors = ['#0f766e', '#2563eb', '#d97706', '#be185d', '#16a34a', '#9333ea', '#0284c7'];

  const applyFilters = () => {
    const next = new URLSearchParams();
    if (account) next.set('account', account);
    if (startDate) next.set('startDate', startDate);
    if (endDate) next.set('endDate', endDate);
    navigate(`/performance?${next.toString()}`);
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
          <button onClick={applyFilters}>Load</button>
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

          {rows.length > MAX_CHART_POINTS ? (
            <p className="muted">Chart sampled to {MAX_CHART_POINTS} points from {rows.length} rows.</p>
          ) : null}

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
              {tableRows.map((row) => (
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
          {rows.length > MAX_TABLE_ROWS ? (
            <p className="muted">Showing latest {MAX_TABLE_ROWS} rows of {rows.length} total.</p>
          ) : null}
        </>
      ) : null}
    </PageShell>
  );
}
