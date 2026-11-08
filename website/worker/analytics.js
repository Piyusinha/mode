// Website analytics, stored in this Cloudflare account's Analytics Engine dataset "heymode_events".
// No cookies and no IP addresses: a visitor is a random ID the page saves in the browser.
// Each event is one data point. Columns (keep this order, Grafana's queries rely on it):
//
//   index1  visitor ID (also the sampling key)
//   blob1   event          e.g. page_view, download_click, download_sent, film_progress, tour_step
//   blob2   path           e.g. /, /releases
//   blob3   visitor ID     random, saved in the browser ("" for downloads not started from the site)
//   blob4   visit ID       random per browser tab
//   blob5   country        from Cloudflare (two letters)
//   blob6   device         phone | tablet | desktop
//   blob7   system         macOS | Windows | Linux | iOS | Android | other
//   blob8   browser        Safari | Chrome | Firefox | Edge | other
//   blob9   came from      the referring site's name only (page_view)
//   blob10  utm_source     (page_view)
//   blob11  utm_medium     (page_view)
//   blob12  utm_campaign   (page_view)
//   blob13  language       e.g. en-IN
//   blob14  where          which button or section, e.g. header, hero, get, closing, releases
//   blob15  target         what it was about, e.g. mac, chapter name, question, film variant
//   blob16  detail         e.g. version, step, silent/sound, wheel/touch/key
//   blob17  extra
//   double1 value          seconds, percent or step number, depending on the event
//   double2 new visitor    1 on a browser's first visit, else 0
//   double3 screen width   CSS pixels

const BOT = /bot|crawl|spider|slurp|headless|lighthouse|preview|facebookexternalhit|embedly|whatsapp|telegram|curl|wget|python|node-fetch/i;
const s = (v, n = 100) => (typeof v === 'string' ? v.slice(0, n) : '');
const num = (v) => (typeof v === 'number' && Number.isFinite(v) ? v : 0);

function device(ua) {
  if (/iPad|Tablet|Android(?!.*Mobile)/i.test(ua)) return 'tablet';
  if (/iPhone|iPod|Android.*Mobile|Mobile/i.test(ua)) return 'phone';
  return 'desktop';
}
function system(ua) {
  if (/iPhone|iPad|iPod/i.test(ua)) return 'iOS';
  if (/Android/i.test(ua)) return 'Android';
  if (/Windows/i.test(ua)) return 'Windows';
  if (/Macintosh|Mac OS X/i.test(ua)) return 'macOS';
  if (/Linux|X11|CrOS/i.test(ua)) return 'Linux';
  return 'other';
}
function browser(ua) {
  if (/Edg\//.test(ua)) return 'Edge';
  if (/Firefox\//.test(ua)) return 'Firefox';
  if (/Chrome\/|CriOS\//.test(ua)) return 'Chrome';
  if (/Safari\//.test(ua)) return 'Safari';
  return 'other';
}

/** Writes one event. `ctx` = what the request tells us; `e` = the event's own fields. */
export function write(env, request, ctx, e) {
  if (!env.EVENTS) return;
  const ua = request.headers.get('user-agent') || '';
  if (BOT.test(ua)) return;
  const visitor = s(ctx.v, 40);
  env.EVENTS.writeDataPoint({
    indexes: [visitor || 'anon'],
    blobs: [
      s(e.e, 32), s(e.p, 200), visitor, s(ctx.s, 40), s(request.cf?.country, 2), device(ua), system(ua), browser(ua),
      s(ctx.r, 100), s(ctx.u?.[0]), s(ctx.u?.[1]), s(ctx.u?.[2]), s(ctx.l, 20),
      s(e.a), s(e.t), s(e.d), s(e.x),
    ],
    doubles: [num(e.val), num(ctx.n), num(ctx.w)],
  });
}

/** POST /api/e from the page: { v, s, n, w, l, r, u: [source, medium, campaign], ev: [{ e, p, a, t, d, x, val }] }. */
export async function collect(request, env) {
  if (request.method !== 'POST') return new Response(null, { status: 405 });
  const text = await request.text();
  if (text.length > 16000) return new Response(null, { status: 413 });
  let body;
  try { body = JSON.parse(text); } catch { return new Response(null, { status: 400 }); }
  const events = Array.isArray(body?.ev) ? body.ev.slice(0, 40) : [];
  for (const e of events) if (typeof e?.e === 'string' && /^[a-z_]{2,32}$/.test(e.e)) write(env, request, body, e);
  return new Response(null, { status: 204, headers: { 'Cache-Control': 'no-store' } });
}
