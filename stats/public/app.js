// Mode stats: the dashboard. Data from /api/report (all panels), /api/explore (custom charts), /api/options
// (filter menus); layout and custom charts saved with /api/prefs. No libraries; charts are plain SVG/HTML.

const $ = (id) => document.getElementById(id);
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
const fmt = (n) => (n == null || Number.isNaN(n) ? '–' : Math.round(n).toLocaleString());
const pct = (a, b) => (b ? `${Math.round((a / b) * 100)}%` : '–');
const dur = (s) => { s = Math.round(s || 0); return s < 60 ? `${s}s` : `${Math.floor(s / 60)}m ${String(s % 60).padStart(2, '0')}s`; };
const none = (v) => (v === '' || v == null ? '(none)' : v);

// ---------------------------------------------------------------------------------------------
// Names shown for events and fields

const EVENT_NAMES = {
  page_view: 'Page view', engaged: 'Time on page', scroll_depth: 'Scrolled', section_view: 'Section seen', nav_click: 'Menu or link click',
  star_click: 'Star click', github_click: 'GitHub click', faq_open: 'Question opened', download_click: 'Download button click',
  get_mode_click: '"Get Mode" click (phone)', download_sent: 'Download sent', release_download: 'Releases page download',
  release_open: 'Older release opened', releases_error: 'Releases failed to load', film_seen: 'Film seen', film_play: 'Film played (silent)',
  film_sound: 'Play with sound', film_fullscreen: 'Film full screen', film_pause: 'Film paused', film_progress: 'Film watched to %',
  film_watch: 'Film seconds watched', film_end: 'Film finished with sound', film_error: 'Film failed', film_fallback: 'Film opened elsewhere',
  tour_step: 'Tour step reached', tour_complete: 'Tour finished', tour_jump: 'Tour chapter jump',
};
const FIELD_NAMES = {
  time: 'Time', event: 'Event', path: 'Page', country: 'Country', device: 'Device', system: 'System', browser: 'Browser', source: 'Came from (site)',
  utm_source: 'utm_source', utm_medium: 'utm_medium', campaign: 'Campaign', language: 'Language', where: 'Where on the page',
  target: 'Target', detail: 'Detail', extra: 'Extra', value: 'Value',
};
const FILTER_FIELDS = ['country', 'device', 'system', 'browser', 'source', 'utm_source', 'utm_medium', 'campaign', 'path', 'language'];
const PLACE = { header: 'Top bar', hero: 'Hero', get: 'Download section', closing: 'Closing card', releases: 'Releases page', footer: 'Footer', how: 'How it works', list: 'Tour list', rail: 'Tour dashes', '': '(none)' };
const SERIES = ['var(--series-1)', 'var(--series-2)', 'var(--series-3)', 'var(--series-4)'];
const countryName = (() => { try { const d = new Intl.DisplayNames(undefined, { type: 'region' }); return (c) => (c && c.length === 2 ? `${d.of(c)} (${c})` : none(c)); } catch { return none; } })();

// ---------------------------------------------------------------------------------------------
// State: range, grouping, filters (kept in the URL hash), prefs (saved on the server)

const RANGES = { '24h': 86400, '7d': 7 * 86400, '30d': 30 * 86400, '90d': 90 * 86400 };
let state = { range: '30d', from: 0, to: 0, step: 'day', filters: {}, test: false };
let prefs = { hidden: [], order: [], custom: [], defaultView: null };
let data = null;

function readHash() {
  const p = new URLSearchParams(location.hash.slice(1));
  if (![...p.keys()].length) return false;
  state.range = p.get('r') || '30d';
  state.from = +p.get('from') || 0; state.to = +p.get('to') || 0;
  state.step = p.get('step') || (state.range === '24h' ? 'hour' : 'day');
  state.test = p.get('test') === '1';
  state.filters = {};
  for (const f of FILTER_FIELDS) if (p.has(`f.${f}`)) state.filters[f] = p.get(`f.${f}`);
  return true;
}
function writeHash() {
  const p = new URLSearchParams({ r: state.range, step: state.step });
  if (state.range === 'custom') { p.set('from', state.from); p.set('to', state.to); }
  if (state.test) p.set('test', '1');
  for (const [k, v] of Object.entries(state.filters)) p.set(`f.${k}`, v);
  history.replaceState(null, '', `#${p}`);
}
function query() {
  const now = Math.floor(Date.now() / 1000);
  const to = state.range === 'custom' ? state.to : now;
  const from = state.range === 'custom' ? state.from : now - RANGES[state.range];
  const p = new URLSearchParams({ from, to, step: state.step });
  if (state.test) p.set('test', '1');
  for (const [k, v] of Object.entries(state.filters)) p.set(`f.${k}`, v);
  return p;
}

async function api(path, init) {
  const res = await fetch(path, { credentials: 'same-origin', ...init });
  if (res.status === 401) { location.href = '/login'; throw new Error('signed out'); }
  const body = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(body.error ? `${body.error}${body.detail ? ` (${body.detail})` : ''}` : `HTTP ${res.status}`);
  return body;
}
async function savePrefs() {
  try { await api('/api/prefs', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(prefs) }); }
  catch (e) { status(`Couldn't save: ${e.message}`, true); }
}
function status(text, err = false) { const s = $('status'); s.textContent = text; s.classList.toggle('err', err); }

// ---------------------------------------------------------------------------------------------
// Tooltip

const tip = $('tip');
function showTip(html, x, y) {
  tip.innerHTML = html; tip.hidden = false;
  const r = tip.getBoundingClientRect();
  tip.style.left = `${Math.min(x + 14, innerWidth - r.width - 8)}px`;
  tip.style.top = `${Math.max(8, y - r.height - 12)}px`;
}
const hideTip = () => { tip.hidden = true; };

// ---------------------------------------------------------------------------------------------
// Building blocks

