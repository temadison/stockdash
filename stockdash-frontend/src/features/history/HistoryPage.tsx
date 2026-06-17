import { useEffect, useMemo, useState } from 'react';
import type { MouseEvent as ReactMouseEvent } from 'react';
import { useSearchParams } from 'react-router-dom';
import type { DailyClosePricePointDto } from '../../shared/types/api';
import { getDailySummary, getHistory } from '../../shared/api/portfolioApi';
import { money, percent, todayIso } from '../../shared/utils/format';
import { computeCagr, computeReturn } from '../../shared/utils/analytics';
import { PageShell } from '../../shared/ui/PageShell';

function normalizeAccount(accountName: string) {
  return accountName.trim().toUpperCase() === 'TOTAL' ? '' : accountName;
}

export function HistoryPage() {
  const [params] = useSearchParams();
  const querySymbol = useMemo(() => params.get('symbol') ?? '', [params]);
  const queryStartDate = useMemo(() => params.get('startDate') ?? '', [params]);
  const queryEndDate = useMemo(() => params.get('endDate') ?? '', [params]);
  const queryAccount = useMemo(() => normalizeAccount(params.get('account') ?? ''), [params]);

  const [symbol, setSymbol] = useState(querySymbol.toUpperCase());
  const [startDate, setStartDate] = useState(queryStartDate);
  const [endDate, setEndDate] = useState(queryEndDate);
  const [symbols, setSymbols] = useState<string[]>([]);
  const [rows, setRows] = useState<DailyClosePricePointDto[]>([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [hoverIndex, setHoverIndex] = useState<number | null>(null);

  const symbolOptions = useMemo(() => {
    if (!symbol) {
      return symbols;
    }
    return symbols.includes(symbol) ? symbols : [symbol, ...symbols];
  }, [symbol, symbols]);

  const load = async (targetSymbol = symbol, targetStartDate = startDate, targetEndDate = endDate) => {
    try {
      setLoading(true);
      setError('');
      const response = await getHistory(targetSymbol, targetStartDate || undefined, targetEndDate || undefined);
      const sorted = [...response].sort((a, b) => a.date.localeCompare(b.date));
      setRows(sorted);
      if (!targetStartDate && sorted.length > 0) {
        setStartDate(sorted[0].date);
      }
      if (!targetEndDate && sorted.length > 0) {
        setEndDate(sorted[sorted.length - 1].date);
      }
    } catch (e) {
      setRows([]);
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    const syncFromQuery = async () => {
      const nextSymbol = querySymbol.toUpperCase();
      setStartDate(queryStartDate);
      setEndDate(queryEndDate);

      if (nextSymbol) {
        setSymbol(nextSymbol);
      }

      try {
        const snapshots = await getDailySummary(todayIso());
        const filteredSnapshots = queryAccount
          ? snapshots.filter((snapshot) => snapshot.accountName.toLowerCase() === queryAccount.toLowerCase())
          : snapshots;
        const currentSymbols = Array.from(
          new Set(
            filteredSnapshots.flatMap((snapshot) => snapshot.positions.map((position) => position.symbol.toUpperCase()))
          )
        ).sort();
        setSymbols(currentSymbols);

        const fallback = nextSymbol || currentSymbols[0] || '';
        setSymbol(fallback);

        if (fallback) {
          await load(fallback, queryStartDate, queryEndDate);
        } else {
          setRows([]);
        }
      } catch (e) {
        setSymbols([]);
        setRows([]);
        setError((e as Error).message);
      }
    };

    void syncFromQuery();
  }, [querySymbol, queryStartDate, queryEndDate, queryAccount]);

  const points = rows.map((row, index) => ({
    ...row,
    x: rows.length <= 1 ? 0 : (index / (rows.length - 1)) * 100,
    y: 0
  }));
  const tableRows = [...rows].reverse();
  const min = rows.length ? Math.min(...rows.map((row) => row.closePrice)) : 0;
  const max = rows.length ? Math.max(...rows.map((row) => row.closePrice)) : 0;
  const spread = Math.max(max - min, 0.01);
  points.forEach((point) => {
    point.y = (1 - (point.closePrice - min) / spread) * 100;
  });
  const polyline = points.map((point) => `${point.x},${point.y}`).join(' ');
  const hoveredPoint = hoverIndex == null ? null : points[hoverIndex] ?? null;
  const yAxisTicks = [max, min + spread / 2, min];
  const startPrice = rows[0]?.closePrice ?? 0;
  const endPrice = rows[rows.length - 1]?.closePrice ?? 0;
  const net = endPrice - startPrice;
  const totalReturn = rows.length > 1 ? computeReturn(startPrice, endPrice) : null;
  const cagr = rows.length > 1 ? computeCagr(startPrice, endPrice, rows[0].date, rows[rows.length - 1].date) : null;

  const clearHover = () => setHoverIndex(null);

  const handleChartHover = (event: ReactMouseEvent<SVGSVGElement>) => {
    if (points.length === 0) {
      setHoverIndex(null);
      return;
    }

    const bounds = event.currentTarget.getBoundingClientRect();
    if (bounds.width === 0 || bounds.height === 0) {
      setHoverIndex(null);
      return;
    }

    const x = ((event.clientX - bounds.left) / bounds.width) * 100;
    const y = ((event.clientY - bounds.top) / bounds.height) * 100;

    let nearestIndex = 0;
    let nearestDistance = Number.POSITIVE_INFINITY;

    points.forEach((point, index) => {
      const dx = point.x - x;
      const dy = point.y - y;
      const distance = Math.hypot(dx, dy);
      if (distance < nearestDistance) {
        nearestDistance = distance;
        nearestIndex = index;
      }
    });

    setHoverIndex(nearestDistance <= 8 ? nearestIndex : null);
  };

  return (
    <PageShell
      title="Price History"
      subtitle="Split-adjusted close-price history from /api/portfolio/prices/history"
      actions={
        <div className="inline">
          <select
            className="history-symbol-select"
            value={symbol}
            onChange={(e) => setSymbol(e.target.value)}
            disabled={symbolOptions.length === 0}
          >
            {symbolOptions.length === 0 ? <option value="">No current holdings</option> : null}
            {symbolOptions.map((option) => (
              <option key={option} value={option}>
                {option === symbol && !symbols.includes(option) ? `${option} (not currently held)` : option}
              </option>
            ))}
          </select>
          <input type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} />
          <input type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} />
          <button onClick={() => void load()} disabled={!symbol}>Load</button>
        </div>
      }
    >
      {error ? <p className="error">{error}</p> : null}
      {!error && loading ? <p className="muted">Loading history...</p> : null}
      {!error && !loading && rows.length === 0 ? <p className="muted">No history rows found for this symbol/date range.</p> : null}
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
                  <div className="chart-plot">
                    <svg
                      viewBox="0 0 100 100"
                      preserveAspectRatio="none"
                      className="chart-svg chart-svg-interactive"
                      onMouseMove={handleChartHover}
                      onMouseLeave={clearHover}
                    >
                      <rect x="0" y="0" width="100" height="100" fill="transparent" />
                      <line x1="0" y1="0" x2="100" y2="0" className="chart-grid-line" vectorEffect="non-scaling-stroke" />
                      <line x1="0" y1="50" x2="100" y2="50" className="chart-grid-line" vectorEffect="non-scaling-stroke" />
                      <line x1="0" y1="100" x2="100" y2="100" className="chart-grid-line" vectorEffect="non-scaling-stroke" />
                      <line x1="0" y1="0" x2="0" y2="100" className="chart-axis-line" vectorEffect="non-scaling-stroke" />
                      <line x1="0" y1="100" x2="100" y2="100" className="chart-axis-line" vectorEffect="non-scaling-stroke" />
                      <polyline points={polyline} fill="none" stroke="#0f766e" strokeWidth="1.5" vectorEffect="non-scaling-stroke" />
                      {hoveredPoint ? (
                        <line
                          x1={hoveredPoint.x}
                          y1={hoveredPoint.y}
                          x2={hoveredPoint.x}
                          y2="100"
                          stroke="#99f6e4"
                          strokeWidth="1"
                          strokeDasharray="2 2"
                          vectorEffect="non-scaling-stroke"
                        />
                      ) : null}
                    </svg>
                    {hoveredPoint ? (
                      <div
                        className="chart-hover-dot"
                        style={{
                          left: `${hoveredPoint.x}%`,
                          top: `${hoveredPoint.y}%`
                        }}
                      />
                    ) : null}
                    {hoveredPoint ? (
                      <div
                        className="chart-tooltip"
                        style={{
                          left: `${Math.min(Math.max(hoveredPoint.x, 14), 86)}%`,
                          top: `${Math.max(hoveredPoint.y - 10, 10)}%`
                        }}
                      >
                        <strong>{hoveredPoint.date}</strong>
                        <span>{money.format(hoveredPoint.closePrice)}</span>
                      </div>
                    ) : null}
                  </div>
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

          <table>
            <thead>
              <tr><th>Date</th><th>Close</th></tr>
            </thead>
            <tbody>
              {tableRows.map((row) => (
                <tr key={row.date}>
                  <td>{row.date}</td>
                  <td>{money.format(row.closePrice)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      ) : null}
    </PageShell>
  );
}
