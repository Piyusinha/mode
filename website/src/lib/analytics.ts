/* Website analytics. Events go to heymode.app/api/e and are stored in this site's own Cloudflare account
   (worker/analytics.js lists every column). No cookies, no third parties, no IP addresses.

   - Visitor: a random ID saved in this browser (localStorage), so reloads and return visits count as the same
     visitor. Another browser, a private window or clearing site data starts a new one.
   - Visit: a random ID for this browser tab (sessionStorage).
   - Browsers that send Do Not Track or Global Privacy Control are not counted.

   Any element with data-ev="event" is tracked on click, with data-where / data-target / data-detail.
   Sections with data-section="name" send section_view once, the first time half of it is on screen. */

type Ev = { e: string; p?: string; a?: string; t?: string; d?: string; x?: string; val?: number };

const off = typeof navigator !== 'undefined' && (navigator.doNotTrack === '1' || (navigator as Navigator & { globalPrivacyControl?: boolean }).globalPrivacyControl === true);

const store = (kind: 'local' | 'session', key: string, make: () => string): [string, boolean] => {
  try {
    const st = kind === 'local' ? localStorage : sessionStorage;
    const had = st.getItem(key);
    if (had) return [had, false];
    const v = make(); st.setItem(key, v); return [v, true];
  } catch { return [make(), true]; }
};
const rid = () => (crypto.randomUUID ? crypto.randomUUID() : Math.random().toString(36).slice(2) + Date.now().toString(36));

const [visitor, isNew] = off ? ['', false] : store('local', 'mode.visitor', rid);
const [visit] = off ? [''] : store('session', 'mode.visit', rid);

/** The visitor ID to put on download links, so a download can be tied to the visit that started it. */
export function withVisitor(href: string, where?: string): string {
  if (off) return href;
  const sep = href.includes('?') ? '&' : '?';
  return `${href}${sep}vid=${encodeURIComponent(visitor)}${where ? `&where=${encodeURIComponent(where)}` : ''}`;
}

const params = new URLSearchParams(typeof location !== 'undefined' ? location.search : '');
const refHost = (() => { try { const h = document.referrer ? new URL(document.referrer).hostname : ''; return h === location.hostname ? '' : h; } catch { return ''; } })();

let queue: Ev[] = [];
let timer = 0;
function flush() {
  if (!queue.length) return;
  const body = JSON.stringify({
    v: visitor, s: visit, n: isNew ? 1 : 0, w: Math.round(innerWidth), l: navigator.language || '',
    r: refHost, u: [params.get('utm_source') || '', params.get('utm_medium') || '', params.get('utm_campaign') || ''],
    ev: queue,
  });
  queue = [];
  const blob = new Blob([body], { type: 'application/json' });
  if (!navigator.sendBeacon?.('/api/e', blob)) fetch('/api/e', { method: 'POST', body, keepalive: true }).catch(() => {});
}

/** Records one event. Events within a moment of each other go together in one request. */
export function track(e: string, props: Omit<Ev, 'e'> = {}) {
  if (off) return;
  queue.push({ e, p: location.pathname, ...props });
  clearTimeout(timer);
  timer = window.setTimeout(flush, 800);
}

const once = new Set<string>();
/** Like track, but at most once per page view for the same key. */
export function trackOnce(key: string, e: string, props: Omit<Ev, 'e'> = {}) {
  if (once.has(key)) return;
  once.add(key); track(e, props);
}

let started = false;
/** Page view, scroll depth, time on page, section views and data-ev clicks. Call once when the page starts. */
export function startAnalytics() {
  if (off || started) return;
  started = true;
  track('page_view', { val: isNew ? 1 : 0 });

  // Clicks on anything marked data-ev.
  document.addEventListener('click', (ev) => {
    const el = (ev.target as Element).closest<HTMLElement>('[data-ev]');
    if (!el) return;
    const d = el.dataset;
    track(d.ev!, { a: d.where, t: d.target, d: d.detail });
  }, true);

  // Scroll depth: 25 / 50 / 75 / 100 % of the page, once each.
  const depth = () => {
    const max = document.documentElement.scrollHeight - innerHeight;
    const pct = max > 0 ? (scrollY / max) * 100 : 100;
    for (const m of [25, 50, 75, 100]) if (pct >= m - 0.5) trackOnce(`scroll${m}`, 'scroll_depth', { val: m });
  };
  addEventListener('scroll', depth, { passive: true });

  // Sections seen (half on screen), once each.
  const io = new IntersectionObserver((es) => es.forEach((x) => {
    if (x.isIntersecting) { const n = (x.target as HTMLElement).dataset.section!; trackOnce(`sec-${n}`, 'section_view', { t: n }); io.unobserve(x.target); }
  }), { threshold: 0.3 });
  const watchSections = () => document.querySelectorAll('[data-section]').forEach((el) => io.observe(el));
  watchSections(); setTimeout(watchSections, 1500);

  // Time on page: only while the tab is visible, sent as it's hidden or closed (each report is the time since the last).
  let since = document.hidden ? 0 : performance.now();
  const report = () => {
    if (since) { const sec = Math.round((performance.now() - since) / 1000); if (sec > 0) track('engaged', { val: sec }); since = 0; }
    flush();
  };
  document.addEventListener('visibilitychange', () => { if (document.hidden) report(); else since = performance.now(); });
  addEventListener('pagehide', report);
}
