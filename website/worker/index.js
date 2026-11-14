// heymode.app: serves the website (dist/), the Mode Setup downloads and the Android app.
//
//   /download/mac | windows | windows-msi | linux | linux-deb   that installer
//   /download/android                                           the Mode app for Android (mode-android.apk)
//   /download                                                   the right one for the visitor's device (Android: the app)
//
//   /download/v/<tag>/<file>                                    a file from a specific release (the Releases page)
//   /api/stars                                                  the GitHub star count (for the star button)
//   /api/releases                                               the releases and their notes (the Releases page)
//   /api/e                                                      website analytics events (worker/analytics.js)
//
// The file comes from the newest GitHub release but is sent from heymode.app as a download, so the
// browser stays on the page and never shows GitHub. Cloudflare caches each file near the visitor.
// Until a release is published, the link shows a short "almost here" page instead of GitHub's 404.

import { collect, write } from './analytics.js';

const REPO = 'heymode/mode';
const CACHE_VERSION = 2;
// `release` is the file a release must carry to count as the newest for that download: Mode Setup releases
// ("Mode-Desktop-App-…") carry the Mac installer, Mode releases ("Mode-Mobile-Build-…") carry the APK.
const SETUP = 'Mode-Setup-mac.dmg';
const FILES = {
  mac: { file: 'Mode-Setup-mac.dmg', name: 'Mode Setup for Mac', release: SETUP },
  windows: { file: 'Mode-Setup-windows.exe', name: 'Mode Setup for Windows', release: SETUP },
  'windows-msi': { file: 'Mode-Setup-windows.msi', name: 'Mode Setup for Windows', release: SETUP },
  linux: { file: 'Mode-Setup-linux.AppImage', name: 'Mode Setup for Linux', release: SETUP },
  'linux-deb': { file: 'Mode-Setup-linux.deb', name: 'Mode Setup for Linux', release: SETUP },
  android: { file: 'mode-android.apk', name: 'Mode for Android', release: 'mode-android.apk' },
};

/** What fits the visitor's device: Android gets the app itself, a computer gets its Mode Setup installer,
    iPhone and iPad get null (Mode Setup runs on a computer, so they go to the instructions). */
function detect(ua = '') {
  if (/Android/i.test(ua)) return 'android';
  if (/iPhone|iPad|iPod|Mobile/i.test(ua)) return null;
  if (/Windows/i.test(ua)) return 'windows';
  if (/Macintosh|Mac OS X/i.test(ua)) return 'mac';
  if (/Linux|X11|CrOS/i.test(ua)) return 'linux';
  return null;
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname === '/api/stars') return stars(url.searchParams.has('fresh'));
    if (url.pathname === '/api/releases') return releases();
    if (url.pathname === '/api/e') return collect(request, env);
    const v = url.pathname.match(/^\/download\/v\/([\w.-]+)\/([\w.-]+)$/);
    if (v) return counted(env, request, url, 'version', v[1], v[2],
      await send(request, `https://github.com/${REPO}/releases/download/${v[1]}/${v[2]}`, v[2], 'Mode', v[1]));
    const m = url.pathname.match(/^\/download\/?([a-z-]*)\/?$/);
    if (!m) return env.ASSETS.fetch(request);

    const key = m[1] || detect(request.headers.get('user-agent') || '');
    const entry = FILES[key];
    if (!entry) return Response.redirect(new URL('/#get', url).toString(), 302);

    // Ask which release is newest, then fetch that exact version's file: each release has its own address,
    // so no cache (Cloudflare's or the browser's) can hand out an older installer.
    const tag = await latestTag(entry.release);
    const from = tag ? `https://github.com/${REPO}/releases/download/${tag}/${entry.file}`
                     : `https://github.com/${REPO}/releases/latest/download/${entry.file}`;
    return counted(env, request, url, m[1] ? key : `auto-${key}`, tag, entry.file, await send(request, from, entry.file, entry.name, tag));
  },
};

/** Counts a download as it goes out: which file, which version, and whether it was ready (or "almost here").
    The site adds the visitor's random ID (?vid=) to its download links; links from elsewhere have none. */
function counted(env, request, url, target, tag, file, response) {
  if (request.method === 'GET') {
    const ready = !(response.headers.get('content-type') || '').startsWith('text/html');
    write(env, request, { v: url.searchParams.get('vid') || '', r: hostOf(request.headers.get('referer')) },
      { e: 'download_sent', p: url.pathname, a: url.searchParams.get('where') || '', t: target,
        d: (tag || '').replace(/^Mode-Desktop-App-|^Mode-Mobile-Build-|^v/, ''), x: file, val: ready ? 1 : 0 });
  }
  return response;
}
const hostOf = (u) => { try { return u ? new URL(u).hostname : ''; } catch { return ''; } };

/** The tag of the newest published release that carries `asset` (e.g. "Mode-Desktop-App-0.1.3" for the Mac installer,
    "Mode-Mobile-Build-0.2.1" for the APK), checked at most once a minute; null if GitHub can't say. Mode and Mode Setup
    are released separately ("Mode-Mobile-Build-…" and "Mode-Desktop-App-…"); the first releases ("v0.1.0" to "v0.1.2")
    held both. GitHub's own "Latest" is the newest Mode, so it isn't used for Mode Setup. */
