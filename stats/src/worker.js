// stats.heymode.app: heymode.app's private analytics dashboard.
//
//   /login        email + password (step 1)      /verify   the emailed code (step 2)      /logout
//   /api/report   everything the dashboard shows    /api/explore   one custom chart
//   /api/options  values for the filter menus       /api/prefs     saved layout and custom charts (GET / PUT)
//   anything else the dashboard (public/), only with a session
//
// Every check runs here. Forms are plain HTML with no browser-side validation; the server decides.

import { checkCode, checkPassword, clearPending, pending, session, signOut } from './auth.js';
import { explore, options, report, scope } from './queries.js';

const SECURITY = {
  'Content-Security-Policy': "default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; font-src 'self'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'",
  'X-Frame-Options': 'DENY',
  'X-Content-Type-Options': 'nosniff',
  'Referrer-Policy': 'same-origin',
  'X-Robots-Tag': 'noindex, nofollow',
  'Cache-Control': 'no-store',
};

function withSecurity(res) {
  const r = new Response(res.body, res);
  for (const [k, v] of Object.entries(SECURITY)) r.headers.set(k, v);
  return r;
}
const redirect = (to, cookies = []) => {
  const h = new Headers({ Location: to });
  for (const c of cookies) h.append('Set-Cookie', c);
  return new Response(null, { status: 303, headers: h });
};
const json = (data, status = 200) => new Response(JSON.stringify(data), { status, headers: { 'Content-Type': 'application/json' } });
/** Form posts and saves must come from this site's own pages. */
const sameOrigin = (request) => request.headers.get('origin') === new URL(request.url).origin;

const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);

function page(title, body, status = 200) {
  return new Response(`<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="robots" content="noindex"><link rel="icon" href="data:image/svg+xml,%3Csvg xmlns=%27http://www.w3.org/2000/svg%27 viewBox=%270 0 16 16%27%3E%3Ccircle cx=%278%27 cy=%278%27 r=%277%27 fill=%27%23eb6834%27/%3E%3C/svg%3E"><title>${esc(title)}</title><style>${AUTH_CSS}</style></head>
<body><main class="box"><p class="brand"><span class="dot"></span>Mode stats</p>${body}</main></body></html>`,
  { status, headers: { 'Content-Type': 'text/html; charset=utf-8' } });
}

const loginPage = (error = '', status = 200) => page('Sign in · Mode stats', `
<h1>Sign in</h1>
${error ? `<p class="err" role="alert">${esc(error)}</p>` : ''}
<form method="post" action="/login" novalidate>
  <label>Email<input name="email" type="text" autocomplete="username" autocapitalize="none" spellcheck="false"></label>
  <label>Password<input name="password" type="password" autocomplete="current-password"></label>
  <button type="submit">Continue</button>
</form>`, status);

const codePage = (error = '', status = 200) => page('Code · Mode stats', `
<h1>Check your email</h1>
<p class="sub">We sent a 6-digit code. It works for 10 minutes.</p>
${error ? `<p class="err" role="alert">${esc(error)}</p>` : ''}
<form method="post" action="/verify" novalidate>
  <label>Code<input name="code" type="text" inputmode="numeric" autocomplete="one-time-code"></label>
  <button type="submit">Sign in</button>
</form>
<form method="post" action="/login/restart" novalidate><button class="link" type="submit">Start again</button></form>`, status);

