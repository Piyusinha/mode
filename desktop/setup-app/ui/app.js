// Mode Setup's window: every screen on the canvas's "Mode Setup" boards, in the order the flow board
// shows. The engine (src/main.rs) does the work; this file only draws and decides what comes next.
// Each wait moves on by itself when the iPhone has done its part.
'use strict';

// ---- talking to the engine ---------------------------------------------------------------------
const TAURI = window.__TAURI__;
const MOCK = new URLSearchParams(location.search).get('mock');
const call = (cmd, args = {}) => MOCK ? new Promise(() => {}) : TAURI.core.invoke(cmd, args);
const listen = (name, f) => { if (!MOCK) TAURI.event.listen(name, e => f(e.payload)); };

// ---- icons (the boards' strokes) -------------------------------------------------------------------
const P = {
  phone: '<rect x="6.5" y="2.5" width="11" height="19" rx="2.6"></rect><path d="M10.5 18.5h3"></path>',
  check: '<path d="M5 12.5l4.2 4.2L19 7"></path>',
  gear: '<circle cx="12" cy="12" r="3.2"></circle><path d="M12 2.8v3M12 18.2v3M2.8 12h3M18.2 12h3M5.5 5.5l2.1 2.1M16.4 16.4l2.1 2.1M5.5 18.5l2.1-2.1M16.4 7.6l2.1-2.1"></path>',
  renew: '<path d="M20 12a8 8 0 0 1-14.3 4.9M4 12a8 8 0 0 1 14.3-4.9"></path><path d="M18.5 3v4.2h-4.2M5.5 21v-4.2h4.2"></path>',
  ext: '<path d="M14 4h6v6M20 4l-9 9M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5"></path>',
  copy: '<rect x="8" y="8" width="12" height="12" rx="2.5"></rect><path d="M16 8V5.5A1.5 1.5 0 0 0 14.5 4h-9A1.5 1.5 0 0 0 4 5.5v9A1.5 1.5 0 0 0 5.5 16H8"></path>',
  down: '<path d="M12 4v11M7 10.5l5 5 5-5M5 20h14"></path>',
  plug: '<path d="M9 3v5M15 3v5M7 8h10v3a5 5 0 0 1-10 0zM12 16v5"></path>',
  key: '<circle cx="8" cy="15" r="4"></circle><path d="M11 12l9-9M17 6l3 3M15 8l2 2"></path>',
  out: '<path d="M15 4h4a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1h-4M10 16l-4-4 4-4M6 12h10"></path>',
  shield: '<path d="M12 3l7 3v6c0 4.4-3 7.6-7 9-4-1.4-7-4.6-7-9V6z"></path><path d="M9 12l2.2 2.2L15.5 10"></path>',
  wifi: '<path d="M2.5 9a14 14 0 0 1 19 0M5.5 12.5a9.5 9.5 0 0 1 13 0M8.6 16a5 5 0 0 1 6.8 0"></path><circle cx="12" cy="19.3" r="0.9" fill="currentColor"></circle>',
  win: '<rect x="3" y="3" width="8" height="8" rx="1"></rect><rect x="13" y="3" width="8" height="8" rx="1"></rect><rect x="3" y="13" width="8" height="8" rx="1"></rect><rect x="13" y="13" width="8" height="8" rx="1"></rect>',
  lock: '<rect x="5" y="11" width="14" height="10" rx="2"></rect><path d="M8 11V8a4 4 0 0 1 8 0v3"></path>',
  store: '<path d="M8 17l4-10 4 10M6.5 13h11"></path><circle cx="12" cy="12" r="9.5"></circle>',
};
const svg = (name, size = 16, sw = 2.2) =>
  `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="${sw}" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${P[name]}</svg>`;
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const mark = (size = 32) => `<span class="mark" aria-hidden="true" style="width:${size}px;height:${size}px;border-radius:${Math.round(size * 0.29)}px"><svg width="${Math.round(size * 0.53)}" height="${Math.round(size * 0.53)}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round"><rect x="8.3" y="5" width="7.4" height="14" rx="2.2"></rect></svg></span>`;
const tile = (icon, kind = '', size = 36) => `<span class="tile ${kind}" aria-hidden="true" style="width:${size}px;height:${size}px;border-radius:${Math.round(size * 0.28)}px">${svg(icon, Math.round(size * 0.52))}</span>`;
const btn = (text, action, kind = '', attrs = '') => `<button type="button" class="btn ${kind}" data-act="${action}" ${attrs}>${esc(text)}</button>`;
const btnIcon = (text, icon, action, kind = '') => `<button type="button" class="btn ${kind}" data-act="${action}">${svg(icon, 15)}${esc(text)}</button>`;
const link = (text, url) => `<button type="button" class="link" data-url="${esc(url)}">${esc(text)}${svg('ext', 12)}</button>`;
const pill = (text, kind = '') => `<span class="pill ${kind}">${esc(text)}</span>`;
const spinner = (size = 22) => `<svg class="spin" style="width:${size}px;height:${size}px" viewBox="0 0 24 24" role="img" aria-label="In progress"><circle cx="12" cy="12" r="9" fill="none" stroke="var(--fill)" stroke-width="2.6"></circle><path d="M12 3a9 9 0 0 1 9 9" fill="none" stroke="var(--accent)" stroke-width="2.6" stroke-linecap="round"></path></svg>`;
const icon = kind => ({
  done: `<span class="ic done" role="img" aria-label="Done">${svg('check', 14, 3)}</span>`,
  wait: spinner(),
  fail: '<span class="ic fail" role="img" aria-label="Problem">!</span>',
  paused: '<span class="ic paused" role="img" aria-label="Paused"><i></i><i></i></span>',
  todo: '<span class="ic todo" role="img" aria-label="Not yet"></span>',
}[kind]);
const row = (kind, title, sub = '', right = '', muted = false, lead = null) =>
  `<div class="row${muted ? ' muted' : ''}">${lead ?? icon(kind)}<span class="t"><b>${esc(title)}</b>${sub ? `<span>${sub}</span>` : ''}</span>${right}</div>`;
const box = (inner, cls = '') => `<div class="box ${cls}">${inner}</div>`;
const callout = (html, kind = '', ic = null, action = '') => `<div class="callout ${kind}">${ic ? svg(ic, 16) : ''}<span class="t">${html}</span>${action}</div>`;
const bar = f => { const pct = Math.round(Math.max(0, Math.min(1, f)) * 100); return `<span class="bar" role="progressbar" aria-valuenow="${pct}" aria-valuemin="0" aria-valuemax="100"><span class="track"><i style="width:${pct}%"></i></span><span>${pct}%</span></span>`; };
const radioMark = on => on
  ? '<svg class="mark-on" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="11" fill="var(--accent)"></circle><path d="M7 12.5l3.2 3.2L17 9" fill="none" stroke="#fff" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"></path></svg>'
  : '<span class="mark-off" aria-hidden="true"></span>';