/** Stat tiles with the change against the previous period of the same length. */
function tiles(items) {
  return `<div class="tiles">${items.map((t) => {
    let d = '';
    if (t.prev != null) {
      const diff = t.value - t.prev;
      d = t.prev === 0 ? (t.value ? '<div class="d">new this period</div>' : '<div class="d">no change</div>')
        : `<div class="d ${diff > 0 ? 'up' : diff < 0 ? 'down' : ''}">${diff > 0 ? '▲' : diff < 0 ? '▼' : '•'} ${Math.abs(Math.round((diff / t.prev) * 100))}% vs before</div>`;
    } else if (t.note) d = `<div class="d">${esc(t.note)}</div>`;
    return `<div class="tile"><div class="k">${esc(t.label)}</div><div class="v">${t.text ?? fmt(t.value)}</div>${d}</div>`;
  }).join('')}</div>`;
}

/** Horizontal bars, one hue. rows: {label, value, value2?, sub?, filter?: [field, value]}. */
function bars(rows, { unit = '', two = null, total = null } = {}) {
  if (!rows.length) return '<p class="empty">Nothing yet in this range.</p>';
  const max = Math.max(...rows.map((r) => Math.max(r.value, r.value2 ?? 0)), 1);
  const sum = total ?? rows.reduce((a, r) => a + r.value, 0);
  const legend = two ? `<div class="legend"><span><i class="sq" style="background:var(--series-1)"></i> ${esc(two[0])}</span><span><i class="sq" style="background:var(--series-2)"></i> ${esc(two[1])}</span></div>` : '';
  return legend + `<div class="bars">${rows.map((r, i) => {
    const tipText = two
      ? `<b>${esc(r.label)}</b><div class="r"><span>${esc(two[0])}</span><span>${fmt(r.value)}</span></div><div class="r"><span>${esc(two[1])}</span><span>${fmt(r.value2)}</span></div>`
      : `<b>${esc(r.label)}</b><div class="r"><span>${esc(unit || 'Count')}</span><span>${fmt(r.value)}</span></div>${sum ? `<div class="r"><span>Share</span><span>${pct(r.value, sum)}</span></div>` : ''}${r.filter ? '<div class="r"><span>Click to filter</span><span></span></div>' : ''}`;
    return `<div class="brow${r.filter ? ' click' : ''}" data-tip="${esc(tipText)}"${r.filter ? ` data-filter="${esc(r.filter[0])}" data-value="${esc(r.filter[1])}"` : ''}>
      <span class="l" title="${esc(r.label)}">${esc(r.label)}${r.sub ? `<small>${esc(r.sub)}</small>` : ''}</span>
      <span class="track"><span class="fill" style="width:${(r.value / max) * 100}%"></span>${two ? `<span class="fill s2" style="width:${((r.value2 || 0) / max) * 100}%"></span>` : ''}</span>
      <span class="n">${two ? `${fmt(r.value)} · ${fmt(r.value2)}` : fmt(r.value)}</span></div>`;
  }).join('')}</div>`;
}

/** A table; numeric columns right-aligned. cols: [key, label, kind?] kind 'num' | fn(row) */
function table(cols, rows) {
  if (!rows.length) return '<p class="empty">Nothing yet in this range.</p>';
  return `<div class="tbl"><table><thead><tr>${cols.map(([, l, k]) => `<th class="${k === 'num' ? 'num' : ''}">${esc(l)}</th>`).join('')}</tr></thead><tbody>${
    rows.map((r) => `<tr>${cols.map(([key, , k]) => `<td class="${k === 'num' ? 'num' : ''}">${k === 'num' ? fmt(r[key]) : esc(typeof k === 'function' ? k(r) : none(r[key]))}</td>`).join('')}</tr>`).join('')
  }</tbody></table></div>`;
}