async function handle(request, env) {
  const url = new URL(request.url), path = url.pathname, method = request.method;

  // ---- sign-in -----------------------------------------------------------------------------
  if (path === '/login') {
    if (method === 'GET') return (await session(request, env)) ? redirect('/') : loginPage();
    if (method !== 'POST' || !sameOrigin(request)) return loginPage('Something went wrong. Try again.', 400);
    const r = await checkPassword(request, env, await request.formData());
    return r.ok ? redirect('/verify', [r.setCookie]) : loginPage(r.error, 401);
  }
  if (path === '/login/restart' && method === 'POST') return redirect('/login', [clearPending()]);
  if (path === '/verify') {
    if (!(await pending(request, env))) return redirect('/login');
    if (method === 'GET') return codePage();
    if (method !== 'POST' || !sameOrigin(request)) return codePage('Something went wrong. Try again.', 400);
    const r = await checkCode(request, env, await request.formData());
    if (r.ok) return redirect('/', r.setCookies);
    return r.restart ? loginPage(r.error, 401) : codePage(r.error, 401);
  }
  if (path === '/logout') {
    if (method !== 'POST' || !sameOrigin(request)) return new Response(null, { status: 405 });
    return redirect('/login', [await signOut(request, env)]);
  }

  // ---- everything below needs a session ------------------------------------------------------
  if (!(await session(request, env))) {
    return path.startsWith('/api/') ? json({ error: 'signed out' }, 401) : redirect('/login');
  }

  if (path.startsWith('/api/')) {
    try {
      if (path === '/api/report' && method === 'GET') return json(await report(env, scope(url)));
      if (path === '/api/explore' && method === 'GET') return json(await explore(env, scope(url), url));
      if (path === '/api/options' && method === 'GET') return json(await options(env, scope(url)));
      if (path === '/api/prefs') {
        if (method === 'GET') return json(JSON.parse((await env.KV.get('prefs')) || '{}'));
        if (method === 'PUT' && !sameOrigin(request)) return json({ error: 'forbidden' }, 403);
        if (method === 'PUT') {
          const text = await request.text();
          if (text.length > 50000) return json({ error: 'too big' }, 413);
          const prefs = JSON.parse(text);
          if (typeof prefs !== 'object' || Array.isArray(prefs) || prefs === null) return json({ error: 'bad prefs' }, 400);
          await env.KV.put('prefs', JSON.stringify(prefs));
          return json({ ok: true });
        }
      }
      return json({ error: 'not found' }, 404);
    } catch (e) {
      const msg = String(e.message || e);
      return json({ error: /^bad /.test(msg) ? msg : 'The analytics query failed. Is CF_API_TOKEN set?' , detail: msg.slice(0, 300) }, /^bad /.test(msg) ? 400 : 502);
    }
  }
  return env.ASSETS.fetch(request);
}

export default {
  async fetch(request, env) {
    return withSecurity(await handle(request, env));
  },
};

const AUTH_CSS = `
:root{--bg:#f6f5f2;--card:#fff;--ink:#0b0b0b;--ink2:#52514e;--line:#e4e2dc;--accent:#2a78d6;--err:#b42318;--errbg:#fdecea;color-scheme:light}
@media (prefers-color-scheme:dark){:root{--bg:#121211;--card:#1a1a19;--ink:#f0efec;--ink2:#c3c2b7;--line:#2e2d2a;--accent:#3987e5;--err:#ff8a80;--errbg:#3a1714;color-scheme:dark}}
*{box-sizing:border-box}body{margin:0;min-height:100vh;display:grid;place-items:center;background:var(--bg);color:var(--ink);font:16px/1.5 system-ui,-apple-system,"Segoe UI",sans-serif;padding:16px}
.box{width:100%;max-width:380px;background:var(--card);border:1px solid var(--line);border-radius:20px;padding:28px}
.brand{display:flex;align-items:center;gap:8px;margin:0 0 20px;font-weight:700;font-size:14px;color:var(--ink2)}
.dot{width:10px;height:10px;border-radius:50%;background:#eb6834}
h1{font-size:24px;margin:0 0 6px;letter-spacing:-.01em}.sub{margin:0 0 16px;color:var(--ink2);font-size:14px}
label{display:block;font-size:14px;font-weight:600;margin:16px 0 0}
input{display:block;width:100%;margin-top:6px;padding:12px 14px;font:inherit;color:var(--ink);background:var(--bg);border:1px solid var(--line);border-radius:12px}
input:focus{outline:2px solid var(--accent);outline-offset:1px}
button{margin-top:22px;width:100%;padding:12px;font:inherit;font-weight:700;border:0;border-radius:12px;background:var(--ink);color:var(--card);cursor:pointer}
button.link{background:none;color:var(--ink2);font-weight:500;margin-top:10px;text-decoration:underline;text-underline-offset:3px}
.err{margin:14px 0 0;padding:10px 12px;border-radius:10px;background:var(--errbg);color:var(--err);font-size:14px}`;
