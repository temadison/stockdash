import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import type { DailyClosePricePointDto } from '../../shared/types/api';
import { getHistory, getSymbols } from '../../shared/api/portfolioApi';
import { money, percent } from '../../shared/utils/format';
import { computeCagr, computeReturn } from '../../shared/utils/analytics';
import { PageShell } from '../../shared/ui/PageShell';

export function HistoryPage() {
  const [params] = useSearchParams();
  const querySymbol = useMemo(() => params.get('symbol') ?? '', [params]);
  const queryStartDate = useMemo(() => params.get('startDate') ?? '', [params]);
  const queryEndDate = useMemo(() => params.get('endDate') ?? '', [params]);

  const [symbol, setSymbol] = useState(querySymbol.toUpperCase());
  const [startDate, setStartDate] = useState(queryStartDate);
  const [endDate, setEndDate] = useState(queryEndDate);
  const [rows, setRows] = useState<DailyClosePricePointDto[]>([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const load = async (targetSymbol = symbol, targetStartDate = startDate, targetEndDate = endDate) => {
    try {
      setLoading(true);
      setError('');
      const response = await getHistory(targetSymbol, targetStartDate || undefined, targetEndDate || undefined);
      setRows([...response].sort((a, b) => a.date.localeCompare(b.date)));
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
        await load(nextSymbol, queryStartDate, queryEndDate);
        return;
      }

      try {
        const symbols = await getSymbols();
        const fallback = symbols[0]?.toUpperCase() ?? '';
        setSymbol(fallback);
        if (fallback) {
          await load(fallback, queryStartDate, queryEndDate);
        } else {
          setRows([]);
        }
      } catch (e) {
        setError((e as Error).message);
      }
    };

    void syncFromQuery();
  }, [querySymbol, queryStartDate, queryEndDate]);

  const points = rows.map((row, index) => ({
    ...row,
    x: rows.length <= 1 ? 0 : (index / (rows.length - 1)) * 100,
    y: 0
  }));
  const min = rows.length ? Math.min(...rows.map((row) => row.closePrice)) : 0;
  const max = rows.length ? Math.max(...rows.map((row) => row.closePrice)) : 0;
  const spread = Math.max(max - min, 0.01);
  points.forEach((point) => {
    point.y = (1 - (point.closePrice - min) / spread) * 100;
  });
  const polyline = points.map((point) => `${point.x},${point.y}`).join(' ');
  const startPrice = rows[0]?.closePrice ?? 0;
  const endPrice = rows[rows.length - 1]?.closePrice ?? 0;
  const net = endPrice - startPrice;
  const totalReturn = rows.length > 1 ? computeReturn(startPrice, endPrice) : null;
  const cagr = rows.length > 1 ? computeCagr(startPrice, endPrice, rows[0].date, rows[rows.length - 1].date) : null;

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

          <div className="chart-wrap">
            <svg viewBox="0 0 100 100" preserveAspectRatio="none" className="chart-svg">
              <polyline points={polyline} fill="none" stroke="#0f766e" strokeWidth="1.5" vectorEffect="non-scaling-stroke" />
            </svg>
          </div>

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
        </>
      ) : null}
    </PageShell>
  );
}