/** Line chart, one axis. series: [{name, points: [[t, y]]}], up to 4 (color follows the series, in fixed order). */
function lineChart(id, series, step) {
  if (!series.length || !series[0].points.length) return '<p class="empty">Nothing yet in this range.</p>';
  const hidden = new Set(lineHidden[id] || []);
  const legend = series.length > 1 ? `<div class="legend">${series.map((s, i) => `<button type="button" data-line="${esc(id)}" data-name="${esc(s.name)}" aria-pressed="${!hidden.has(s.name)}"><i style="background:${SERIES[i]}"></i>${esc(s.name)}</button>`).join('')}</div>` : '';
  return `${legend}<div class="chart" data-chart="${esc(id)}"></div>`;
}
const lineHidden = {};
const charts = {};
function drawLine(el, series, step) {
  const W = el.clientWidth || 600, H = 240, L = 44, R = 96, T = 10, B = 26;
  const id = el.dataset.chart, hidden = new Set(lineHidden[id] || []);
  const vis = series.map((s, i) => ({ ...s, color: SERIES[i] })).filter((s) => !hidden.has(s.name));
  const xs = series[0].points.map((p) => p[0]);
  const x0 = xs[0], x1 = xs[xs.length - 1] || x0 + 1;
  // whole-number steps: counts never get a 0.5 tick
  const top = Math.max(1, ...vis.flatMap((s) => s.points.map((p) => p[1])));
  const stepY = Math.max(1, niceMax(top / 4)), ymax = Math.ceil(top / stepY) * stepY;
  const X = (t) => L + (x1 === x0 ? (W - L - R) / 2 : ((t - x0) / (x1 - x0)) * (W - L - R));
  const Y = (v) => T + (1 - v / ymax) * (H - T - B);
  const ticks = []; for (let v = 0; v <= ymax + 1e-9; v += stepY) ticks.push(v);
  const every = Math.max(1, Math.ceil(xs.length / Math.max(2, Math.floor((W - L - R) / 70))));
  const label = (t) => { const d = new Date(t * 1000); return step === 'hour' ? d.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' }) : d.toLocaleDateString(undefined, { day: 'numeric', month: 'short' }); };
  // direct labels at the line ends, nudged apart so they never overlap
  const ends = vis.map((s) => ({ s, y: Y(s.points[s.points.length - 1][1]) })).sort((a, b) => a.y - b.y);
  for (let i = 1; i < ends.length; i++) if (ends[i].y - ends[i - 1].y < 14) ends[i].y = ends[i - 1].y + 14;
  const showDots = xs.length <= 45;
  el.innerHTML = `<svg viewBox="0 0 ${W} ${H}" height="${H}" role="img" aria-label="${esc(vis.map((s) => s.name).join(', '))} over time">
    <g class="axis">${ticks.map((v) => `<line class="gridl" x1="${L}" x2="${W - R}" y1="${Y(v)}" y2="${Y(v)}"/><text x="${L - 8}" y="${Y(v) + 4}" text-anchor="end">${fmt(v)}</text>`).join('')}
    <line class="base" x1="${L}" x2="${W - R}" y1="${Y(0)}" y2="${Y(0)}"/>
    ${xs.map((t, i) => (i % every === 0 ? `<text x="${X(t)}" y="${H - 6}" text-anchor="middle">${esc(label(t))}</text>` : '')).join('')}</g>
    ${vis.map((s) => `<path class="line" stroke="${s.color}" d="${s.points.map((p, i) => `${i ? 'L' : 'M'}${X(p[0]).toFixed(1)},${Y(p[1]).toFixed(1)}`).join('')}"/>`).join('')}
    ${showDots ? vis.map((s) => s.points.map((p) => `<circle class="dot" r="4" fill="${s.color}" cx="${X(p[0])}" cy="${Y(p[1])}"/>`).join('')).join('') : ''}
    ${vis.length > 1 && vis.length <= 4 ? ends.map((e) => `<text class="dl" x="${W - R + 8}" y="${e.y + 4}">${esc(e.s.name)}</text>`).join('') : ''}
    <line class="cross" id="x-${esc(id)}" y1="${T}" y2="${H - B}" visibility="hidden"/>
    <rect x="${L}" y="${T}" width="${W - L - R}" height="${H - T - B}" fill="transparent" data-hover="1"/>
  </svg>`;
  const svg = el.firstElementChild, cross = svg.querySelector('.cross'), rect = svg.querySelector('rect');
  rect.addEventListener('pointermove', (ev) => {
    const box = svg.getBoundingClientRect(), px = ((ev.clientX - box.left) / box.width) * W;
    let k = 0; for (let i = 1; i < xs.length; i++) if (Math.abs(X(xs[i]) - px) < Math.abs(X(xs[k]) - px)) k = i;
    cross.setAttribute('x1', X(xs[k])); cross.setAttribute('x2', X(xs[k])); cross.setAttribute('visibility', 'visible');
    const d = new Date(xs[k] * 1000);
    const head = step === 'hour' ? d.toLocaleString(undefined, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }) : step === 'week' ? `Week of ${d.toLocaleDateString(undefined, { day: 'numeric', month: 'short' })}` : d.toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' });
    showTip(`<b>${esc(head)}</b>${vis.map((s) => `<div class="r"><span><i style="background:${s.color}"></i>${esc(s.name)}</span><span>${fmt(s.points[k][1])}</span></div>`).join('')}`, ev.clientX, ev.clientY);
  });
  rect.addEventListener('pointerleave', () => { cross.setAttribute('visibility', 'hidden'); hideTip(); });
}
function niceMax(v) { const p = 10 ** Math.floor(Math.log10(v)), m = v / p; return (m <= 1 ? 1 : m <= 2 ? 2 : m <= 2.5 ? 2.5 : m <= 5 ? 5 : 10) * p; }

/** Time buckets for the range (missing ones become zeros). */
function buckets(range, step) {
  const s = step === 'hour' ? 3600 : step === 'week' ? 604800 : 86400;
  const out = []; for (let t = Math.floor(range.from / s) * s; t < range.to; t += s) out.push(t);
  return out.length > 2000 ? out.slice(-2000) : out;
}

// ---------------------------------------------------------------------------------------------
// Panels: every chart on the page. section → panels; each panel returns HTML (and may draw after).

const ev = (name) => (data.events.find((e) => e.e === name) || { n: 0, u: 0, s: 0, v: 0 });
const evPrev = (name) => (data.eventsPrev.find((e) => e.e === name) || { n: 0, u: 0, s: 0, v: 0 });
const fun = (name) => (data.funnel.find((e) => e.e === name) || { u: 0 }).u;
const platformOf = (file) => (/mac/i.test(file) ? 'Mac' : /\.msi$/i.test(file) ? 'Windows (.msi)' : /windows/i.test(file) ? 'Windows' : /\.deb$/i.test(file) ? 'Linux (.deb)' : /linux/i.test(file) ? 'Linux' : file || '(unknown)');
const visitorsList = (key, field, label = none) => bars(data[key].map((r) => ({ label: label(r.k), value: r.u, filter: [field, r.k] })), { unit: 'Visitors' });

