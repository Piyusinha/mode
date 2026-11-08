// Reads heymode.app's analytics (Analytics Engine dataset, columns in website/worker/analytics.js) through
// Cloudflare's SQL API. The page never sends SQL: it asks for a report or an "explore" with named fields,
// and the SQL is built here from fixed lists. Filter values are checked before they go into a query.

/** Named fields → dataset columns. */
export const DIMS = {
  event: 'blob1', path: 'blob2', country: 'blob5', device: 'blob6', system: 'blob7', browser: 'blob8',
  source: 'blob9', utm_source: 'blob10', utm_medium: 'blob11', campaign: 'blob12', language: 'blob13',
  where: 'blob14', target: 'blob15', detail: 'blob16', extra: 'blob17', value: 'double1',
};
/** Fields the page can filter on. */
export const FILTERS = ['country', 'device', 'system', 'browser', 'source', 'utm_source', 'utm_medium', 'campaign', 'path', 'language'];
export const EVENTS = [
  'page_view', 'engaged', 'scroll_depth', 'section_view', 'nav_click', 'star_click', 'github_click', 'faq_open',
  'download_click', 'get_mode_click', 'download_sent', 'release_download', 'release_open', 'releases_error',
  'film_seen', 'film_play', 'film_sound', 'film_fullscreen', 'film_pause', 'film_progress', 'film_watch', 'film_end',
  'film_error', 'film_fallback', 'tour_step', 'tour_complete', 'tour_jump',
];
const MEASURES = {
  events: 'SUM(_sample_interval)',
  visitors: "count(DISTINCT blob3)",
  visits: 'count(DISTINCT blob4)',
  total: 'SUM(double1 * _sample_interval)',
};

