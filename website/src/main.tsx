import { lazy, StrictMode, Suspense } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './App';
// The other pages load only when opened, so the landing page stays light.
const Privacy = lazy(() => import('./pages/Privacy').then((m) => ({ default: m.Privacy })));
const License = lazy(() => import('./pages/License').then((m) => ({ default: m.License })));
const Releases = lazy(() => import('./pages/Releases').then((m) => ({ default: m.Releases })));
import './styles.css';

/** One small site: the landing page, plus Privacy, Licence and Releases (served by the same index.html). */
function Route() {
  const path = location.pathname.replace(/\/+$/, '') || '/';
  if (path === '/privacy') return <Privacy />;
  if (path === '/license' || path === '/licence') return <License />;
  if (path === '/releases') return <Releases />;
  return <App />;
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Suspense fallback={null}><Route /></Suspense>
  </StrictMode>,
);
