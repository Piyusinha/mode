// heymode.app: serves the website (dist/) and the Mode Setup downloads.
//
//   /download/mac | windows | windows-msi | linux | linux-deb   that installer
//   /download                                                   the right one for the visitor's computer
//
//   /download/v/<tag>/<file>                                    a file from a specific release (the Releases page)
//   /api/stars                                                  the GitHub star count (for the star button)
//   /api/releases                                               the releases and their notes (the Releases page)
//
// The file comes from the newest GitHub release but is sent from heymode.app as a download, so the
// browser stays on the page and never shows GitHub. Cloudflare caches each file near the visitor.
// Until a release is published, the link shows a short "almost here" page instead of GitHub's 404.

const REPO = 'Piyusinha/mode';
const CACHE_VERSION = 2;
const FILES = {
  mac: { file: 'Mode-Setup-mac.dmg', label: 'Mac' },
  windows: { file: 'Mode-Setup-windows.exe', label: 'Windows' },
  'windows-msi': { file: 'Mode-Setup-windows.msi', label: 'Windows' },
  linux: { file: 'Mode-Setup-linux.AppImage', label: 'Linux' },
  'linux-deb': { file: 'Mode-Setup-linux.deb', label: 'Linux' },
};

/** Which installer fits the visitor's computer; null for phones and tablets (Mode Setup runs on a computer). */
function detect(ua = '') {
  if (/iPhone|iPad|iPod|Android|Mobile/i.test(ua)) return null;
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
    const v = url.pathname.match(/^\/download\/v\/([\w.-]+)\/([\w.-]+)$/);
    if (v) return send(request, `https://github.com/${REPO}/releases/download/${v[1]}/${v[2]}`, v[2], 'Mode', v[1]);
    const m = url.pathname.match(/^\/download\/?([a-z-]*)\/?$/);
    if (!m) return env.ASSETS.fetch(request);

    const key = m[1] || detect(request.headers.get('user-agent') || '');
    const entry = FILES[key];
    if (!entry) return Response.redirect(new URL('/#get', url).toString(), 302);

    // Ask which release is newest, then fetch that exact version's file: each release has its own address,
    // so no cache (Cloudflare's or the browser's) can hand out an older installer.
    const tag = await latestTag();
    const from = tag ? `https://github.com/${REPO}/releases/download/${tag}/${entry.file}`
                     : `https://github.com/${REPO}/releases/latest/download/${entry.file}`;
    return send(request, from, entry.file, entry.label, tag);
  },
};

/** The newest published release's tag (e.g. "v0.1.1"), checked at most once a minute; null if GitHub can't say. */
async function latestTag() {
  try {
    const res = await fetch(`https://api.github.com/repos/${REPO}/releases/latest`, {
      headers: { 'User-Agent': 'heymode.app', Accept: 'application/vnd.github+json' },
      cf: { cacheEverything: true, cacheTtlByStatus: { '200-299': 60, '300-599': 0 } },
    });
    return res.ok ? (await res.json()).tag_name : null;
  } catch { return null; }
}

/** Fetches a release file and sends it from heymode.app as a download. A file at a version's own address never
    changes, so Cloudflare keeps it a day; the browser keeps nothing, so it always gets what we serve now. */
async function send(request, from, file, label, tag) {
  const upstream = await fetch(`${from}?v=${CACHE_VERSION}`, {
    method: request.method === 'HEAD' ? 'HEAD' : 'GET',
    redirect: 'follow',
    cf: { cacheEverything: true, cacheTtlByStatus: { '200-299': tag ? 86400 : 300, '300-599': 0 } },
  });
  if (!upstream.ok) return almostHere(label);
  const headers = new Headers({
    'Content-Type': 'application/octet-stream',
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
  const res = await fetch(`https://api.github.com/repos/${REPO}/releases?per_page=20`, {
    headers: { 'User-Agent': 'heymode.app', Accept: 'application/vnd.github+json' },
    cf: { cacheEverything: true, cacheTtlByStatus: { '200-299': 600, '300-599': 0 } },
  });
  const list = res.ok ? await res.json() : [];
  const out = list.filter((r) => !r.draft).map((r) => ({
    tag: r.tag_name, name: r.name || r.tag_name, date: r.published_at, prerelease: r.prerelease, notes: r.body || '',
    files: r.assets.map((a) => ({ name: a.name, size: a.size })),
  }));
  return new Response(JSON.stringify(out), { headers: { 'Content-Type': 'application/json', 'Cache-Control': 'public, max-age=300' } });
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

function almostHere(label) {
  const html = `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Mode Setup is almost here</title><link rel="icon" href="/favicon.svg">
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
<h1>Mode Setup for ${label} is almost here</h1>
<p>We're putting the finishing touches on the first release. Star the project on GitHub and you'll hear the moment it's out.</p>
<a class="ink" href="https://github.com/${REPO}">Star on GitHub</a><a class="soft" href="/">Back to Mode</a>
</main></body></html>`;
  return new Response(html, { status: 200, headers: { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store' } });
}