const safe = (v) => typeof v === 'string' && v.length <= 120 && !/['\\\u0000-\u001f]/.test(v);
const q = (v) => `'${v}'`;

/** Reads range, step and filters from the URL; throws on anything it doesn't accept. */
export function scope(url) {
  const p = url.searchParams;
  const now = Math.floor(Date.now() / 1000);
  let to = Math.min(+p.get('to') || now, now + 60), from = +p.get('from') || to - 30 * 86400;
  if (!(from > 0 && to > from)) throw new Error('bad range');
  from = Math.max(from, to - 92 * 86400); // Analytics Engine keeps three months
  const step = { hour: "INTERVAL '1' HOUR", day: "INTERVAL '1' DAY", week: "INTERVAL '7' DAY" }[p.get('step') || 'day'];
  if (!step) throw new Error('bad step');
  const where = [];
  for (const f of FILTERS) {
    const v = p.get(`f.${f}`);
    if (v === null) continue;
    if (!safe(v)) throw new Error('bad filter');
    where.push(`${DIMS[f]} = ${q(v)}`);
  }
  // Test visits made while building the site are hidden unless asked for.
  if (p.get('test') !== '1') where.push("blob10 != 'claude-test'", "blob14 != 'test'");
  return { from, to, step, where };
}

const W = (s, ...more) => [`timestamp >= toDateTime(${s.from})`, `timestamp < toDateTime(${s.to})`, ...s.where, ...more].join(' AND ');
const prev = (s) => ({ ...s, from: s.from - (s.to - s.from), to: s.from });

/** Runs one query; numbers come back as numbers. */
export async function sql(env, text) {
  if (!env.CF_API_TOKEN) throw new Error('CF_API_TOKEN is not set. Run: npx wrangler@4 secret put CF_API_TOKEN (in stats/)');
  const res = await fetch(`https://api.cloudflare.com/client/v4/accounts/${env.ACCOUNT_ID}/analytics_engine/sql`, {
    method: 'POST', headers: { Authorization: `Bearer ${env.CF_API_TOKEN}` }, body: `${text} FORMAT JSON`,
  });
  const body = await res.text();
  if (!res.ok) throw new Error(/9106|Authentication/.test(body) ? 'Cloudflare refused CF_API_TOKEN: it needs Account > Account Analytics > Read on this account.' : `Analytics query failed (${res.status}): ${body.slice(0, 200)}`);
  const j = JSON.parse(body);
  const num = new Set(j.meta.filter((m) => /Int|Float|Decimal/.test(m.type)).map((m) => m.name));
  return j.data.map((r) => { for (const k of num) r[k] = Number(r[k]); return r; });
}

/** Runs named queries a few at a time. */
async function all(env, named) {
  const out = {}, keys = Object.keys(named);
  for (let i = 0; i < keys.length; i += 6) {
    await Promise.all(keys.slice(i, i + 6).map(async (k) => { out[k] = await sql(env, named[k]); }));
  }
  return out;
}

/** Everything the dashboard shows, for one range and set of filters. */
export async function report(env, s) {
  const ds = env.DATASET, F = `FROM ${ds} WHERE`, P = prev(s);
  const byEvent = (x) => `SELECT blob1 AS e, SUM(_sample_interval) AS n, count(DISTINCT blob3) AS u, count(DISTINCT blob4) AS s, SUM(double1 * _sample_interval) AS v ${F} ${W(x)} GROUP BY e`;
  const newVisitors = (x) => `SELECT count(DISTINCT blob3) AS u ${F} ${W(x, "blob1 = 'page_view'", 'double2 = 1')}`;
  const visitorsBy = (col) => `SELECT ${col} AS k, count(DISTINCT blob3) AS u, SUM(_sample_interval) AS n ${F} ${W(s, "blob1 = 'page_view'")} GROUP BY k ORDER BY u DESC LIMIT 50`;
  const r = await all(env, {
    events: byEvent(s),
    eventsPrev: byEvent(P),
    newVisitors: newVisitors(s),
    newVisitorsPrev: newVisitors(P),
    series: `SELECT toUnixTimestamp(toStartOfInterval(timestamp, ${s.step})) AS t, blob1 AS e, SUM(_sample_interval) AS n, count(DISTINCT blob3) AS u ${F} ${W(s, "blob1 IN ('page_view', 'download_click', 'release_download', 'download_sent')")} GROUP BY t, e ORDER BY t`,
    funnel: `SELECT blob1 AS e, count(DISTINCT blob3) AS u ${F} ${W(s, "blob3 != ''", "blob1 IN ('page_view', 'film_play', 'film_sound', 'tour_step', 'tour_complete', 'download_click', 'release_download', 'download_sent')")} GROUP BY e`,
    funnelGet: `SELECT count(DISTINCT blob3) AS u ${F} ${W(s, "blob1 = 'section_view'", "blob15 = 'get'")}`,
    downloads: `SELECT blob16 AS version, blob17 AS file, blob15 AS how, double1 AS ready, SUM(_sample_interval) AS n ${F} ${W(s, "blob1 = 'download_sent'")} GROUP BY version, file, how, ready ORDER BY n DESC`,
    downloadsFrom: `SELECT blob9 AS site, blob14 AS button, SUM(_sample_interval) AS n ${F} ${W(s, "blob1 = 'download_sent'")} GROUP BY site, button ORDER BY n DESC LIMIT 50`,
    buttons: `SELECT blob1 AS e, blob14 AS place, blob15 AS target, blob16 AS detail, SUM(_sample_interval) AS n, count(DISTINCT blob3) AS u ${F} ${W(s, "blob1 IN ('download_click', 'release_download', 'get_mode_click')")} GROUP BY e, place, target, detail ORDER BY n DESC LIMIT 200`,
    film: `SELECT blob1 AS e, blob16 AS mode, if(blob1 = 'film_progress', double1, 0.0) AS val, blob15 AS variant, SUM(_sample_interval) AS n, count(DISTINCT blob3) AS u, SUM(double1 * _sample_interval) AS v ${F} ${W(s, "blob1 LIKE 'film_%'")} GROUP BY e, mode, val, variant`,
    tour: `SELECT double1 AS step, blob15 AS chapter, blob16 AS screen, count(DISTINCT blob3) AS u ${F} ${W(s, "blob1 = 'tour_step'")} GROUP BY step, chapter, screen ORDER BY step`,
    tourHow: `SELECT blob17 AS how, SUM(_sample_interval) AS n ${F} ${W(s, "blob1 = 'tour_step'")} GROUP BY how ORDER BY n DESC`,
    tourJumps: `SELECT blob14 AS place, blob15 AS chapter, SUM(_sample_interval) AS n ${F} ${W(s, "blob1 = 'tour_jump'")} GROUP BY place, chapter ORDER BY n DESC`,
    sections: `SELECT blob15 AS k, count(DISTINCT blob3) AS u ${F} ${W(s, "blob1 = 'section_view'")} GROUP BY k ORDER BY u DESC`,
    scroll: `SELECT double1 AS k, count(DISTINCT blob3) AS u ${F} ${W(s, "blob1 = 'scroll_depth'")} GROUP BY k ORDER BY k`,
    clicks: `SELECT blob1 AS e, blob14 AS place, blob15 AS target, SUM(_sample_interval) AS n, count(DISTINCT blob3) AS u ${F} ${W(s, "blob1 IN ('nav_click', 'star_click', 'github_click', 'release_open')")} GROUP BY e, place, target ORDER BY n DESC LIMIT 200`,
    faq: `SELECT blob15 AS k, SUM(_sample_interval) AS n, count(DISTINCT blob3) AS u ${F} ${W(s, "blob1 = 'faq_open'")} GROUP BY k ORDER BY n DESC`,
    problems: `SELECT blob1 AS e, blob15 AS about, blob17 AS file, SUM(_sample_interval) AS n ${F} ${W(s, "(blob1 IN ('film_error', 'film_fallback', 'releases_error') OR (blob1 = 'download_sent' AND double1 = 0))")} GROUP BY e, about, file ORDER BY n DESC`,
    visits: `SELECT blob3 AS id, count(DISTINCT blob4) AS visits ${F} ${W(s, "blob1 = 'page_view'", "blob3 != ''")} GROUP BY id LIMIT 50000`,
    country: visitorsBy('blob5'), device: visitorsBy('blob6'), system: visitorsBy('blob7'), browser: visitorsBy('blob8'),
    source: visitorsBy('blob9'), utm_source: visitorsBy('blob10'), campaign: visitorsBy('blob12'), language: visitorsBy('blob13'), path: visitorsBy('blob2'),
    recent: `SELECT toUnixTimestamp(timestamp) AS at, blob1 AS e, blob2 AS path, blob3 AS visitor, blob5 AS country, blob6 AS device, blob7 AS system, blob14 AS place, blob15 AS target, blob16 AS detail, double1 AS val ${F} ${W(s, "blob1 != 'engaged'")} ORDER BY at DESC LIMIT 100`,
  });

  // How many visits each visitor made, as a distribution (the per-visitor rows stay here).
  const buckets = { '1 visit': 0, '2 visits': 0, '3–5 visits': 0, '6+ visits': 0 };
  for (const { visits } of r.visits) buckets[visits <= 1 ? '1 visit' : visits === 2 ? '2 visits' : visits <= 5 ? '3–5 visits' : '6+ visits']++;
  r.visits = Object.entries(buckets).map(([k, u]) => ({ k, u }));
  // Visitor IDs are shortened before they leave the server.
  r.recent = r.recent.map((x) => ({ ...x, visitor: x.visitor ? x.visitor.slice(0, 6) : '' }));
  return { range: { from: s.from, to: s.to }, ...r };
}

/** One custom chart: a measure of one event (or all), split by a field or over time, optionally by a second field. */
export async function explore(env, s, url) {
  const p = url.searchParams;
  const event = p.get('event') || 'page_view', measure = p.get('measure') || 'visitors';
  const by = p.get('by') || 'time', split = p.get('split') || '';
  if (event !== 'all' && !EVENTS.includes(event)) throw new Error('bad event');
  if (!MEASURES[measure]) throw new Error('bad measure');
  if (by !== 'time' && !DIMS[by]) throw new Error('bad field');
  if (split && !DIMS[split]) throw new Error('bad split');
  const cond = event === 'all' ? [] : [`blob1 = '${event}'`];
  const kx = by === 'time' ? `toUnixTimestamp(toStartOfInterval(timestamp, ${s.step}))` : DIMS[by];
  const cols = [`${kx} AS k`, ...(split ? [`${DIMS[split]} AS g`] : []), `${MEASURES[measure]} AS y`];
  const group = split ? 'k, g' : 'k';
  const order = by === 'time' ? 'k' : 'y DESC';
  const rows = await sql(env, `SELECT ${cols.join(', ')} FROM ${env.DATASET} WHERE ${W(s, ...cond)} GROUP BY ${group} ORDER BY ${order} LIMIT 500`);
  return { range: { from: s.from, to: s.to }, by, split, rows };
}

/** Values seen for each filter field in the range, for the filter menus. */
export async function options(env, s) {
  const named = {};
  for (const f of FILTERS) named[f] = `SELECT ${DIMS[f]} AS k, count(DISTINCT blob3) AS u FROM ${env.DATASET} WHERE ${W({ ...s, where: [] })} GROUP BY k ORDER BY u DESC LIMIT 100`;
  return all(env, named);
}