const SECTIONS = [
  { id: 'overview', title: 'Overview', panels: [
    { id: 'kpis', title: 'This period', w: 12, csv: () => kpis().map((t) => ({ metric: t.label, value: t.value, before: t.prev ?? '' })), html: () => tiles(kpis()) },
    { id: 'trend', title: 'Over time', w: 12, html: () => lineChart('trend', trend(), state.step), draw: (el) => drawLine(el.querySelector('[data-chart]'), trend(), state.step), csv: () => trendCsv() },
  ] },
  { id: 'funnel', title: 'Getting to a download', panels: [
    { id: 'reach', title: 'How far visitors get', sub: 'Unique visitors who did each thing, as a share of everyone who visited.', w: 6, html: () => reachBars(), csv: () => reach() },
    { id: 'rate', title: 'Download rates', w: 6, html: () => { const v = ev('page_view').u || 0; return tiles([
      { label: 'Visitors who clicked download', text: pct(fun('download_click') + fun('release_download'), v), value: 0 },
      { label: 'Visitors whose download started', text: pct(fun('download_sent'), v), value: 0 },
      { label: 'Clicks that became downloads', text: pct(ev('download_sent').v, ev('download_click').n + ev('release_download').n), value: 0 },
      { label: 'Downloads from links outside the site', value: data.downloadsFrom.filter((d) => !d.button).reduce((a, d) => a + d.n, 0) },
    ]); } },
  ] },
  { id: 'downloads', title: 'Downloads', panels: [
    { id: 'versions', title: 'By version and file', sub: '"Almost here" = the file wasn\'t ready, so the visitor saw the waiting page.', w: 6, html: () => table([['version', 'Version'], ['file', 'File'], ['ok', 'Downloads', 'num'], ['wait', '"Almost here"', 'num']], versions()), csv: () => versions() },
    { id: 'platforms', title: 'By computer', w: 6, html: () => bars(platforms(), { unit: 'Downloads' }), csv: () => platforms() },
    { id: 'buttons', title: 'Which button', sub: 'Download clicks by where the button is.', w: 4, html: () => bars(buttonPlaces(), { unit: 'Clicks' }), csv: () => buttonPlaces() },
    { id: 'detected', title: 'Computer picked vs detected', sub: '"detected-mac" = the site guessed Mac for that visitor.', w: 4, html: () => table([['target', 'Clicked'], ['detail', 'Detected'], ['n', 'Clicks', 'num']], data.buttons.filter((b) => b.e === 'download_click')), csv: () => data.buttons },
    { id: 'from', title: 'Where downloads start', sub: 'Site = the page the link was on. No button = a link from outside the site (README, GitHub, chat).', w: 4, html: () => table([['site', 'Site', (r) => none(r.site)], ['button', 'Button', (r) => (r.button ? PLACE[r.button] || r.button : 'no button')], ['n', 'Downloads', 'num']], data.downloadsFrom), csv: () => data.downloadsFrom },
  ] },
  { id: 'film', title: 'Film', panels: [
    { id: 'filmTiles', title: 'Film', w: 12, html: () => tiles(filmTiles()), csv: () => filmTiles() },
    { id: 'filmProgress', title: 'How far it\'s watched', sub: 'Unique visitors reaching each point, silent loop vs with sound.', w: 6, html: () => bars(filmProgress(), { two: ['Silent', 'With sound'] }), csv: () => filmProgress() },
    { id: 'filmVariant', title: 'Which cut', sub: 'Wide (computers) or tall 9:16 (phones), by visitors who saw it.', w: 6, html: () => bars(filmVariant(), { unit: 'Visitors' }), csv: () => filmVariant() },
  ] },
  { id: 'tour', title: 'Tour', panels: [
    { id: 'tourSteps', title: 'Where people drop off', sub: 'Unique visitors who stopped on each step.', w: 8, html: () => bars(tourSteps(), { unit: 'Visitors' }), csv: () => data.tour },
    { id: 'tourHow', title: 'How they move', w: 4, html: () => bars(data.tourHow.map((r) => ({ label: { wheel: 'Mouse wheel / trackpad', swipe: 'Swipe', key: 'Keyboard', jump: 'Jumped from a list', fling: 'Fast scroll', scroll: 'Plain scroll' }[r.how] || none(r.how), value: r.n })), { unit: 'Steps' }), csv: () => data.tourHow },
    { id: 'tourJumps', title: 'Chapter jumps', w: 4, html: () => table([['place', 'From', (r) => PLACE[r.place] || none(r.place)], ['chapter', 'Chapter', (r) => (r.chapter === '' ? '(none)' : `#${+r.chapter + 1}`)], ['n', 'Jumps', 'num']], data.tourJumps), csv: () => data.tourJumps },
  ] },
  { id: 'engagement', title: 'Reading the page', panels: [
    { id: 'sections', title: 'Sections seen', w: 4, html: () => bars(data.sections.map((r) => ({ label: { hero: 'Top (hero)', tour: 'Tour', get: 'Download section', appstore: 'Why not the App Store', more: 'The small things', how: 'How it works', try: 'Try saying', faq: 'Questions', closing: 'Closing card' }[r.k] || none(r.k), value: r.u })), { unit: 'Visitors', total: ev('page_view').u }), csv: () => data.sections },
    { id: 'scroll', title: 'Scroll depth', w: 4, html: () => bars(data.scroll.map((r) => ({ label: `${r.k}% of the page`, value: r.u })), { unit: 'Visitors', total: ev('page_view').u }), csv: () => data.scroll },
    { id: 'returning', title: 'Visits per visitor', sub: 'A visit is one browser tab session; reloads stay in the same visit.', w: 4, html: () => bars(data.visits.map((r) => ({ label: r.k, value: r.u })), { unit: 'Visitors' }), csv: () => data.visits },
    { id: 'faq', title: 'Questions opened', w: 6, html: () => bars(data.faq.map((r) => ({ label: none(r.k), value: r.n })), { unit: 'Times opened' }), csv: () => data.faq },
    { id: 'clicks', title: 'Other clicks', w: 6, html: () => table([['e', 'What', (r) => EVENT_NAMES[r.e] || r.e], ['place', 'Where', (r) => PLACE[r.place] || none(r.place)], ['target', 'Target'], ['n', 'Clicks', 'num'], ['u', 'Visitors', 'num']], data.clicks), csv: () => data.clicks },
  ] },
  { id: 'audience', title: 'Visitors', panels: [
    { id: 'country', title: 'Countries', w: 4, html: () => visitorsList('country', 'country', countryName), csv: () => data.country },
    { id: 'source', title: 'Came from', sub: 'The site that linked them; (none) = typed or bookmarked.', w: 4, html: () => visitorsList('source', 'source'), csv: () => data.source },
    { id: 'campaign', title: 'Campaigns', sub: 'From ?utm_campaign= on the link.', w: 4, html: () => visitorsList('campaign', 'campaign'), csv: () => data.campaign },
    { id: 'utm_source', title: 'utm_source', w: 4, html: () => visitorsList('utm_source', 'utm_source'), csv: () => data.utm_source },
    { id: 'device', title: 'Devices', w: 4, html: () => visitorsList('device', 'device'), csv: () => data.device },
    { id: 'system', title: 'Systems', w: 4, html: () => visitorsList('system', 'system'), csv: () => data.system },
    { id: 'browser', title: 'Browsers', w: 4, html: () => visitorsList('browser', 'browser'), csv: () => data.browser },
    { id: 'language', title: 'Languages', w: 4, html: () => visitorsList('language', 'language'), csv: () => data.language },
    { id: 'path', title: 'Pages', w: 4, html: () => visitorsList('path', 'path'), csv: () => data.path },
  ] },
  { id: 'health', title: 'Problems and live events', panels: [
    { id: 'problems', title: 'Problems', sub: 'Film failures, the release list not loading, downloads that weren\'t ready.', w: 4, html: () => table([['e', 'What', (r) => (r.e === 'download_sent' ? '"Almost here" shown' : EVENT_NAMES[r.e] || r.e)], ['about', 'About', (r) => none(r.about || r.file)], ['n', 'Times', 'num']], data.problems), csv: () => data.problems },
    { id: 'recent', title: 'Latest events', sub: 'The newest 100 (time on page left out).', w: 8, html: () => table([
      ['at', 'When', (r) => new Date(r.at * 1000).toLocaleString(undefined, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit', second: '2-digit' })],
      ['e', 'Event', (r) => EVENT_NAMES[r.e] || r.e], ['place', 'Where', (r) => PLACE[r.place] ?? r.place], ['target', 'Target'], ['detail', 'Detail'],
      ['val', 'Value', (r) => (r.val ? String(r.val) : '')], ['country', 'Country'], ['device', 'Device', (r) => `${r.device} · ${r.system}`], ['visitor', 'Visitor'],
    ], data.recent), csv: () => data.recent },
  ] },
];

function kpis() {
  const pv = ev('page_view'), pp = evPrev('page_view'), en = ev('engaged'), ep = evPrev('engaged');
  const nv = data.newVisitors[0]?.u || 0, np = data.newVisitorsPrev[0]?.u || 0;
  return [
    { label: 'Visitors', value: pv.u, prev: pp.u },
    { label: 'New visitors', value: nv, prev: np },
    { label: 'Returning visitors', value: Math.max(0, pv.u - nv), prev: Math.max(0, pp.u - np) },
    { label: 'Visits', value: pv.s, prev: pp.s },
    { label: 'Page views', value: pv.n, prev: pp.n },
    { label: 'Time per visit', value: pv.s ? en.v / pv.s : 0, prev: pp.s ? ep.v / pp.s : 0, text: dur(pv.s ? en.v / pv.s : 0) },
    { label: 'Download clicks', value: ev('download_click').n + ev('release_download').n, prev: evPrev('download_click').n + evPrev('release_download').n },
    { label: 'Downloads', value: ev('download_sent').v, prev: evPrev('download_sent').v },
    { label: 'Played film with sound', value: ev('film_sound').u, prev: evPrev('film_sound').u },
    { label: 'Finished the tour', value: ev('tour_complete').u, prev: evPrev('tour_complete').u },
    { label: 'Stars clicked', value: ev('star_click').n, prev: evPrev('star_click').n },
  ];
}
function trend() {
  const xs = buckets(data.range, state.step), get = (e, k) => { const m = new Map(); for (const r of data.series) if (e.includes(r.e)) m.set(r.t, (m.get(r.t) || 0) + r[k]); return xs.map((t) => [t, m.get(t) || 0]); };
  return [
    { name: 'Visitors', points: get(['page_view'], 'u') },
    { name: 'Page views', points: get(['page_view'], 'n') },
    { name: 'Download clicks', points: get(['download_click', 'release_download'], 'n') },
    { name: 'Downloads', points: get(['download_sent'], 'n') },
  ];
}
const trendCsv = () => { const s = trend(); return s[0].points.map((p, i) => Object.fromEntries([['time', new Date(p[0] * 1000).toISOString()], ...s.map((x) => [x.name, x.points[i][1]])])); };
function reach() {
  return [
    ['Visited', ev('page_view').u], ['Film played', fun('film_play')], ['Turned film sound on', fun('film_sound')], ['Started the tour', fun('tour_step')],
    ['Finished the tour', fun('tour_complete')], ['Saw the download section', data.funnelGet[0]?.u || 0], ['Clicked download', fun('download_click') + fun('release_download')],
    ['Download started', fun('download_sent')],
  ].map(([step, visitors]) => ({ step, visitors }));
}
const reachBars = () => { const r = reach(), all = r[0].visitors; return bars(r.map((x) => ({ label: x.step, value: x.visitors, sub: x === r[0] ? '' : pct(x.visitors, all) })), { unit: 'Visitors', total: all }); };
function versions() {
  const m = new Map();
  for (const d of data.downloads) { const k = `${d.version}|${d.file}`; const r = m.get(k) || { version: none(d.version), file: d.file, ok: 0, wait: 0 }; r[d.ready ? 'ok' : 'wait'] += d.n; m.set(k, r); }
  return [...m.values()].sort((a, b) => String(b.version).localeCompare(String(a.version), undefined, { numeric: true }) || b.ok - a.ok);
}
function platforms() {
  const m = new Map(); for (const d of data.downloads) if (d.ready) m.set(platformOf(d.file), (m.get(platformOf(d.file)) || 0) + d.n);
  return [...m].map(([label, value]) => ({ label, value })).sort((a, b) => b.value - a.value);
}
function buttonPlaces() {
  const m = new Map(); for (const b of data.buttons) { const k = PLACE[b.place] || none(b.place); m.set(k, (m.get(k) || 0) + b.n); }
  return [...m].map(([label, value]) => ({ label, value })).sort((a, b) => b.value - a.value);
}
const filmU = (e, f = () => true) => data.film.filter((r) => r.e === e && f(r)).reduce((a, r) => a + r.u, 0);
const filmN = (e) => data.film.filter((r) => r.e === e).reduce((a, r) => a + r.n, 0);
function filmTiles() {
  const mins = (mode) => data.film.filter((r) => r.e === 'film_watch' && r.mode === mode).reduce((a, r) => a + r.v, 0) / 60;
  return [
    { label: 'Saw the film', value: filmU('film_seen') }, { label: 'Silent loop played', value: filmU('film_play') },
    { label: 'Turned sound on', value: filmU('film_sound') }, { label: 'Watched to the end with sound', value: filmU('film_progress', (r) => r.mode === 'sound' && r.val === 100) },
    { label: 'Full screen', value: filmN('film_fullscreen') }, { label: 'Paused', value: filmN('film_pause') },
    { label: 'Minutes watched with sound', value: mins('sound') }, { label: 'Minutes watched silent', value: mins('silent') },
  ];
}
const filmProgress = () => [25, 50, 75, 100].map((p) => ({ label: `${p}%`, value: filmU('film_progress', (r) => r.val === p && r.mode === 'silent'), value2: filmU('film_progress', (r) => r.val === p && r.mode === 'sound') }));
const filmVariant = () => ['wide', 'tall'].map((v) => ({ label: v === 'wide' ? 'Wide (16:9)' : 'Tall (9:16)', value: filmU('film_seen', (r) => r.variant === v) }));
function tourSteps() {
  const m = new Map(); for (const r of data.tour) { const k = r.step; const x = m.get(k) || { label: `${r.step}. ${r.chapter}`, sub: r.screen, value: 0 }; x.value += r.u; m.set(k, x); }
  return [...m.entries()].sort((a, b) => a[0] - b[0]).map((e) => e[1]);
}

// ---------------------------------------------------------------------------------------------
// Custom charts (saved in prefs.custom)

async function customHtml(c) {
  const p = query(); p.set('event', c.event); p.set('measure', c.measure); p.set('by', c.by); if (c.split) p.set('split', c.split);
  const r = await api(`/api/explore?${p}`);
  const unit = { visitors: 'Visitors', events: 'Times', visits: 'Visits', total: 'Total' }[c.measure];
  const lab = (field, v) => (field === 'country' ? countryName(v) : field === 'where' ? PLACE[v] || none(v) : field === 'event' ? EVENT_NAMES[v] || v : none(v));
  if (c.by === 'time') {
    const xs = buckets(r.range, state.step);
    const groups = new Map();
    for (const row of r.rows) { const g = c.split ? row.g : unit; if (!groups.has(g)) groups.set(g, new Map()); groups.get(g).set(row.k, (groups.get(g).get(row.k) || 0) + row.y); }
    // at most four lines; the rest fold into "Other"
    let list = [...groups].map(([g, m]) => ({ g, m, total: [...m.values()].reduce((a, b) => a + b, 0) })).sort((a, b) => b.total - a.total);
    if (list.length > 4) { const rest = list.slice(3), m = new Map(); for (const x of rest) for (const [t, y] of x.m) m.set(t, (m.get(t) || 0) + y); list = [...list.slice(0, 3), { g: 'Other', m }]; }
    const series = list.map((x) => ({ name: x.g === 'Other' ? 'Other' : c.split ? lab(c.split, x.g) : x.g, points: xs.map((t) => [t, x.m.get(t) || 0]) }));
    customSeries[c.id] = series;
    return { html: lineChart(`c-${c.id}`, series, state.step), rows: r.rows };
  }
  if (!c.split) return { html: bars(r.rows.map((x) => ({ label: lab(c.by, x.k), value: x.y, filter: FILTER_FIELDS.includes(c.by) ? [c.by, x.k] : null })), { unit }), rows: r.rows };
  return { html: table([['k', FIELD_NAMES[c.by], (x) => lab(c.by, x.k)], ['g', FIELD_NAMES[c.split], (x) => lab(c.split, x.g)], ['y', unit, 'num']], r.rows), rows: r.rows };
}
const customSeries = {};

// ---------------------------------------------------------------------------------------------
// Rendering

const csvCache = {};
function card(p, body) {
  return `<article class="card w${p.w || 4}" data-panel="${esc(p.id)}"><header><div style="flex:1"><h3>${esc(p.title)}</h3>${p.sub ? `<p>${esc(p.sub)}</p>` : ''}</div>
    <span class="tools"><button type="button" data-csv="${esc(p.id)}" title="Download as CSV">CSV</button><button type="button" data-hide="${esc(p.id)}" title="Hide this chart" aria-label="Hide ${esc(p.title)}">×</button></span></header>${body}</article>`;
}
function orderedSections() {
  const order = prefs.order?.length ? prefs.order : SECTIONS.map((s) => s.id);
  const all = [...SECTIONS, { id: 'custom', title: 'Your charts', panels: [] }];
  return [...order.map((id) => all.find((s) => s.id === id)).filter(Boolean), ...all.filter((s) => !order.includes(s.id))];
}

async function render() {
  const main = $('main'), hidden = new Set(prefs.hidden);
  let html = '';
  const after = [];
  for (const s of orderedSections()) {
    if (s.id === 'custom') { html += `<section class="section" id="sec-custom"${prefs.custom.length ? '' : ' hidden'}><h2>Your charts</h2><div class="grid" id="customGrid"></div></section>`; continue; }
    const panels = s.panels.filter((p) => !hidden.has(p.id));
    if (!panels.length) continue;
    html += `<section class="section"><h2>${esc(s.title)}</h2><div class="grid">${panels.map((p) => {
      try { csvCache[p.id] = p.csv?.(); if (p.draw) after.push(p); return card(p, p.html()); }
      catch (e) { console.error(p.id, e); return card(p, `<p class="empty">Couldn't show this: ${esc(e.message)}</p>`); }
    }).join('')}</div></section>`;
  }
  main.innerHTML = html;
  for (const p of after) p.draw(main.querySelector(`[data-panel="${p.id}"]`));
  // custom charts load one by one
  const grid = $('customGrid');
  if (grid) for (const c of prefs.custom) {
    const pid = `c-${c.id}`;
    if (hidden.has(pid)) continue;
    const p = { id: pid, title: c.title, w: c.by === 'time' ? 12 : 6, sub: describe(c) };
    grid.insertAdjacentHTML('beforeend', card(p, '<p class="empty">Loading…</p>'));
    customHtml(c).then(({ html: h, rows }) => {
      const el = grid.querySelector(`[data-panel="${pid}"]`); if (!el) return;
      el.querySelector('.empty, .bars, .tbl, .legend, .chart')?.remove();
      el.insertAdjacentHTML('beforeend', h); csvCache[pid] = rows;
      const ch = el.querySelector('[data-chart]'); if (ch) drawLine(ch, customSeries[c.id], state.step);
    }).catch((e) => { const el = grid.querySelector(`[data-panel="${pid}"] .empty`); if (el) el.textContent = `Couldn't load: ${e.message}`; });
  }
}
const describe = (c) => `${{ visitors: 'Unique visitors', events: 'Times', visits: 'Unique visits', total: 'Sum of value' }[c.measure]} · ${c.event === 'all' ? 'all events' : EVENT_NAMES[c.event] || c.event} · by ${FIELD_NAMES[c.by]}${c.split ? ` and ${FIELD_NAMES[c.split]}` : ''}`;

