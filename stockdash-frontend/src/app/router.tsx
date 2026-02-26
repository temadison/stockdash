import { createBrowserRouter } from 'react-router-dom';
import { SummaryPage } from '../features/summary/SummaryPage';
import { PerformancePage } from '../features/performance/PerformancePage';
import { HistoryPage } from '../features/history/HistoryPage';

export const router = createBrowserRouter([
  { path: '/', element: <SummaryPage /> },
  { path: '/performance', element: <PerformancePage /> },
  { path: '/history', element: <HistoryPage /> }
]);
