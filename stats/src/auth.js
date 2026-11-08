// Sign-in for stats.heymode.app: email + password, then a 6-digit code sent to that email.
// All checks happen here on Cloudflare; the browser only shows plain forms.
//
// - Only env.ALLOWED_EMAIL can sign in. A wrong email or password gets the same answer (and takes the same time),
//   and no code is sent.
// - The password is checked against env.PASSWORD_HASH (PBKDF2-SHA256, see scripts/set-password.mjs).
// - The code is valid 10 minutes, 5 tries; at most 5 codes an hour are sent.
// - 10 failed tries from one IP address in 15 minutes blocks that address for 15 minutes.
// - Sessions last 7 days, are kept in KV (signing out ends them for real) and live in a __Host- cookie
//   (HttpOnly, Secure, SameSite=Strict).

import { EmailMessage } from 'cloudflare:email';

const enc = new TextEncoder();
const SESSION_DAYS = 7;
const CODE_MINUTES = 10;

const b64u = (buf) => btoa(String.fromCharCode(...new Uint8Array(buf))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
const unb64 = (s) => Uint8Array.from(atob(s.replace(/-/g, '+').replace(/_/g, '/')), (c) => c.charCodeAt(0));
const randomId = (n = 24) => b64u(crypto.getRandomValues(new Uint8Array(n)));

function same(a, b) {
  const x = enc.encode(a), y = enc.encode(b);
  if (x.length !== y.length) { crypto.subtle.timingSafeEqual(x, x); return false; }
  return crypto.subtle.timingSafeEqual(x, y);
}
async function hmac(secret, data) {
  const key = await crypto.subtle.importKey('raw', enc.encode(secret), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
  return b64u(await crypto.subtle.sign('HMAC', key, enc.encode(data)));
}
async function sha256(s) { return b64u(await crypto.subtle.digest('SHA-256', enc.encode(s))); }

/** "pbkdf2$<iterations>$<salt>$<hash>", as made by scripts/set-password.mjs. */
async function passwordMatches(password, stored) {
  const [kind, iter, salt, hash] = String(stored || '').split('$');
  // A missing or broken hash still costs the same work, so timing never tells which part was wrong.
  const ok = kind === 'pbkdf2' && +iter > 0 && salt && hash;
  const key = await crypto.subtle.importKey('raw', enc.encode(String(password)), 'PBKDF2', false, ['deriveBits']);
  const bits = await crypto.subtle.deriveBits({ name: 'PBKDF2', hash: 'SHA-256', salt: ok ? unb64(salt) : new Uint8Array(16), iterations: ok ? +iter : 100000 }, key, 256);
  return ok ? same(b64u(bits), hash) : false;
}

const normEmail = (e) => String(e || '').trim().toLowerCase();

function cookie(name, value, maxAge) {
  return `${name}=${value}; Path=/; HttpOnly; Secure; SameSite=Strict; Max-Age=${maxAge}`;
}
function readCookie(request, name) {
  const m = (request.headers.get('cookie') || '').match(new RegExp(`(?:^|;\\s*)${name}=([^;]+)`));
  return m ? m[1] : null;
}

/** Counts a failure for this IP; true once it's over the limit. */
async function tooMany(env, ip, add) {
  const key = `rl:${ip}`;
  const n = +(await env.KV.get(key)) || 0;
  if (add) await env.KV.put(key, String(n + 1), { expirationTtl: 900 });
  return n + (add ? 1 : 0) >= 10;
}

// ---- sessions -----------------------------------------------------------------------------

export async function session(request, env) {
  const raw = readCookie(request, '__Host-s');
  if (!raw || !env.SESSION_SECRET) return false;
  const [id, sig] = raw.split('.');
  if (!id || !sig || !same(sig, await hmac(env.SESSION_SECRET, `s.${id}`))) return false;
  return (await env.KV.get(`sess:${id}`)) === '1';
}

async function startSession(env) {
  const id = randomId();
  await env.KV.put(`sess:${id}`, '1', { expirationTtl: SESSION_DAYS * 86400 });
  return cookie('__Host-s', `${id}.${await hmac(env.SESSION_SECRET, `s.${id}`)}`, SESSION_DAYS * 86400);
}

export async function signOut(request, env) {
  const raw = readCookie(request, '__Host-s');
  if (raw) await env.KV.delete(`sess:${raw.split('.')[0]}`);
  return cookie('__Host-s', '', 0);
}

// ---- step 1: email + password ---------------------------------------------------------------

/** Returns { ok, error, setCookie }. On success a code has been emailed and a short-lived "pending" cookie is set. */
export async function checkPassword(request, env, form) {
  const ip = request.headers.get('cf-connecting-ip') || 'local';
  if (await tooMany(env, ip, false)) return { ok: false, error: 'Too many tries. Wait 15 minutes and try again.' };

  const emailOk = !!env.ALLOWED_EMAIL && same(normEmail(form.get('email')), normEmail(env.ALLOWED_EMAIL));
  const passwordOk = await passwordMatches(form.get('password') || '', env.PASSWORD_HASH);
  if (!(emailOk && passwordOk)) {
    await tooMany(env, ip, true);
    return { ok: false, error: "That email and password don't match." };
  }

  // At most 5 codes an hour, so the inbox can't be flooded.
  const hour = `sends:${Math.floor(Date.now() / 3600000)}`;
  const sent = +(await env.KV.get(hour)) || 0;
  if (sent >= 5) return { ok: false, error: 'Too many codes sent this hour. Try again later.' };
  await env.KV.put(hour, String(sent + 1), { expirationTtl: 3700 });

  const code = String(crypto.getRandomValues(new Uint32Array(1))[0] % 1000000).padStart(6, '0');
  const id = randomId();
  await env.KV.put(`otp:${id}`, JSON.stringify({ h: await sha256(`${id}:${code}`), tries: 0 }), { expirationTtl: CODE_MINUTES * 60 });
  try {
    await sendCode(env, code);
  } catch (e) {
    console.error('code email failed', e);
    return { ok: false, error: "Couldn't send the code email. Check Email Routing's destination address is verified." };
  }
  return { ok: true, setCookie: cookie('__Host-p', `${id}.${await hmac(env.SESSION_SECRET, `p.${id}`)}`, CODE_MINUTES * 60) };
}

async function sendCode(env, code) {
  const to = normEmail(env.ALLOWED_EMAIL), from = env.FROM_EMAIL;
  const raw = [
    `From: Mode stats <${from}>`,
    `To: <${to}>`,
    `Subject: ${code} is your Mode stats code`,
    `Message-ID: <${randomId(12)}@heymode.app>`,
    `Date: ${new Date().toUTCString()}`,
    'MIME-Version: 1.0',
    'Content-Type: text/plain; charset=utf-8',
    '',
    `Your code for stats.heymode.app is ${code}`,
    '',
    `It works for ${CODE_MINUTES} minutes. If you didn't just sign in, change the stats password: someone knows it.`,
  ].join('\r\n');
  await env.EMAIL.send(new EmailMessage(from, to, raw));
}

// ---- step 2: the code ---------------------------------------------------------------------

/** The pending sign-in from the cookie, or null. */
export async function pending(request, env) {
  const raw = readCookie(request, '__Host-p');
  if (!raw || !env.SESSION_SECRET) return null;
  const [id, sig] = raw.split('.');
  if (!id || !sig || !same(sig, await hmac(env.SESSION_SECRET, `p.${id}`))) return null;
  return id;
}

/** Returns { ok, error, restart, setCookies }. */
export async function checkCode(request, env, form) {
  const ip = request.headers.get('cf-connecting-ip') || 'local';
  if (await tooMany(env, ip, false)) return { ok: false, error: 'Too many tries. Wait 15 minutes and try again.' };
  const id = await pending(request, env);
  const rec = id && JSON.parse((await env.KV.get(`otp:${id}`)) || 'null');
  if (!rec) return { ok: false, restart: true, error: 'That code has expired. Sign in again.' };

  const code = String(form.get('code') || '').replace(/\s+/g, '');
  if (!same(await sha256(`${id}:${code}`), rec.h)) {
    await tooMany(env, ip, true);
    if (rec.tries + 1 >= 5) {
      await env.KV.delete(`otp:${id}`);
      return { ok: false, restart: true, error: 'Too many wrong codes. Sign in again for a new one.' };
    }
    await env.KV.put(`otp:${id}`, JSON.stringify({ ...rec, tries: rec.tries + 1 }), { expirationTtl: CODE_MINUTES * 60 });
    return { ok: false, error: "That code isn't right." };
  }
  await env.KV.delete(`otp:${id}`);
  return { ok: true, setCookies: [await startSession(env), cookie('__Host-p', '', 0)] };
}

export const clearPending = () => cookie('__Host-p', '', 0);