async function load() {
  writeHash(); syncControls();
  $('main').classList.add('loading'); status('Loading…');
  try {
    data = await api(`/api/report?${query()}`);
    await render();
    status(`Updated ${new Date().toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })}`);
  } catch (e) {
    status(e.message, true);
    if (!data) $('main').innerHTML = `<p class="empty">Couldn't load the stats: ${esc(e.message)}</p>`;
  } finally { $('main').classList.remove('loading'); }
  loadOptions();
}

async function loadOptions() {
  try {
    const o = await api(`/api/options?${query()}`);
    $('addFilter').innerHTML = '<option value="">+ Filter</option>' + FILTER_FIELDS.map((f) => `<optgroup label="${esc(FIELD_NAMES[f])}">${(o[f] || []).map((r) =>
      `<option value="${esc(f)}\u0000${esc(r.k)}">${esc(f === 'country' ? countryName(r.k) : none(r.k))} (${fmt(r.u)})</option>`).join('')}</optgroup>`).join('');
  } catch { /* the menu just stays empty */ }
}

// ---------------------------------------------------------------------------------------------
// Controls

function syncControls() {
  for (const b of $('ranges').querySelectorAll('button')) b.setAttribute('aria-pressed', String(b.dataset.range === state.range));
  $('custom').hidden = state.range !== 'custom';
  if (state.range === 'custom') { $('from').value = day(state.from); $('to').value = day(state.to - 1); }
  $('step').value = state.step; $('test').checked = state.test;
  $('chips').innerHTML = Object.entries(state.filters).map(([k, v]) => `<span class="chip">${esc(FIELD_NAMES[k])}: <b>${esc(k === 'country' ? countryName(v) : none(v))}</b><button type="button" data-unfilter="${esc(k)}" aria-label="Remove filter">×</button></span>`).join('');
}
const day = (t) => new Date(t * 1000).toISOString().slice(0, 10);
const fromDay = (s, end) => Math.floor(Date.parse(`${s}T00:00:00`) / 1000) + (end ? 86400 : 0);

