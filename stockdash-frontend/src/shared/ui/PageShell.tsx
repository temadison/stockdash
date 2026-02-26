import type { PropsWithChildren, ReactNode } from 'react';
import { Link, NavLink, useLocation } from 'react-router-dom';

type PageShellProps = PropsWithChildren<{
  title: string;
  subtitle?: string;
  actions?: ReactNode;
}>;

export function PageShell({ title, subtitle, actions, children }: PageShellProps) {
  const location = useLocation();
  const historyLink = (() => {
    if (location.pathname !== '/performance') {
      return '/history';
    }
    const params = new URLSearchParams(location.search);
    const next = new URLSearchParams();
    const account = params.get('account');
    const startDate = params.get('startDate');
    const endDate = params.get('endDate');
    if (account) next.set('account', account);
    if (startDate) next.set('startDate', startDate);
    if (endDate) next.set('endDate', endDate);
    return `/history${next.toString() ? `?${next.toString()}` : ''}`;
  })();

  return (
    <main className="shell">
      <header className="topbar">
        <Link to="/" className="brand">StockDash</Link>
        <nav className="nav">
          <NavLink to="/" end>Summary</NavLink>
          <NavLink to="/performance">Performance</NavLink>
          <NavLink to={historyLink}>History</NavLink>
        </nav>
      </header>

      <section className="panel">
        <div className="panel-head">
          <div>
            <h1>{title}</h1>
            {subtitle ? <p className="muted">{subtitle}</p> : null}
          </div>
          {actions}
        </div>
        {children}
      </section>
    </main>
  );
}
