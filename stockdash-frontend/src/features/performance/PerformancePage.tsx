import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import type { PortfolioPerformancePointDto } from '../../shared/types/api';
import { getDailySummary, getPerformance } from '../../shared/api/portfolioApi';
import { PageShell } from '../../shared/ui/PageShell';
import { money, percent, todayIso } from '../../shared/utils/format';
import { computeCagr, computeReturn } from '../../shared/utils/analytics';

const MAX_CHART_POINTS = 600;
const BIWEEKLY_INTERVAL_DAYS = 14;

function sampleRows(rows: PortfolioPerformancePointDto[], maxPoints: number): PortfolioPerformancePointDto[] {
  if (rows.length <= maxPoints) return rows;
  const step = (rows.length - 1) / (maxPoints - 1);
  const sampled: PortfolioPerformancePointDto[] = [];
  for (let i = 0; i < maxPoints; i += 1) {
    sampled.push(rows[Math.round(i * step)]);
  }
  return sampled;
}

function colorForSeries(index: number): string {
  const hue = (index * 137.508) % 360;
  return `hsl(${hue.toFixed(1)} 72% 42%)`;
}

function polarToCartesian(centerX: number, centerY: number, radius: number, angleInDegrees: number) {
  const angleInRadians = ((angleInDegrees - 90) * Math.PI) / 180;
  return {
    x: centerX + radius * Math.cos(angleInRadians),
    y: centerY + radius * Math.sin(angleInRadians)
  };
}

function describePieSlice(centerX: number, centerY: number, radius: number, startAngle: number, endAngle: number) {
  const start = polarToCartesian(centerX, centerY, radius, endAngle);
  const end = polarToCartesian(centerX, centerY, radius, startAngle);
  const largeArcFlag = endAngle - startAngle > 180 ? 1 : 0;

  return [
    `M ${centerX} ${centerY}`,
    `L ${start.x} ${start.y}`,
    `A ${radius} ${radius} 0 ${largeArcFlag} 0 ${end.x} ${end.y}`,
    'Z'
  ].join(' ');
}

function parseIsoDate(isoDate: string): number {
  return Date.parse(`${isoDate}T00:00:00Z`);
}

function subtractDays(isoDate: string, days: number): string {
  const date = new Date(parseIsoDate(isoDate));
  date.setUTCDate(date.getUTCDate() - days);
  return date.toISOString().slice(0, 10);
}

function sampleBiweeklyRows(rows: PortfolioPerformancePointDto[], anchorDate: string): PortfolioPerformancePointDto[] {
  if (rows.length === 0) return [];

  const earliestDate = rows[0].date;
  const latestDate = rows[rows.length - 1].date;
  const effectiveAnchor = parseIsoDate(anchorDate) < parseIsoDate(latestDate) ? anchorDate : latestDate;
  const selected: PortfolioPerformancePointDto[] = [];

  let rowIndex = rows.length - 1;
  let targetDate = effectiveAnchor;

  while (rowIndex >= 0 && parseIsoDate(targetDate) >= parseIsoDate(earliestDate)) {
    while (rowIndex >= 0 && parseIsoDate(rows[rowIndex].date) > parseIsoDate(targetDate)) {
      rowIndex -= 1;
    }

    if (rowIndex < 0) break;

    const row = rows[rowIndex];
    if (selected[selected.length - 1]?.date !== row.date) {
      selected.push(row);
    }

    targetDate = subtractDays(targetDate, BIWEEKLY_INTERVAL_DAYS);
  }

  return selected;
}

function normalizeAccount(accountName: string) {
  return accountName.trim().toUpperCase() === 'TOTAL' ? '' : accountName;
}