$('ranges').addEventListener('click', (e) => {
  const b = e.target.closest('button'); if (!b) return;
  state.range = b.dataset.range;
  if (state.range === 'custom') { const now = Math.floor(Date.now() / 1000); state.to = state.to || now; state.from = state.from || now - 7 * 86400; }
  state.step = state.range === '24h' ? 'hour' : state.step === 'hour' ? 'day' : state.step;
  load();
});
for (const id of ['from', 'to']) $(id).addEventListener('change', () => {
  if ($('from').value && $('to').value) { state.from = fromDay($('from').value); state.to = fromDay($('to').value, true); load(); }
});
$('step').addEventListener('change', () => { state.step = $('step').value; load(); });
$('test').addEventListener('change', () => { state.test = $('test').checked; load(); });
$('refresh').addEventListener('click', load);
$('addFilter').addEventListener('change', (e) => { const [k, v] = e.target.value.split('\u0000'); if (k) { state.filters[k] = v ?? ''; load(); } e.target.value = ''; });

document.addEventListener('click', (e) => {
  const t = e.target.closest('[data-unfilter],[data-filter],[data-hide],[data-csv],[data-line]');
  if (!t) return;
  if (t.dataset.unfilter) { delete state.filters[t.dataset.unfilter]; load(); }
  else if (t.dataset.filter) { state.filters[t.dataset.filter] = t.dataset.value; hideTip(); load(); }
  else if (t.dataset.hide) { prefs.hidden = [...new Set([...prefs.hidden, t.dataset.hide])]; savePrefs(); t.closest('.card').remove(); }
  else if (t.dataset.csv) downloadCsv(t.dataset.csv);
  else if (t.dataset.line) {
    const id = t.dataset.line, set = new Set(lineHidden[id] || []);
    set.has(t.dataset.name) ? set.delete(t.dataset.name) : set.add(t.dataset.name);
    lineHidden[id] = [...set]; t.setAttribute('aria-pressed', String(!set.has(t.dataset.name)));
    const el = document.querySelector(`[data-chart="${CSS.escape(id)}"]`);
    drawLine(el, id === 'trend' ? trend() : customSeries[id.slice(2)], state.step);
  }
});
// tooltips for bars
document.addEventListener('pointermove', (e) => { const r = e.target.closest?.('[data-tip]'); if (r) showTip(r.dataset.tip, e.clientX, e.clientY); else if (!e.target.closest?.('svg')) hideTip(); });
let rz; addEventListener('resize', () => { clearTimeout(rz); rz = setTimeout(() => data && render(), 200); });