const radio = (title, sub, on, action, right = '', lead = '') =>
  `<button type="button" role="radio" aria-checked="${on}" class="radio" data-act="${action}">${lead}<span class="t"><b>${esc(title)}</b><span>${sub}</span></span>${right}${radioMark(on)}</button>`;
const head = (title, sub = '', top = '') => `<header class="head">${top}<h1>${esc(title)}</h1>${sub ? `<p>${sub}</p>` : ''}</header>`;
const kv = (k, v, right = '') => `<div class="kv"><span class="k">${esc(k)}</span><span class="v">${v}</span>${right}</div>`;
const fmtDate = iso => { if (!iso) return 'unknown'; const d = new Date(iso); return d.toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short', year: d.getFullYear() !== new Date().getFullYear() ? 'numeric' : undefined }); };
const daysLeft = iso => iso ? Math.ceil((new Date(iso) - new Date()) / 86400000) : null;

// ---- state ------------------------------------------------------------------------------------------
const STEPS = ['Connect your iPhone', 'Apple account', 'Install Mode', 'On your iPhone', 'Ready'];
const S = {
  screen: 'welcome', phones: [], phone: null, readiness: null, shownDevSwitch: false,
  email: '', remember: true, signingIn: false, signInError: null, codeReq: null, code: '', codeError: null,
  helper: 'https://ani.iloader.app', helpers: [], teams: [], team: null, renew: 'sidestore',
  ownVpn: false, vpn: null, stages: [], progress: {}, installed: null, failure: null, phoneApps: null,
  prefs: { phones: {} }, record: null, busy: false, copied: false, customHelper: '',
};
let timer = null;
const stopPolling = () => { if (timer) { clearTimeout(timer); timer = null; } };
const poll = (f, ms) => { stopPolling(); const tick = async () => { try { if (await f() === false) return; } catch (e) { console.warn(e); } timer = setTimeout(tick, ms); }; tick(); };
const go = (screen, extra = {}) => {
  if (!screens[screen]) { console.error('Mode Setup: no screen', screen); return; }
  stopPolling(); Object.assign(S, extra, { screen }); render(); enter(screen);
};
const errText = e => String(e?.message ?? e);

// ---- the window --------------------------------------------------------------------------------------
function stepsNav(current, doneUpto = current, failed = null) {
  return `<ol class="steps" aria-label="Setup steps">${STEPS.map((s, i) => {
    const kind = failed === i ? 'failed' : i < doneUpto ? 'done' : i === current ? 'current' : 'todo';
    const ic = kind === 'done' ? icon('done') : kind === 'failed' ? icon('fail') : `<span class="num">${i + 1}</span>`;
    return `<li class="${kind}"${kind === 'current' || kind === 'failed' ? ' aria-current="step"' : ''}>${ic}${esc(s)}</li>`;
  }).join('')}</ol>`;
}
function deviceChip() {
  const p = S.phone;
  const [name, status, dot] = !p ? ['No iPhone yet', 'Connect one with a cable', '']
    : S.screen === 'unplugged' ? [p.name, 'Disconnected', 'orange']
    : S.screen === 'ready' ? [p.name, 'Unplugged', '']
    : S.readiness && S.readiness !== 'ready' ? [p.name, { needsTrust: 'Waiting for Trust', locked: 'Locked', denied: 'Didn’t trust', needsDeveloperMode: 'Restarting…' }[S.readiness] ?? 'Connected', 'orange']
    : [p.name, 'Connected by cable', 'green'];
  return `<div class="devchip">${svg('phone', 18)}<span style="flex:1;min-width:0"><b>${esc(name)}</b><small><i class="dot ${dot}"></i>${esc(status)}</small></span></div>`;
}
function side(nav, chip = true, settings = true) {
  return `<nav class="side"><div class="brand">${mark(32)}<span><b>Mode Setup</b><small>for iPhone</small></span></div>${nav}<div class="spacer"></div>${chip ? deviceChip() : ''}${settings ? `<button type="button" class="navlink" data-act="settings">${svg('gear', 16)}Settings</button>` : ''}</nav>`;
}
function page({ nav, body, left = '', right = '', tight = false, chip = true }) {
  const foot = left || right;
  return `<div class="drag"></div>${side(nav, chip)}<main class="${foot ? '' : 'nofoot'} ${tight ? 'tight' : ''}">${body}</main>${foot ? `<footer>${left}<span class="spacer"></span>${right}</footer>` : ''}`;
}

// ---- screens -------------------------------------------------------------------------------------------
const screens = {
  welcome: () => page({
    nav: stepsNav(-1, 0),
    body: head('Set up Mode on your iPhone', 'Mode is an assistant that uses your iPhone for you. This app installs it and gets it ready. It takes about 10 minutes, once.', mark(52))
      + `<div style="display:flex;flex-direction:column;gap:10px"><h2 class="label">You’ll need</h2>${box(
        row('', 'An iPhone and its cable', 'iOS 17.4 or later. Setup needs the cable once.', '', false, tile('phone', '', 32))
        + row('', 'An Apple account', 'A free one works. Mode is signed with it so it can run on your iPhone.', '', false, tile('key', '', 32))
        + row('', 'A passcode on your iPhone', 'Your iPhone asks for it to trust this computer.', '', false, tile('shield', '', 32)))}</div>`
      + `<p class="note">${svg('lock', 16, 2)}<span>Mode is free and open source. Your Apple password goes only to Apple.</span></p>`,
    left: link('How setup works', 'https://github.com/Piyusinha/mode/blob/main/docs/mac-windows-setup-plan.md'),
    right: btn('Get started', 'start', 'primary'),
  }),

  connect: () => page({
    nav: stepsNav(0),
    body: head('Connect your iPhone', 'Use a cable. Then unlock your iPhone.')
      + `<div class="box" style="flex-direction:row;align-items:center;gap:28px;padding:22px 26px">${svg('phone', 64, 1.4)}<span style="flex:1;border-top:2.5px dashed var(--ring)"></span><span style="color:var(--sec)">${svg('plug', 40, 1.6)}</span><span style="flex:1;border-top:2.5px dashed var(--ring)"></span><span style="width:70px;height:46px;border-radius:6px;border:2.5px solid var(--text)"></span></div>`
      + box(row('wait', 'Looking for your iPhone…', 'This page moves on by itself when it’s connected.'))
      + (S.failure ? callout(esc(S.failure), 'orange', 'plug') : '')
      + callout('<b>On Windows?</b> Install Apple’s iTunes from apple.com first — not the Microsoft Store version. It lets this app talk to your iPhone.', '', 'win', link('Get iTunes', 'https://www.apple.com/itunes/')),
    left: btn('Back', 'welcome'),
  }),

  pickPhone: () => page({
    nav: stepsNav(0),
    body: head('Which iPhone?', 'More than one is connected. Mode is set up on one at a time.')
      + box(S.phones.map((p, i) => radio(p.name, `${esc(model(p.model))} · iOS ${esc(p.ios)} · ${p.usb ? 'by cable' : 'Wi-Fi'}`, S.phone?.udid === p.udid, `pick:${i}`, '', tile('phone', i ? 'grey' : '', 34))).join('')),
    left: btn('Back', 'welcome'), right: btn('Continue', 'pickContinue', 'primary', S.phone ? '' : 'disabled'),
  }),

  trust: () => page({
    nav: stepsNav(0),
    body: head('Unlock your iPhone and tap Trust', 'Then enter your iPhone passcode. This lets this computer install Mode.')
      + `<div class="side-by-side"><div class="grow">${box(
        row('done', 'Connected', `${esc(S.phone?.name)} · ${esc(model(S.phone?.model))}`)
        + row(S.readiness === 'locked' ? 'wait' : 'done', 'Unlocked', S.readiness === 'locked' ? 'Unlock your iPhone…' : '')
        + row(S.readiness === 'denied' ? 'fail' : 'wait', 'Trusted', S.readiness === 'denied' ? 'Your iPhone said Don’t Trust.' : 'Waiting for you to tap Trust…'))}
        ${callout('Tapped Don’t Trust by mistake? Unplug the cable and plug it back in — your iPhone asks again.', S.readiness === 'denied' ? 'orange' : '', 'plug')}</div>${phoneTrust()}</div>`,
    left: btn('Back', 'welcome'),
  }),

  devMode: () => page({
    nav: stepsNav(0), tight: true,
    body: head('Turn on Developer Mode', 'Mode needs it to see and tap your screen for you. Your iPhone restarts once.')
      + `<div class="side-by-side"><div class="grow"><ol class="numbered">${[
        ['Open Settings → Privacy &amp; Security', 'We’ve added the Developer Mode switch there for you.'],
        ['Scroll down, tap Developer Mode, turn it on', ''], ['Tap Restart', 'Keep the cable in.'],
        ['After the restart, unlock and tap Turn On', 'Then enter your passcode.']].map(([t, s], i) =>
        `<li><span class="n">${i + 1}</span><span><b>${t}</b>${s ? `<span>${s}</span>` : ''}</span></li>`).join('')}</ol>
        ${box(row('wait', 'Waiting for your iPhone to restart…', 'We’ll carry on by ourselves.'))}</div>${phoneDevMode()}</div>`,
    left: btn('Back', 'welcome'),
  }),

  signIn: () => page({
    nav: stepsNav(1),
    body: head('Sign in with your Apple account', 'Mode is signed with your account so it can run on your iPhone. A free account is fine.')
      + `<form id="signin" style="display:flex;flex-direction:column;gap:14px;max-width:420px">
          <label class="field"><span>Apple Account</span><input id="email" type="email" autocomplete="username" value="${esc(S.email)}" required></label>
          <label class="field${S.signInError ? ' error' : ''}"><span>Password</span><input id="password" type="password" autocomplete="current-password" placeholder="${S.hasSaved ? 'Saved on this computer' : ''}">${S.signInError ? `<em>${esc(S.signInError)}</em>` : ''}</label>
          <button type="button" class="check ${S.remember ? 'on' : ''}" role="checkbox" aria-checked="${S.remember}" data-act="remember"><i>${S.remember ? svg('check', 12, 3.2) : ''}</i><span><b>Remember on this computer</b><small>Kept in this computer’s Keychain, so renewing Mode doesn’t ask again. Untick to sign in each time.</small></span></button>
        </form>`
      + `<p class="note">${svg('lock', 16, 2)}<span>Your password goes only to Apple. Mode and its makers never see it.</span></p>`,
    left: link('Create an Apple Account', 'https://account.apple.com/account'),
    right: btn('Back', 'welcome') + (S.signingIn ? `<span class="wait">${spinner(18)}Signing in…</span>` : btn('Sign in', 'signIn', 'primary')),
  }),

  code: () => {
    const r = S.codeReq || {};
    return page({
      nav: stepsNav(1),
      body: head('Enter the verification code', r.textedTo ? `Apple texted a 6-digit code to ${esc(r.textedTo)}.` : 'Apple sent a 6-digit code to your iPhone and your other devices.')
        + `<div class="codebox" role="group" aria-label="Verification code">${[0, 1, 2, 3, 4, 5].map(i => `<input inputmode="numeric" maxlength="1" data-digit="${i}" value="${esc(S.code[i] ?? '')}" aria-label="Digit ${i + 1}">`).join('')}</div>`
        + (S.codeError || r.lastFailed ? callout('That code didn’t work. Check it, or have it sent another way.', 'orange', 'key') : '')
        + `<div style="display:flex;gap:16px;flex-wrap:wrap">${(r.numbers || []).map(([id, n]) => `<button type="button" class="link" data-act="text:${id}">Text it to ${esc(n)}</button>`).join('')}<button type="button" class="link" data-act="resend">Didn’t get a code?</button>${r.textedTo ? '<button type="button" class="link" data-act="devices">Send it to my devices</button>' : ''}</div>`,
      left: btn('Cancel', 'cancelCode'), right: btn('Continue', 'submitCode', 'primary', S.code.length === 6 ? '' : 'disabled'),
    });
  },

  helperDown: () => page({
    nav: stepsNav(1, 1, 1),
    body: head('Couldn’t reach the sign-in helper', 'Signing in needs a small helper that tells Apple which computer you’re on. It never sees your password. The one we use isn’t answering — try another.')
      + helperList(true)
      + callout('These helpers are run by open-source projects, not by Mode or Apple. You can change this any time in Settings.', '', 'shield'),
    left: btn('Back', 'signInScreen'), right: btn('Try again', 'signInScreen', 'primary'),
  }),

  team: () => {
    const paid = S.teams.some(t => t.kind === 'paid');
    return page({
      nav: stepsNav(1),
      body: head('Which account should sign Mode?', paid ? 'You have a paid developer account, so we picked it: Mode lasts a year and you won’t need to renew it every week.' : 'Your Apple account belongs to more than one team.')
        + box(S.teams.map((t, i) => radio(t.name, t.kind === 'paid' ? 'Paid developer account · Mode lasts a year' : t.kind === 'free' ? 'Free · Mode lasts 7 days, then needs renewing' : 'Mode lasts as long as Apple allows this team',
          S.team?.id === t.id, `team:${i}`, t.kind === 'paid' && paid ? pill('Recommended', 'accent') : t.kind === 'free' ? pill('Free') : '')).join(''))
        + (paid && S.team?.kind === 'paid' ? callout('With the paid account there’s no weekly renewal and no SideStore. We remind you a few weeks before the year is up.', 'green', 'check') : '')
        + callout('Already used Mode on this iPhone? Pick the same team as before, so Mode keeps its chats, memory and sign-ins.', '', 'renew'),
      left: btn('Back', 'signInScreen'), right: btn('Continue', 'teamContinue', 'primary', S.team ? '' : 'disabled'),
    });
  },

  sidestore: () => {
    const choice = (key, title, sub, bullets, tag = '') =>
      `<button type="button" role="radio" aria-checked="${S.renew === key}" class="choice ${S.renew === key ? 'on' : ''}" data-act="renew:${key}"><span class="top"><b>${esc(title)}</b>${tag}${radioMark(S.renew === key)}</span><p>${esc(sub)}</p><ul>${bullets.map(([t, ok]) => `<li><em class="${ok ? 'ok' : ''}">${ok ? '✓' : '•'}</em>${esc(t)}</li>`).join('')}</ul></button>`;
    return page({
      nav: stepsNav(2), tight: true,
      body: head('Renew Mode without your computer?', 'A free Apple account signs Mode for 7 days. SideStore, a free open-source app, renews it on your iPhone every week, so you won’t need this computer again.', tile('renew', '', 44))
        + `<div class="choices" role="radiogroup" aria-label="How to renew Mode">${choice('sidestore', 'Install SideStore too', 'SideStore renews Mode on your iPhone every week.', [['No computer after today', true], ['Needs LocalDevVPN on and Wi-Fi to renew', false], ['Uses 2 of your 3 free app slots', false]], pill('Recommended', 'accent'))
          + choice('computer', 'I’ll use this computer', 'Connect your iPhone here once a week and press Renew.', [['Nothing else on your iPhone', true], ['This app shows when it runs out', false], ['If it runs out, Mode won’t open until you renew', false]])}</div>`
        + `<div style="display:flex;align-items:center;gap:14px;font-size:13px;color:var(--sec)">${link('About SideStore · sidestore.io', 'https://sidestore.io')}<span>Made by the SideStore team, not by Mode. You can change this later.</span></div>`,
      left: btn('Back', 'teamScreen'), right: btn('Continue', 'install', 'primary'),
    });
  },

  install: () => {
    const paid = S.team?.kind === 'paid';
    return page({
      nav: stepsNav(2, 2, S.failure ? 2 : null), tight: true,
      body: head(S.failure ? 'Installing stopped' : 'Installing Mode', S.failure ? esc(S.failure) : 'Keep your iPhone connected. This takes a minute or two.')
        + `<div style="display:flex;gap:8px">${pill('Mode', 'accent')}${pill(paid ? 'Paid account · 1 year' : 'Free account · 7 days')}${S.ownVpn ? pill('Mode’s own VPN', 'green') : S.renew === 'sidestore' && !paid ? pill('With SideStore', 'green') : ''}</div>`
        + box(S.stages.map(st => row(st.state, st.title, st.sub ?? '', st.state === 'wait' && st.fraction != null ? bar(st.fraction) : '', st.state === 'todo')).join(''), 'compact'),
      left: S.failure ? btn('Back', 'teamScreen') : `<span style="font-size:13px;color:var(--sec)">Keep the cable in until this finishes.</span>`,
      right: S.failure ? btn('Try again', 'install', 'primary') : '',
    });
  },

  unplugged: () => page({
    nav: stepsNav(2, 2, 2), tight: true,
    body: head('Your iPhone was disconnected', 'Plug it back in and unlock it. Setup carries on from where it stopped.')
      + callout('Nothing was lost. Mode isn’t half-installed — your iPhone only finishes installing once everything has arrived.', 'orange', 'plug')
      + box(S.stages.map(st => row(st.state === 'wait' ? 'paused' : st.state, st.title, st.state === 'wait' ? 'Paused · picks up where it stopped' : st.sub ?? '', '', st.state === 'todo')).join(''), 'compact'),
    left: btn('Cancel setup', 'welcome'), right: `<span class="wait">${spinner(18)}Waiting for your iPhone…</span>`,
  }),

  limit: () => page({
    nav: stepsNav(2, 2, 2), tight: true,
    body: head('Your free account has no app slots left', (S.phoneApps?.sideloaded?.length ? 'A free Apple account can have 3 apps from outside the App Store on an iPhone at once. These are using them:' : 'A free Apple account can have 3 apps from outside the App Store on an iPhone at once.'))
      + (S.phoneApps?.sideloaded?.length ? box(S.phoneApps.sideloaded.map(a => row('', a.name, 'Signed with a developer account', '', false, `<span class="letter" aria-hidden="true">${esc(a.name.slice(0, 1))}</span>`)).join('')) : '')
      + callout('Delete one of them on your iPhone, then press Try again. Mode never removes your apps for you.', '', 'phone')
      + (S.renew === 'sidestore' ? callout('Or install Mode without SideStore: it then needs one slot, and this computer renews it each week.', 'accent', 'renew') : ''),
    left: btn('Back', 'teamScreen'),
    right: (S.renew === 'sidestore' ? btn('Install without SideStore', 'noSidestore') : '') + btn('Try again', 'install', 'primary'),
  }),

  vpn: () => {
    const v = S.vpn || {};
    const on = v.appOn && v.tunnelOn;
    const idl = (label, id) => `<span class="idline selectable">${esc(id)}<button type="button" data-copy="${esc(id)}" aria-label="Copy ${esc(label)}">Copy</button></span>`;
    return page({
      nav: stepsNav(2, 2, on ? null : 2), tight: true,
      body: head('Turn on Mode’s own VPN', 'Apple didn’t let us switch it on for you. It takes a minute in your developer account — this page notices by itself.')
        + box(on ? row('done', 'Network Extensions is on for Mode', 'Found it in your developer account. Carrying on…', pill('On', 'green'))
                 : row('wait', 'Network Extensions for Mode', 'Checking your developer account every few seconds…', pill('Off', 'orange')))
        + `<ol class="numbered">${[
          ['Open your developer account', link('developer.apple.com/account', 'https://developer.apple.com/account/resources/identifiers/list')],
          ['Certificates, IDs &amp; Profiles → Identifiers → Mode', idl('Mode’s ID', v.appId ?? '')],
          ['Tick Network Extensions, then Save', ''],
          ['Do the same for Mode Tunnel', idl('Mode Tunnel’s ID', v.tunnelId ?? '')]].map(([t, s], i) =>
          `<li><span class="n">${i + 1}</span><span><b>${t}</b>${s ? `<span style="display:block">${s}</span>` : ''}</span></li>`).join('')}</ol>`,
      left: btn('Use LocalDevVPN instead', 'localDevVpn'),
      right: on ? btn('Continue', 'install', 'primary') : btnIcon('Check now', 'renew', 'vpnCheck'),
    });
  },

  onPhone: () => {
    const vpnApp = S.phoneApps?.localDevVpn;
    const needsVpnApp = !S.ownVpn;
    return page({
      nav: stepsNav(3), tight: true,
      body: head('Finish on your iPhone', 'One thing at a time. This page ticks them off by itself.')
        + `<div class="side-by-side"><div class="grow">` + box(row('done', S.installedSidestore ? 'Mode and SideStore installed' : 'Mode installed', S.ownVpn ? 'With Mode’s own VPN — no extra app needed' : '')
          + (needsVpnApp ? row(vpnApp ? 'done' : 'wait', 'Get LocalDevVPN from the App Store', vpnApp ? 'Installed. Open it and tap Connect.' : 'Free. Mode uses it to reach its tools on your iPhone without a computer. Scan the code with your iPhone.') : '')
          + row(needsVpnApp && !vpnApp ? 'todo' : 'wait', 'Open Mode and follow its setup', 'Mode checks it can see and tap your screen', '', needsVpnApp && !vpnApp)
          + row('todo', 'Unplug the cable', 'Mode runs its last check without this computer', '', true)) + '</div>'
        + (needsVpnApp && !vpnApp ? `<div style="display:flex;flex-direction:column;align-items:center;gap:8px"><span class="qr" role="img" aria-label="QR code for LocalDevVPN on the App Store">${S.qr ?? ''}</span><span style="font-size:12px;color:var(--sec)">LocalDevVPN · App Store</span></div>` : '') + '</div>',
      left: link('Why LocalDevVPN?', 'https://github.com/Piyusinha/mode/blob/main/docs/mac-windows-setup-plan.md'),
      right: btn('I’ve unplugged it', 'unplugged'),
    });
  },

  ready: () => {
    const r = S.record || {};
    const paid = r.paid;
    return page({
      nav: stepsNav(4, 5),
      body: head('Mode is ready', 'Mode works on its own now. Ask it something on your iPhone.', `<span class="donemark">${svg('check', 28, 3)}</span>`)
        + box(kv('Mode', esc(r.mode?.version ?? ''), pill('Installed', 'green'))
          + kv('Signed until', `${esc(fmtDate(r.mode?.expires))} · ${esc(r.teamName ?? '')}`)
          + kv('Renewing', paid ? 'Once a year, from this computer' : r.sidestore ? 'SideStore renews it every week on your iPhone' : 'Connect here once a week and press Renew',
            paid ? pill('No weekly renewal', 'green') : r.sidestore ? pill('No computer needed', 'green') : pill('Weekly', 'orange'))
          + kv('Phone control', r.ownVpn ? 'Mode’s own VPN · checked by Mode on your iPhone' : 'LocalDevVPN · checked by Mode on your iPhone', pill(r.ownVpn ? 'No extra app' : 'Working', 'green')))
        + (r.sidestore ? callout('Keep LocalDevVPN connected and your iPhone on Wi-Fi once a week, so SideStore can renew Mode.', '', 'wifi')
          : paid ? callout(`Renewing takes a minute with your iPhone connected. Due by ${esc(fmtDate(r.mode?.expires))}.`, '', 'renew') : ''),
      left: btn('See this iPhone', 'device'), right: btn('Done', 'device', 'primary'),
    });
  },

  device: () => {
    const r = S.record || {};
    const days = daysLeft(r.mode?.expires);
    const connected = !!S.phone && S.phones.some(p => p.udid === S.phone.udid);
    const soon = days != null && days <= (r.paid ? 30 : 2);
    const tc = (ic, kind, title, value, sub, action) => `<div class="tilecard"><div class="h">${tile(ic, kind, 30)}${esc(title)}</div><span class="v">${value}</span><span class="s">${sub}</span><span style="display:flex;gap:8px">${action}</span></div>`;
    return `<div class="drag"></div><nav class="side"><div class="brand">${mark(32)}<span><b>Mode Setup</b><small>for iPhone</small></span></div>
      <div style="display:flex;flex-direction:column;gap:4px"><span class="group-label">IPHONES</span>
      <button type="button" class="devnav" aria-current="page">${svg('phone', 18)}<span><b>${esc(r.phone ?? S.phone?.name ?? 'iPhone')}</b><small><i class="dot ${connected ? 'green' : ''}"></i>${connected ? 'Connected by cable' : 'Not connected'}</small></span></button>
      <button type="button" class="navlink tint" data-act="welcome"><span style="font-size:18px;width:18px;text-align:center">+</span>Set up another iPhone</button></div>
      <div class="spacer"></div><button type="button" class="navlink" data-act="settings">${svg('gear', 16)}Settings</button></nav>
      <main class="nofoot tight"><header style="display:flex;align-items:center;gap:14px">${tile('phone', 'grey', 52)}<span><h1 style="font-size:26px">${esc(r.phone ?? 'iPhone')}</h1><span style="font-size:14px;color:var(--sec)">${connected ? `${esc(model(S.phone?.model))} · iOS ${esc(S.phone?.ios)} · connected by cable` : 'Not connected · what’s below is from the last time'}</span></span></header>
      ${soon ? callout(`<b>Mode stops opening ${days <= 0 ? 'now' : `in ${days} day${days === 1 ? '' : 's'}`}</b> (${esc(fmtDate(r.mode?.expires))}). Renew it now — it takes a minute and keeps everything on your iPhone.`, 'orange', 'renew', connected ? btn('Renew now', 'renewNow', 'primary') : '') : ''}
      ${!connected ? callout('Connect your iPhone with a cable to renew, update or reconnect.', '', 'plug') : ''}
      <div class="tiles">
        ${tc('down', '', 'Mode', esc(r.mode?.version ?? '—'), 'Your chats, memory and sign-ins stay when you update.', btn('Update', 'renewNow', 'small primary', connected ? '' : 'disabled'))}
        ${tc('renew', soon ? 'orange' : 'green', 'Signed until', esc(fmtDate(r.mode?.expires)), r.paid ? 'Paid account · renews once a year.' : r.sidestore ? 'SideStore renews it every week on your iPhone.' : 'Renewed by this computer. Free accounts last 7 days.',
          r.paid || r.sidestore ? '' : btn('Install SideStore', 'addSidestore', 'small', connected ? '' : 'disabled'))}
        ${tc('shield', 'green', 'Phone control', r.ownVpn ? 'Mode’s own VPN' : 'LocalDevVPN', 'Make a new one if Mode can’t reach its tools.', btn('Reconnect', 'reconnect', 'small', connected ? '' : 'disabled'))}
      </div>
      ${S.failure ? callout(esc(S.failure), 'orange', 'plug') : ''}
      ${box(kv('Apple account', `${esc(S.prefs.email ?? '')} · ${esc(r.teamName ?? '')}`, btnIcon('Sign out', 'out', 'signOut', 'small plain'))
        + kv('Help', 'Copy a report to share when asking for help', btnIcon(S.copied ? 'Copied' : 'Copy report', 'copy', 'copyReport', 'small')))}
      </main>`;
  },

  settings: () => `<div class="drag"></div><nav class="side"><div class="brand">${mark(32)}<span><b>Mode Setup</b><small>for iPhone</small></span></div>
      <div style="display:flex;flex-direction:column;gap:2px"><button type="button" class="navlink current">${svg('gear', 16)}Settings</button>
      <button type="button" class="navlink" data-act="back">${svg('phone', 16)}${S.record ? esc(S.record.phone) : 'Back to setup'}</button></div></nav>
      <main class="nofoot" style="padding-top:60px;gap:18px"><h1 style="font-size:26px">Settings</h1>
      <section class="group"><h2 class="label">Sign-in helper</h2>${helperList(false)}<p>Tells Apple which computer you’re signing in from. It never sees your password. Run by open-source projects, not by Mode.</p></section>
      <section class="group"><h2 class="label">Apple account</h2>${box(kv('Signed in as', esc(S.prefs.email ?? 'Not signed in'), S.prefs.email ? btnIcon('Sign out', 'out', 'signOut', 'small plain') : '')
        + kv('Password', S.hasSaved ? 'Kept in this computer’s Keychain' : 'Asked each time', ''))}</section>
      <section class="group"><h2 class="label">About</h2>${box(kv('Mode Setup', '0.1 · free and open source', link('Source', 'https://github.com/Piyusinha/mode')) + kv('Help', 'Copy a report to share when asking for help', btnIcon(S.copied ? 'Copied' : 'Copy report', 'copy', 'copyReport', 'small')))}
      <p>Reports never include passwords, codes, keys or phone-control files.</p></section></main>`,
};

function helperList(compact) {
  const r = (...a) => compact ? radio(...a) : radio(...a).replace('class="radio"', 'class="radio small"');
  const known = S.helpers.length ? S.helpers : [{ name: 'iLoader', url: 'https://ani.iloader.app' }, { name: 'SideStore', url: 'https://ani.sidestore.io' }];
  const isCustom = !known.some(h => h.url === S.helper);
  return box(known.map((h, i) => r(h.name, esc(h.url.replace('https://', '')) + (h.ms != null ? ' · answering' : ''), S.helper === h.url, `helper:${i}`,
    h.ms != null ? pill('Answering', 'green') : h.error ? pill('Not answering', 'red') : '')).join('')
    + r('Your own', isCustom ? esc(S.helper) : 'Paste the address of a helper you run', isCustom, 'helperCustom')
    + (S.editingHelper ? `<div class="custom"><input id="customHelper" placeholder="https://…" value="${esc(S.customHelper)}"><button type="button" class="btn small primary" data-act="saveHelper">Use</button></div>` : ''));
}

function model(id) {
  const m = { 'iPhone18,1': 'iPhone 17 Pro', 'iPhone18,2': 'iPhone 17 Pro Max', 'iPhone18,3': 'iPhone 17', 'iPhone18,4': 'iPhone Air', 'iPhone17,1': 'iPhone 16 Pro', 'iPhone17,2': 'iPhone 16 Pro Max', 'iPhone17,3': 'iPhone 16', 'iPhone17,4': 'iPhone 16 Plus', 'iPhone17,5': 'iPhone 16e' };
  return m[id] ?? (id || 'iPhone');
}

function phoneTrust() {
  return `<div class="phone" aria-hidden="true"><span class="island"></span><div style="position:absolute;inset:0;background:rgba(0,0,0,0.28)"></div>
    <div style="position:absolute;left:14px;right:14px;top:110px;border-radius:14px;background:var(--card);text-align:center;overflow:hidden">
    <div style="padding:12px 10px 10px;display:flex;flex-direction:column;gap:4px"><b style="font-size:12px">Trust This Computer?</b><span style="font-size:9.5px;line-height:12px;color:var(--sec)">Your settings and data will be accessible from this computer when connected.</span></div>
    <div style="border-top:1px solid var(--sep);height:30px;display:flex;align-items:center;justify-content:center;font-size:12px;font-weight:700;color:#0A84FF">Trust</div>
    <div style="border-top:1px solid var(--sep);height:30px;display:flex;align-items:center;justify-content:center;font-size:12px;color:#0A84FF">Don’t Trust</div></div></div>`;
}
function phoneDevMode() {
  return `<div class="phone" aria-hidden="true"><span class="island"></span><div style="position:absolute;left:12px;right:12px;top:34px;display:flex;flex-direction:column;gap:8px">
    <span style="font-size:10px;color:#0A84FF">‹ Privacy &amp; Security</span><b style="font-size:17px">Developer Mode</b>
    <div style="border-radius:10px;background:var(--card);padding:8px 10px;display:flex;align-items:center;justify-content:space-between;font-size:11px">Developer Mode<span style="position:relative;width:30px;height:18px;border-radius:9px;background:var(--green)"><span style="position:absolute;right:1.5px;top:1.5px;width:15px;height:15px;border-radius:8px;background:#fff"></span></span></div>
    <span style="font-size:9px;line-height:12px;color:var(--sec)">Turning on Developer Mode needs a restart.</span>
    <div style="margin-top:70px;border-radius:12px;background:var(--card);text-align:center;overflow:hidden"><div style="padding:9px;font-size:10.5px;font-weight:700">Turn On Developer Mode?</div>
    <div style="border-top:1px solid var(--sep);height:26px;display:flex;align-items:center;justify-content:center;font-size:11px;font-weight:700;color:#0A84FF">Turn On</div></div></div></div>`;
}

// ---- what each screen does when it opens --------------------------------------------------------------
async function enter(screen) {
  if (MOCK) return;
  if (screen === 'welcome') {
    // Show the iPhone in the sidebar as soon as it is plugged in; Get started then goes straight on.
    poll(async () => {
      const phones = (await call('phones')).filter(p => p.usb);
      const phone = phones.length === 1 ? phones[0] : null;
      if (phone?.udid !== S.phone?.udid) { S.phones = phones; S.phone = phone; render(); }
    }, 2000);
  }
  if (screen === 'connect') {
    poll(async () => {
      const phones = (await call('phones')).filter(p => p.usb);
      S.phones = phones;
      if (phones.length === 1) { S.phone = phones[0]; go('trust'); return false; }
      if (phones.length > 1) { S.phone = null; go('pickPhone'); return false; }
    }, 1500);
  }
  if (screen === 'trust' || screen === 'devMode') {
    poll(async () => {
      const present = (await call('phones')).some(p => p.udid === S.phone.udid);
      if (!present) { if (S.readiness !== 'needsDeveloperMode') { S.readiness = null; render(); } return; }
      let r;
      try { r = await call('trust', { udid: S.phone.udid }); } catch (e) { return; }
      if (r !== S.readiness) { S.readiness = r; }
      if (r === 'ready') { go('signIn'); return false; }
      if (r === 'needsDeveloperMode') {
        if (!S.shownDevSwitch) { S.shownDevSwitch = true; call('show_developer_mode', { udid: S.phone.udid }).catch(() => {}); }
        if (S.screen !== 'devMode') { S.screen = 'devMode'; }
      } else if (S.screen !== 'trust') { S.screen = 'trust'; }
      render();
    }, 2000);
  }
  if (screen === 'signIn') {
    S.hasSaved = S.email ? await call('has_remembered_password', { email: S.email }).catch(() => false) : false;
    render();
    document.getElementById(S.email ? 'password' : 'email')?.focus();
  }
  if (screen === 'code') document.querySelector('[data-digit="0"]')?.focus();
  if (screen === 'helperDown' || screen === 'settings') {
    call('helpers').then(h => { S.helpers = h; render(); }).catch(() => {});
  }
  if (screen === 'vpn') {
    poll(async () => {
      S.vpn = await call('mode_vpn_status', { teamId: S.team.id });
      render();
      if (S.vpn.appOn && S.vpn.tunnelOn) { S.ownVpn = true; setTimeout(runInstall, 1200); return false; }
    }, 4000);
  }
  if (screen === 'limit') {
    call('apps_on_phone', { udid: S.phone.udid }).then(a => { S.phoneApps = a; render(); }).catch(() => {});
  }
  if (screen === 'unplugged') {
    poll(async () => {
      if ((await call('phones')).some(p => p.udid === S.phone.udid)) { runInstall(); return false; }
    }, 1500);
  }
  if (screen === 'onPhone') {
    if (!S.qr) call('local_dev_vpn_qr').then(q => { S.qr = q; render(); }).catch(() => {});
    poll(async () => {
      const present = (await call('phones')).some(p => p.usb && p.udid === S.phone.udid);
      if (!present) { await showReady(); return false; }
      S.phoneApps = await call('apps_on_phone', { udid: S.phone.udid }).catch(() => S.phoneApps);
      render();
    }, 2500);
  }
  if (screen === 'device') {
    poll(async () => { S.phones = (await call('phones')).filter(p => p.usb); if (!S.phone && S.phones.length) S.phone = S.phones.find(p => S.prefs.phones[p.udid]) ?? null; render(); }, 3000);
  }
}

async function showReady() {
  S.prefs = await call('prefs');
  S.record = S.prefs.phones?.[S.phone.udid] ?? S.record;
  go('ready');
}

// ---- signing in --------------------------------------------------------------------------------------
async function signIn() {
  S.email = document.getElementById('email').value.trim();
  const password = document.getElementById('password').value;
  if (!S.email) return;
  S.signingIn = true; S.signInError = null; render();
  try {
    S.teams = await call('sign_in', { email: S.email, password: password || null, remember: S.remember, helper: S.helper });
    S.signingIn = false;
    S.prefs = await call('prefs');
    const before = S.prefs.phones?.[S.phone?.udid];
    S.team = S.teams.find(t => t.id === before?.teamId) ?? S.teams[0] ?? null;
    if (!S.team) { S.signInError = 'This Apple account has no team that can sign apps.'; go('signIn'); return; }
    if (S.after === 'renew') { S.after = null; S.ownVpn = before?.ownVpn ?? false; S.vpnDecided = S.team.kind === 'paid' && !S.ownVpn; S.renew = S.renewAddSidestore || before?.sidestore ? 'sidestore' : 'computer'; S.renewAddSidestore = false; runInstall(); return; }
    if (S.teams.length > 1) go('team'); else afterTeam();
  } catch (e) {
    S.signingIn = false;
    const m = errText(e);
    if (/sign-in helper/i.test(m)) { go('helperDown'); return; }
    S.signInError = /wrong apple account or password/i.test(m) ? 'Wrong Apple Account or password.' : m;
    go('signIn');
  }
}
listen('code-needed', req => { S.codeReq = req; S.code = ''; S.codeError = !!req.lastFailed; go('code'); });
const answer = a => call('submit_code', { answer: a }).then(() => { if (S.screen === 'code') go('signIn', { signingIn: true }); });

function afterTeam() {
  if (S.team.kind === 'paid') { S.renew = 'computer'; runInstall(); } else go('sidestore');
}

// ---- installing ----------------------------------------------------------------------------------------
function stagesFor() {
  const paid = S.team.kind === 'paid';
  const st = [];
  if (paid) st.push({ id: 'vpn', title: 'Turning on Mode’s own VPN', state: 'todo' });
  st.push({ id: 'signing', title: `Signing with ${S.team.name}`, state: 'todo' });
  st.push({ id: 'installing', title: 'Installing Mode on your iPhone', sub: 'Keep it unlocked', state: 'todo' });
  if (!paid && S.renew === 'sidestore') st.push({ id: 'sidestore', title: 'Installing SideStore', state: 'todo' });
  st.push({ id: 'control', title: 'Connecting phone control', sub: 'So Mode can see and tap your screen', state: 'todo' });
  return st;
}
const stage = (id, patch) => { const s = S.stages.find(x => x.id === id); if (s) Object.assign(s, patch); if (S.screen === 'install') render(); };
listen('progress', p => {
  if (p.what === 'sidestore') { stage('sidestore', { state: 'wait', fraction: p.fraction, sub: p.stage === 'download' ? 'Downloading' : p.stage === 'signing' ? 'Signing' : 'Installing' }); return; }
  if (p.stage === 'signing') stage('signing', { state: 'wait', fraction: p.fraction });
  if (p.stage === 'installing') { stage('signing', { state: 'done', fraction: null }); stage('installing', { state: 'wait', fraction: p.fraction }); }
});

async function runInstall() {
  const paid = S.team.kind === 'paid';
  if (!S.stages.length || S.screen !== 'unplugged') S.stages = stagesFor();
  S.failure = null;
  go('install');
  const udid = S.phone.udid, phoneName = S.phone.name, teamId = S.team.id;
  try {
    if (paid && !S.vpnDecided) {
      stage('vpn', { state: 'wait' });
      const v = await call('mode_vpn_turn_on', { teamId });
      S.vpn = v;
      if (!(v.appOn && v.tunnelOn)) { go('vpn'); S.vpnDecided = true; return; }
      S.ownVpn = true; S.vpnDecided = true;
    }
    if (paid) stage('vpn', S.ownVpn ? { state: 'done', title: 'Turned on Mode’s own VPN', sub: 'Network Extensions is on for Mode in your developer account' } : { state: 'done', title: 'Using LocalDevVPN', sub: 'Mode’s own VPN stays off' });
    stage('signing', { state: 'wait', fraction: 0 });
    const installed = await call('install_mode', { udid, phoneName, teamId, ownVpn: S.ownVpn });
    stage('signing', { state: 'done', fraction: null, title: `Signed with ${S.team.name}`, sub: `Lasts until ${fmtDate(installed.expires)}` });
    stage('installing', { state: 'done', fraction: null, title: 'Installed Mode on your iPhone', sub: '' });
    if (!paid && S.renew === 'sidestore') {
      stage('sidestore', { state: 'wait', fraction: 0 });
      await call('install_sidestore', { udid, phoneName, teamId });
      S.installedSidestore = true;
      stage('sidestore', { state: 'done', fraction: null, title: 'Installed SideStore', sub: 'It renews Mode every week' });
    }
    stage('control', { state: 'wait' });
    await call('phone_control', { udid });
    stage('control', { state: 'done', title: 'Connected phone control' });
    S.prefs = await call('prefs');
    S.record = S.prefs.phones?.[udid];
    S.vpnDecided = false;
    setTimeout(() => go('onPhone'), 700);
  } catch (e) {
    const m = errText(e);
    S.stages.forEach(s => { if (s.state === 'wait') s.state = 'fail'; });
    if (/disconnected/i.test(m)) { S.stages.forEach(s => { if (s.state === 'fail') s.state = 'wait'; }); go('unplugged'); return; }
    if (/no app slots/i.test(m)) { go('limit'); return; }
    S.failure = m; render();
  }
}

// ---- events from the window ------------------------------------------------------------------------
document.addEventListener('click', async e => {
  const urlEl = e.target.closest('[data-url]');
  if (urlEl) { call('open_url', { url: urlEl.dataset.url }); return; }
  const copyEl = e.target.closest('[data-copy]');
  if (copyEl) { navigator.clipboard?.writeText(copyEl.dataset.copy).catch(() => {}); copyEl.textContent = 'Copied'; return; }
  const el = e.target.closest('[data-act]');
  if (!el || el.disabled) return;
  const [act, arg] = el.dataset.act.split(':');
  switch (act) {
    case 'start': case 'welcomeConnect': S.failure = null; S.after = null; go('connect'); break;
    case 'welcome': go('welcome', { readiness: null, shownDevSwitch: false, record: null }); break;
    case 'pick': S.phone = S.phones[+arg]; render(); break;
    case 'pickContinue': go('trust'); break;
    case 'remember': S.remember = !S.remember; render(); break;
    case 'signIn': signIn(); break;
    case 'signInScreen': go('signIn'); break;
    case 'teamScreen': S.teams.length > 1 ? go('team') : go('signIn'); break;
    case 'text': answer({ kind: 'text', value: +arg }); break;
    case 'resend': answer({ kind: 'resend' }); break;
    case 'devices': answer({ kind: 'sendToDevices' }); break;
    case 'cancelCode': answer({ kind: 'cancel' }); break;
    case 'submitCode': answer({ kind: 'code', value: S.code }); break;
    case 'team': S.team = S.teams[+arg]; render(); break;
    case 'teamContinue': afterTeam(); break;
    case 'renew': S.renew = arg; render(); break;
    case 'install': runInstall(); break;
    case 'noSidestore': S.renew = 'computer'; runInstall(); break;
    case 'localDevVpn': S.ownVpn = false; S.vpnDecided = true; runInstall(); break;
    case 'vpnCheck': go('vpn'); break;
    case 'unplugged': showReady(); break;
    case 'device': S.prefs = await call('prefs'); go('device'); break;
    case 'settings': S.returnTo = S.screen; go('settings'); break;
    case 'back': go(S.returnTo && S.returnTo !== 'settings' ? S.returnTo : S.record ? 'device' : 'welcome'); break;
    case 'helper': { const h = (S.helpers.length ? S.helpers : [{ url: 'https://ani.iloader.app' }, { url: 'https://ani.sidestore.io' }])[+arg]; S.helper = h.url; S.editingHelper = false; call('set_helper', { url: h.url }); render(); break; }
    case 'helperCustom': S.editingHelper = true; render(); document.getElementById('customHelper')?.focus(); break;
    case 'saveHelper': { const v = document.getElementById('customHelper').value.trim(); if (/^https:\/\//.test(v)) { S.helper = v; S.editingHelper = false; call('set_helper', { url: v }); render(); } break; }
    case 'renewNow': S.after = 'renew'; S.team = null; go('signIn'); break;
    case 'addSidestore': S.after = 'renew'; S.renewAddSidestore = true; go('signIn'); break;
    case 'reconnect':
      try { await call('phone_control', { udid: S.phone.udid }); S.failure = null; S.copied = false; render(); }
      catch (err) { S.failure = errText(err); render(); }
      break;
    case 'signOut': await call('sign_out', { email: S.prefs.email ?? S.email }); S.prefs.email = null; S.hasSaved = false; render(); break;
    case 'copyReport': { const r = await call('diagnostics'); navigator.clipboard?.writeText(r).catch(() => {}); S.copied = true; render(); setTimeout(() => { S.copied = false; render(); }, 2000); break; }
  }
});
document.addEventListener('submit', e => { e.preventDefault(); if (S.screen === 'signIn') signIn(); });
document.addEventListener('keydown', e => { if (e.key === 'Enter' && S.screen === 'signIn' && !S.signingIn) { e.preventDefault(); signIn(); } });
document.addEventListener('input', e => {
  const d = e.target.dataset?.digit;
  if (d == null) return;
  const v = e.target.value.replace(/\D/g, '');
  if (v.length > 1) { S.code = v.slice(0, 6); } else { const a = S.code.padEnd(6, ' ').split(''); a[+d] = v || ' '; S.code = a.join('').trimEnd(); }
  const next = Math.min(S.code.replace(/ /g, '').length, 5);
  render();
  document.querySelector(`[data-digit="${v ? next : d}"]`)?.focus();
  if (S.code.length === 6 && !S.code.includes(' ')) answer({ kind: 'code', value: S.code });
});

// ---- start ------------------------------------------------------------------------------------------
function render() {
  const keep = document.activeElement?.id;
  const typed = { email: document.getElementById('email')?.value, password: document.getElementById('password')?.value };
  document.getElementById('app').innerHTML = screens[S.screen]();
  if (typed.email != null && document.getElementById('email')) document.getElementById('email').value = typed.email;
  if (typed.password && document.getElementById('password')) document.getElementById('password').value = typed.password;
  if (keep) document.getElementById(keep)?.focus();
}

async function start() {
  if (MOCK) { Object.assign(S, (window.MOCKS || {})[MOCK] || {}); render(); return; }
  S.prefs = await call('prefs').catch(() => ({ phones: {} }));
  S.email = S.prefs.email ?? '';
  S.helper = S.prefs.helper ?? S.helper;
  const debugStart = await call('debug_start').catch(() => null);
  if (debugStart) { go(debugStart); return; }
  const known = Object.keys(S.prefs.phones || {});
  if (known.length) {
    const phones = (await call('phones').catch(() => [])).filter(p => p.usb);
    S.phones = phones;
    const udid = phones.find(p => known.includes(p.udid))?.udid ?? known[0];
    S.phone = phones.find(p => p.udid === udid) ?? { udid, name: S.prefs.phones[udid].phone, model: '', ios: '' };
    S.record = S.prefs.phones[udid];
    go('device');
  } else go('welcome');
}
start();