export function PerformancePage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const queryAccount = useMemo(() => normalizeAccount(params.get('account') ?? ''), [params]);
  const queryStart = useMemo(() => params.get('startDate') ?? '', [params]);
  const queryEnd = useMemo(() => params.get('endDate') ?? '', [params]);

  const [account, setAccount] = useState(queryAccount);
  const [accounts, setAccounts] = useState<string[]>([]);
  const [startDate, setStartDate] = useState(queryStart);
  const [endDate, setEndDate] = useState(queryEnd);
  const [hasCustomStartDate, setHasCustomStartDate] = useState(queryStart.length > 0);
  const [hasCustomEndDate, setHasCustomEndDate] = useState(queryEnd.length > 0);
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
    setHasCustomStartDate(queryStart.length > 0);
    setHasCustomEndDate(queryEnd.length > 0);
    void load(queryAccount, queryStart, queryEnd);
  }, [queryAccount, queryStart, queryEnd]);

  useEffect(() => {
    const loadAccounts = async () => {
      try {
        const snapshots = await getDailySummary(todayIso());
        const nextAccounts = Array.from(new Set(snapshots.map((snapshot) => snapshot.accountName))).sort((a, b) => a.localeCompare(b));
        setAccounts(nextAccounts);
      } catch {
        setAccounts((current) => current);
      }
    };

    void loadAccounts();
  }, []);

  const chartRows = useMemo(() => sampleRows(rows, MAX_CHART_POINTS), [rows]);
  const tableRows = useMemo(
    () => sampleBiweeklyRows(rows, todayIso()),
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
  const latestRow = rows[rows.length - 1] ?? null;
  const latestCashBalance = latestRow?.cashBalance ?? 0;
  const hasCashSeries = useMemo(() => rows.some((row) => Math.abs(row.cashBalance) > 0.0001), [rows]);

  const max = useMemo(() => {
    let currentMax = 1;
    for (const row of chartRows) {
      if (row.cashBalance > currentMax) currentMax = row.cashBalance;
      for (const stock of row.stocks) {
        if (stock.marketValue > currentMax) currentMax = stock.marketValue;
      }
    }
    return currentMax;
  }, [chartRows]);

  const pointsBySymbol = useMemo(() => {
    const chartSymbols = hasCashSeries ? [...symbols, 'CASH'] : symbols;
    return chartSymbols.map((symbol) => {
      const points = chartRows.map((row, index) => {
        const value = symbol === 'CASH'
          ? Math.max(row.cashBalance, 0)
          : row.stocks.find((stock) => stock.symbol === symbol)?.marketValue ?? 0;
        const x = chartRows.length <= 1 ? 0 : (index / (chartRows.length - 1)) * 100;
        const y = (1 - value / max) * 100;
        return `${x},${y}`;
      });
      return { symbol, polyline: points.join(' ') };
    });
  }, [chartRows, hasCashSeries, max, symbols]);
  const yAxisTicks = useMemo(() => [max, max / 2, 0], [max]);

  const colorBySymbol = useMemo(() => {
    const entries = symbols.map((symbol, index) => [symbol, colorForSeries(index)] as const);
    if (hasCashSeries) {
      entries.push(['CASH', '#f97316'] as const);
    }
    return new Map(entries);
  }, [hasCashSeries, symbols]);
  const allocationSlices = useMemo(() => {
    if (!latestRow || latestRow.totalValue <= 0) return [];

    let runningAngle = 0;
    return [
      ...latestRow.stocks,
      ...(latestRow.cashBalance > 0 ? [{ symbol: 'CASH', marketValue: latestRow.cashBalance }] : [])
    ]
      .filter((stock) => stock.marketValue > 0)
      .sort((a, b) => b.marketValue - a.marketValue)
      .map((stock) => {
        const weight = stock.marketValue / latestRow.totalValue;
        const startAngle = runningAngle;
        const sweepAngle = weight * 360;
        const endAngle = runningAngle + sweepAngle;
        runningAngle = endAngle;

        return {
          symbol: stock.symbol,
          marketValue: stock.marketValue,
          weight,
          path: describePieSlice(50, 50, 42, startAngle, endAngle),
          color: colorBySymbol.get(stock.symbol) ?? '#334155'
        };
      });
  }, [colorBySymbol, latestRow]);

  const applyFilters = () => {
    const next = new URLSearchParams();
    if (account) next.set('account', account);
    if (hasCustomStartDate && startDate) next.set('startDate', startDate);
    if (hasCustomEndDate && endDate) next.set('endDate', endDate);
    navigate(`/performance?${next.toString()}`);
  };

  return (
    <PageShell
      title="Performance"
      subtitle="Raw series view from /api/portfolio/performance"
      actions={
        <div className="inline">
          <select value={account} onChange={(e) => setAccount(e.target.value)}>
            <option value="">All Accounts</option>
            {accounts.map((accountName) => (
              <option key={accountName} value={accountName}>{accountName}</option>
            ))}
          </select>
          <input
            type="date"
            value={startDate}
            onChange={(e) => {
              setStartDate(e.target.value);
              setHasCustomStartDate(e.target.value.length > 0);
            }}
          />
          <input
            type="date"
            value={endDate}
            onChange={(e) => {
              setEndDate(e.target.value);
              setHasCustomEndDate(e.target.value.length > 0);
            }}
          />
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
            <article className="summary-card">
              <div className="summary-label">Cash Balance</div>
              <div className="summary-value">{money.format(latestCashBalance)}</div>
            </article>
          </div>

          {allocationSlices.length > 0 ? (
            <section className="allocation-card">
              <div className="inline spread">
                <div>
                  <h2>Current Allocation</h2>
                  <p className="muted">Latest portfolio value split by stock as of {latestRow?.date}.</p>
                </div>
                <strong>{money.format(latestRow?.totalValue ?? 0)}</strong>
              </div>
              <div className="allocation-layout">
                <div className="allocation-chart-wrap" aria-hidden="true">
                  <svg viewBox="0 0 100 100" className="allocation-chart">
                    {allocationSlices.map((slice) => (
                      <path
                        key={slice.symbol}
                        d={slice.path}
                        fill={slice.color}
                        stroke="#ffffff"
                        strokeWidth="1.2"
                      />
                    ))}
                    <circle cx="50" cy="50" r="18" fill="#fffdf8" />
                  </svg>
                </div>
                <div className="allocation-labels">
                  {allocationSlices.map((slice) => (
                    slice.symbol === 'CASH' ? (
                      <div key={slice.symbol} className="allocation-label">
                        <span className="legend-dot" style={{ background: slice.color }} />
                        <span className="allocation-symbol">{slice.symbol}</span>
                        <span className="allocation-weight">{percent.format(slice.weight)}</span>
                        <span className="allocation-value">{money.format(slice.marketValue)}</span>
                      </div>
                    ) : (
                      <Link
                        key={slice.symbol}
                        className="allocation-label allocation-link"
                        to={`/history?symbol=${encodeURIComponent(slice.symbol)}&startDate=${encodeURIComponent(rows[0].date)}&endDate=${encodeURIComponent(rows[rows.length - 1].date)}&account=${encodeURIComponent(account || 'TOTAL')}`}
                      >
                        <span className="legend-dot" style={{ background: slice.color }} />
                        <span className="allocation-symbol">{slice.symbol}</span>
                        <span className="allocation-weight">{percent.format(slice.weight)}</span>
                        <span className="allocation-value">{money.format(slice.marketValue)}</span>
                      </Link>
                    )
                  ))}
                </div>
              </div>
            </section>
          ) : null}

          {rows.length > MAX_CHART_POINTS ? (
            <p className="muted">Chart sampled to {MAX_CHART_POINTS} points from {rows.length} rows.</p>
          ) : null}

          <div className="chart-frame">
            <div className="chart-stage">
              <div className="chart-body">
                <div className="chart-y-axis" aria-hidden="true">
                  {yAxisTicks.map((value, index) => (
                    <span
                      key={index}
                      className="chart-y-tick"
                      style={{ top: `calc(var(--chart-pad) + (var(--chart-height) * ${index} / 2))` }}
                    >
                      {money.format(value)}
                    </span>
                  ))}
                </div>
                <div className="chart-wrap">
                  <svg viewBox="0 0 100 100" preserveAspectRatio="none" className="chart-svg">
                    <line x1="0" y1="0" x2="100" y2="0" className="chart-grid-line" vectorEffect="non-scaling-stroke" />
                    <line x1="0" y1="50" x2="100" y2="50" className="chart-grid-line" vectorEffect="non-scaling-stroke" />
                    <line x1="0" y1="100" x2="100" y2="100" className="chart-grid-line" vectorEffect="non-scaling-stroke" />
                    <line x1="0" y1="0" x2="0" y2="100" className="chart-axis-line" vectorEffect="non-scaling-stroke" />
                    <line x1="0" y1="100" x2="100" y2="100" className="chart-axis-line" vectorEffect="non-scaling-stroke" />
                    {pointsBySymbol.map((series, index) => (
                      <polyline
                        key={series.symbol}
                        points={series.polyline}
                        fill="none"
                        stroke={colorBySymbol.get(series.symbol) ?? colorForSeries(index)}
                        strokeWidth="1.2"
                        vectorEffect="non-scaling-stroke"
                      />
                    ))}
                  </svg>
                </div>
              </div>
              <div className="chart-x-axis" aria-hidden="true">
                <div className="chart-x-axis-spacer" />
                <div className="chart-x-axis-labels">
                  <span className="chart-x-tick" style={{ left: 'calc(var(--chart-pad) + 1px)' }}>{rows[0]?.date}</span>
                  <span className="chart-x-tick" style={{ left: 'calc(100% - var(--chart-pad) - 1px)' }}>{rows[rows.length - 1]?.date}</span>
                </div>
              </div>
            </div>
          </div>

          <table className="compact-table">
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
                    {row.cashBalance > 0 ? <span>Cash </span> : null}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <p className="muted">Showing one row every 14 days, working backward from the latest current-range date.</p>
        </>
      ) : null}
    </PageShell>
  );
}