function downloadCsv(id) {
  const rows = csvCache[id]; if (!rows?.length) return;
  const cols = Object.keys(rows[0]);
  const cell = (v) => { const s = String(v ?? ''); return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s; };
  const csv = [cols.join(','), ...rows.map((r) => cols.map((c) => cell(r[c])).join(','))].join('\n');
  const a = document.createElement('a');
  a.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv' }));
  a.download = `mode-${id}-${day(Date.now() / 1000)}.csv`; a.click(); URL.revokeObjectURL(a.href);
}

// ---------------------------------------------------------------------------------------------
// Customize dialog

function fillCustomize() {
  const hidden = new Set(prefs.hidden);
  const secs = orderedSections();
  $('panelList').innerHTML = secs.map((s, i) => {
    const panels = s.id === 'custom' ? prefs.custom.map((c) => ({ id: `c-${c.id}`, title: c.title })) : s.panels;
    return `<div class="psec"><div class="h"><span>${esc(s.title)}</span>
      <button type="button" data-move="${i}" data-dir="-1" aria-label="Move ${esc(s.title)} up"${i === 0 ? ' disabled' : ''}>↑</button>
      <button type="button" data-move="${i}" data-dir="1" aria-label="Move ${esc(s.title)} down"${i === secs.length - 1 ? ' disabled' : ''}>↓</button></div>
      <div class="items">${panels.length ? panels.map((p) => `<label><input type="checkbox" data-show="${esc(p.id)}"${hidden.has(p.id) ? '' : ' checked'}> ${esc(p.title)}</label>`).join('') : '<span class="muted">No charts yet.</span>'}</div></div>`;
  }).join('');
  $('customList').innerHTML = prefs.custom.map((c) => `<div class="crow"><span><b>${esc(c.title)}</b> <span class="muted">${esc(describe(c))}</span></span><button type="button" class="btn quiet" data-remove="${esc(c.id)}">Remove</button></div>`).join('');
}
$('customizeBtn').addEventListener('click', () => {
  $('cEvent').innerHTML = `<option value="all">All events</option>${Object.entries(EVENT_NAMES).map(([k, v]) => `<option value="${k}">${esc(v)}</option>`).join('')}`;
  $('cBy').innerHTML = Object.entries(FIELD_NAMES).map(([k, v]) => `<option value="${k}">${esc(v)}</option>`).join('');
  $('cSplit').innerHTML = `<option value="">Nothing</option>${Object.entries(FIELD_NAMES).filter(([k]) => k !== 'time').map(([k, v]) => `<option value="${k}">${esc(v)}</option>`).join('')}`;
  fillCustomize(); $('customize').showModal();
});
$('panelList').addEventListener('change', (e) => {
  const id = e.target.dataset.show; if (!id) return;
  const set = new Set(prefs.hidden); e.target.checked ? set.delete(id) : set.add(id);
  prefs.hidden = [...set]; savePrefs(); render();
});
$('panelList').addEventListener('click', (e) => {
  const b = e.target.closest('[data-move]'); if (!b) return;
  const ids = orderedSections().map((s) => s.id), i = +b.dataset.move, j = i + +b.dataset.dir;
  [ids[i], ids[j]] = [ids[j], ids[i]]; prefs.order = ids; savePrefs(); fillCustomize(); render();
});
$('cAdd').addEventListener('click', () => {
  const c = { id: Math.random().toString(36).slice(2, 9), event: $('cEvent').value, measure: $('cMeasure').value, by: $('cBy').value, split: $('cSplit').value };
  if (c.split === c.by) c.split = '';
  c.title = $('cTitle').value.trim() || describe(c);
  prefs.custom = [...prefs.custom, c]; $('cTitle').value = '';
  savePrefs(); fillCustomize(); render();
});
$('customList').addEventListener('click', (e) => {
  const id = e.target.dataset.remove; if (!id) return;
  prefs.custom = prefs.custom.filter((c) => c.id !== id); savePrefs(); fillCustomize(); render();
});
$('saveDefault').addEventListener('click', () => {
  prefs.defaultView = { range: state.range, from: state.from, to: state.to, step: state.step, filters: state.filters, test: state.test };
  savePrefs(); status('Saved as your default view');
});
$('resetAll').addEventListener('click', () => {
  prefs = { hidden: [], order: [], custom: [], defaultView: null }; savePrefs(); fillCustomize(); render();
});

// ---------------------------------------------------------------------------------------------
// Start

(async () => {
  try { prefs = { hidden: [], order: [], custom: [], defaultView: null, ...(await api('/api/prefs')) }; } catch { /* defaults */ }
  if (!readHash() && prefs.defaultView) Object.assign(state, prefs.defaultView);
  if (!state.filters) state.filters = {};
  load();
})();