async function latestTag(asset) {
  try {
    const res = await fetch(`https://api.github.com/repos/${REPO}/releases?per_page=50`, {
      headers: { 'User-Agent': 'heymode.app', Accept: 'application/vnd.github+json' },
      cf: { cacheEverything: true, cacheTtlByStatus: { '200-299': 60, '300-599': 0 } },
    });
    if (!res.ok) return null;
    let best = null;
    for (const r of await res.json()) {
      if (r.draft || r.prerelease || !r.assets.some((a) => a.name === asset)) continue;
      const v = (r.tag_name.match(/^(?:Mode-Desktop-App-|Mode-Mobile-Build-|v)(\d[\w.]*)$/) || [])[1];
      if (v && (!best || newer(v, best.v))) best = { v, tag: r.tag_name };
    }
    return best ? best.tag : null;
  } catch { return null; }
}

/** "0.1.10" is newer than "0.1.9". */
function newer(a, b) {
  const pa = a.split('.').map(Number), pb = b.split('.').map(Number);
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const d = (pa[i] || 0) - (pb[i] || 0);
    if (d) return d > 0;
  }
  return false;
}

/** Fetches a release file and sends it from heymode.app as a download. A file at a version's own address never
    changes, so Cloudflare keeps it a day; the browser keeps nothing, so it always gets what we serve now. */
async function send(request, from, file, name, tag) {
  const upstream = await fetch(`${from}?v=${CACHE_VERSION}`, {
    method: request.method === 'HEAD' ? 'HEAD' : 'GET',
    redirect: 'follow',
    cf: { cacheEverything: true, cacheTtlByStatus: { '200-299': tag ? 86400 : 300, '300-599': 0 } },
  });
  if (!upstream.ok) return almostHere(name);
  const headers = new Headers({
    // Android's package installer looks for this type; everything else is a plain download.
    'Content-Type': file.endsWith('.apk') ? 'application/vnd.android.package-archive' : 'application/octet-stream',
    'Content-Disposition': `attachment; filename="${file}"`,
    'Cache-Control': 'no-store',
    'X-Content-Type-Options': 'nosniff',
  });
  if (tag) headers.set('X-Mode-Release', tag);
  const len = upstream.headers.get('content-length');
  if (len) headers.set('Content-Length', len);
  return new Response(request.method === 'HEAD' ? null : upstream.body, { status: 200, headers });
}

/** Published releases with their notes and files, for the Releases page. Cached at Cloudflare for 10 minutes. */
async function releases() {
  // Checked at most once a minute, so a release you publish or delete shows up (or goes) within a minute.
  const res = await fetch(`https://api.github.com/repos/${REPO}/releases?per_page=30&page=1`, {
    headers: { 'User-Agent': 'heymode.app', Accept: 'application/vnd.github+json' },
    cf: { cacheEverything: true, cacheTtlByStatus: { '200-299': 60, '300-599': 0 } },
  });
  const list = res.ok ? await res.json() : [];
  const out = list.filter((r) => !r.draft).map((r) => ({
    tag: r.tag_name, name: r.name || r.tag_name, date: r.published_at, prerelease: r.prerelease, notes: r.body || '',
    files: r.assets.map((a) => ({ name: a.name, size: a.size })),
  }));
  return new Response(JSON.stringify(out), { headers: { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' } });
}

/** The repo's star count, for the GitHub button. Cached at Cloudflare for a minute; `?fresh` skips that
    (the page asks fresh when you come back to it, e.g. right after starring on GitHub). */
async function stars(fresh) {
  const res = await fetch(`https://api.github.com/repos/${REPO}`, {
    headers: { 'User-Agent': 'heymode.app', Accept: 'application/vnd.github+json' },
    cf: fresh ? { cacheTtl: 0 } : { cacheEverything: true, cacheTtlByStatus: { '200-299': 60, '300-599': 0 } },
  });
  const n = res.ok ? (await res.json()).stargazers_count : null;
  return new Response(JSON.stringify({ stars: typeof n === 'number' ? n : null }), {
    headers: { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' },
  });
}

/** `name` is what's coming, e.g. "Mode Setup for Mac" or "Mode for Android". */
function almostHere(name) {
  const html = `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>${name} is almost here</title><link rel="icon" href="/favicon.svg">
<style>
:root{color-scheme:light dark;--p:#F5F3EF;--i:#1A1A1F;--s:#5E5C66;--c:#fff}
@media (prefers-color-scheme:dark){:root{--p:#111114;--i:#F4F2EE;--s:#A6A3AD;--c:#1C1C21}}
body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:var(--p);color:var(--i);font:17px/1.5 -apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;padding:24px;box-sizing:border-box}
main{max-width:460px;text-align:center}img{width:96px;height:96px}
h1{font-size:34px;line-height:1.1;letter-spacing:-.02em;margin:18px 0 10px}p{color:var(--s);margin:0 0 24px}
a{display:inline-flex;align-items:center;height:48px;padding:0 22px;border-radius:24px;text-decoration:none;font-weight:700;margin:4px}
.ink{background:var(--i);color:var(--p)}.soft{background:var(--c);color:var(--i)}
</style></head><body><main>
<img src="/favicon.svg" alt="">
<h1>${name} is almost here</h1>
<p>We're putting the finishing touches on the first release. Star the project on GitHub and you'll hear the moment it's out.</p>
<a class="ink" href="https://github.com/${REPO}">Star on GitHub</a><a class="soft" href="/">Back to Mode</a>
</main></body></html>`;
  return new Response(html, { status: 200, headers: { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store' } });
}
