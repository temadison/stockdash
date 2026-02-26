import type { PropsWithChildren, ReactNode } from 'react';
import { Link, NavLink } from 'react-router-dom';

type PageShellProps = PropsWithChildren<{
  title: string;
  subtitle?: string;
  actions?: ReactNode;
}>;

export function PageShell({ title, subtitle, actions, children }: PageShellProps) {
  return (
    <main className="shell">
      <header className="topbar">
        <Link to="/" className="brand">StockDash</Link>
        <nav className="nav">
          <NavLink to="/" end>Summary</NavLink>
          <NavLink to="/performance">Performance</NavLink>
          <NavLink to="/history?symbol=AAPL">History</NavLink>
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
