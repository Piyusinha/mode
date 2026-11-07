const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

// Exercise the real screen and event handlers without a phone or Apple requests.
function fixture(responses = {}) {
  const handlers = {};
  const app = { innerHTML: '' };
  const calls = [];
  const fields = { email: { value: 'test@example.com', focus() {} }, password: { value: '', focus() {} } };
  const phone = { udid: 'test-phone', name: 'Test iPhone', usb: true };
  let phones = [phone];
  let failure = false;
  const context = vm.createContext({
    URLSearchParams, location: { search: '' }, console,
    document: {
      activeElement: null,
      getElementById: id => id === 'app' ? app : fields[id] ?? null,
      addEventListener: (name, handler) => { handlers[name] = handler; },
    },
    window: { __TAURI__: {
      event: { listen() {} },
      core: { invoke: async (cmd, args) => {
        calls.push(cmd);
        if (cmd in responses) return typeof responses[cmd] === 'function' ? responses[cmd](args) : responses[cmd];
        if (cmd === 'cancel_sign_in') return;
        if (cmd === 'active_account') return null;
        if (cmd === 'phones') {
          if (failure) throw new Error('Device service unavailable');
          return phones;
        }
        if (cmd === 'prefs') return { phones: { 'test-phone': { ownVpn: true, paid: true, mode: {} } } };
        if (cmd === 'apps_on_phone') return {};
        throw new Error(`Unexpected command: ${cmd}`);
      } },
    } },
    setTimeout: () => 1, clearTimeout() {},
  });
  const source = fs.readFileSync(path.join(__dirname, '../ui/app.js'), 'utf8');
  vm.runInContext(source.replace(/\nstart\(\);\s*$/, '\n'), context);
  const run = code => vm.runInContext(code, context);
  run(`Object.assign(S, { screen: 'onPhone', phone: ${JSON.stringify(phone)}, ownVpn: true }); render();`);
  return {
    run, app, calls,
    disconnect() { phones = []; },
    wifiOnly() { phones = [{ ...phone, usb: false }]; },
    fail() { failure = true; },
    async poll() { await run("enter('onPhone')"); await new Promise(setImmediate); },
    async click(action) {
      await handlers.click({ target: { closest: selector => selector === '[data-act]' ? { dataset: { act: action }, disabled: false } : null } });
    },
  };
}

test('unplugging does not confirm the test or advance to Ready', async () => {
  const f = fixture();
  f.disconnect();
  await f.poll();
  assert.equal(f.run('S.screen'), 'onPhone');
  assert.equal(f.run('S.phoneTestConfirmed'), false);
  assert.match(f.app.innerHTML, /Setup complete — test passed/);
  await f.click('finishPhoneSetup');
  assert.equal(f.run('S.screen'), 'onPhone');
});

test('test confirmation still requires cable removal and a final confirmation', async () => {
  const f = fixture();
  await f.poll();
  await f.click('confirmPhoneTest');
  assert.match(f.app.innerHTML, /data-act="finishPhoneSetup" disabled/);
  await f.click('finishPhoneSetup');
  assert.equal(f.run('S.screen'), 'onPhone');
  f.wifiOnly(); // Wi-Fi visibility must not count as a connected USB cable.
  await f.poll();
  assert.equal(f.run('S.screen'), 'onPhone');
  assert.doesNotMatch(f.app.innerHTML, /data-act="finishPhoneSetup" disabled/);
  await f.click('finishPhoneSetup');
  assert.equal(f.run('S.screen'), 'ready');
  assert.match(f.app.innerHTML, /test confirmed by you/);
});

test('cable state is rechecked when finishing, even if the last poll said unplugged', async () => {
  const f = fixture();
  await f.click('confirmPhoneTest');
  f.run('S.cableDisconnected = true; S.phonePresenceKnown = true;');
  await f.click('finishPhoneSetup');
  assert.equal(f.run('S.screen'), 'onPhone');
  assert.equal(f.run('S.cableDisconnected'), false);
});

test('a device-service error cannot mark setup Ready', async () => {
  const f = fixture();
  await f.click('confirmPhoneTest');
  f.fail();
  await f.click('finishPhoneSetup');
  assert.equal(f.run('S.screen'), 'onPhone');
  assert.equal(f.run('S.phonePresenceKnown'), false);
  assert.match(f.app.innerHTML, /Could not check the cable connection/);
});

test('paid setup has no LocalDevVPN instruction; free setup retains it', () => {
  const f = fixture();
  assert.doesNotMatch(f.app.innerHTML, /LocalDevVPN/);
  assert.match(f.app.innerHTML, /This computer cannot automatically detect the test result/);
  f.run('S.ownVpn = false; render();');
  assert.match(f.app.innerHTML, /Get LocalDevVPN from the App Store/);
});

function device(f) {
  f.run(`S.record = { paid: true, ownVpn: true, mode: { version: '0.1' }, teamId: 'team' };
    S.phones = [S.phone]; S.prefs = { email: 'test@example.com', phones: { 'test-phone': S.record } };
    S.screen = 'device'; render();`);
}

test('checking for updates never signs in and same/older releases do not enable Update', async () => {
  for (const version of ['0.1', '0.1.0', '0.0.9']) {
    const f = fixture({ check_update: { version } }); device(f);
    assert.doesNotMatch(f.app.innerHTML, /data-act="updateNow"/);
    await f.click('checkUpdates');
    assert.equal(f.run('S.screen'), 'device');
    assert.match(f.app.innerHTML, /No newer release/);
    assert.doesNotMatch(f.app.innerHTML, /data-act="updateNow"/);
    assert.deepEqual(f.calls, ['check_update']);
  }
});

test('a newer release enables Update; failed/unknown checks never do', async () => {
  const f = fixture({ check_update: { version: '0.2' } }); device(f);
  await f.click('checkUpdates');
  assert.match(f.app.innerHTML, /Update to 0.2/);
  for (const response of [{ version: 'unrecognised' }, () => { throw new Error('offline'); }]) {
    const f = fixture({ check_update: response }); device(f);
    await f.click('checkUpdates');
    assert.doesNotMatch(f.app.innerHTML, /data-act="updateNow"/);
    assert.match(f.app.innerHTML, /No update was started/);
  }
});

test('Back from renewal sign-in returns to the device without reading saved credentials', async () => {
  const f = fixture(); device(f);
  await f.click('renewNow');
  assert.equal(f.run('S.screen'), 'signIn');
  assert.match(f.app.innerHTML, /data-act="cancelSignIn"/);
  await f.click('cancelSignIn');
  assert.equal(f.run('S.screen'), 'device');
  assert.equal(f.run('S.after'), null);
  assert.deepEqual(f.calls.filter(c => !['phones', 'apps_on_phone'].includes(c)), ['active_account', 'cancel_sign_in']);
});

test('an active account session is reused for renewing signing', async () => {
  const f = fixture({ active_account: { email: 'test@example.com', teams: [{ id: 'team', kind: 'paid' }] } }); device(f);
  f.run("runInstall = async () => { S.screen = 'install'; };");
  await f.click('renewNow');
  assert.equal(f.run('S.screen'), 'install');
  assert.deepEqual(f.calls, ['active_account']);
});

test('duplicate sign-in is suppressed and cancelled results cannot navigate away', async () => {
  let resolve;
  const pending = new Promise(r => { resolve = r; });
  const f = fixture({ sign_in: () => pending }); device(f);
  f.run("S.screen = 'signIn';");
  const first = f.run('signIn()');
  await f.run('signIn()');
  assert.equal(f.calls.filter(c => c === 'sign_in').length, 1);
  await f.click('cancelSignIn');
  resolve([{ id: 'team', kind: 'paid' }]);
  await first;
  assert.equal(f.run('S.screen'), 'device');
  assert.equal(f.run('S.signingIn'), false);
});


test('Reconnect shows progress, prevents duplicate repairs, and reports success', async () => {
  let finish;
  const pending = new Promise(r => { finish = r; });
  const f = fixture({ phone_control: () => pending }); device(f);
  const first = f.click('reconnect');
  assert.match(f.app.innerHTML, /Reconnecting…/);
  assert.match(f.app.innerHTML, /data-act="reconnect" disabled/);
  await f.click('reconnect');
  assert.equal(f.calls.filter(c => c === 'phone_control').length, 1);
  finish(['app.heymode.mode', 'app.heymode.mode.TEAM']);
  await first;
  assert.equal(f.run('S.reconnecting'), false);
  assert.match(f.app.innerHTML, /Pairing repaired in all copies of Mode/);
  assert.match(f.app.innerHTML, /run a short task to check/);
});

test('Reconnect exposes failures, permits retry, and clears stale success', async () => {
  let fail = true;
  const f = fixture({ phone_control: () => { if (fail) throw new Error('Unlock your iPhone'); return ['mode']; } }); device(f);
  f.run("S.reconnectResult = 'old success';");
  await f.click('reconnect');
  assert.equal(f.run('S.reconnectResult'), null);
  assert.equal(f.run('S.reconnecting'), false);
  assert.match(f.app.innerHTML, /Phone control was not fully repaired.*Unlock your iPhone/);
  fail = false;
  await f.click('reconnect');
  assert.equal(f.run('S.failure'), null);
  assert.match(f.app.innerHTML, /Pairing repaired in Mode/);
});

test('Reconnect refuses a disconnected phone without contacting pairing services', async () => {
  const f = fixture(); device(f);
  f.run('S.phones = [];');
  await f.click('reconnect');
  assert.match(f.app.innerHTML, /Connect and unlock your iPhone/);
  assert.equal(f.calls.includes('phone_control'), false);
});

test('the dashboard notices Mode was removed from the iPhone and offers to install it again', async () => {
  const f = fixture({ apps_on_phone: { modes: [{ bundleId: 'app.heymode.mode', name: 'Mode', version: '0.1' }], sideloaded: [] } });
  device(f);
  f.run("S.record.mode.bundleId = 'app.heymode.mode.TEAM'; render();");
  assert.doesNotMatch(f.app.innerHTML, /Not installed/);
  await f.run("enter('device')");
  for (let i = 0; i < 5; i++) await new Promise(setImmediate);
  assert.match(f.app.innerHTML, /Mode isn’t on this iPhone any more/);
  assert.match(f.app.innerHTML, /Not installed/);
  assert.match(f.app.innerHTML, /data-act="reinstall"/);
  assert.match(f.app.innerHTML, /copy built in Xcode/);
  assert.doesNotMatch(f.app.innerHTML, /data-act="renewNow"/);
});

test('the dashboard stays as it was while this computer’s copy is still installed', async () => {
  const f = fixture({ apps_on_phone: { modes: [{ bundleId: 'app.heymode.mode.TEAM', name: 'Mode', version: '0.1' }], sideloaded: [] } });
  device(f);
  f.run("S.record.mode.bundleId = 'app.heymode.mode.TEAM'; render();");
  await f.run("enter('device')");
  for (let i = 0; i < 5; i++) await new Promise(setImmediate);
  assert.doesNotMatch(f.app.innerHTML, /Not installed/);
  assert.match(f.app.innerHTML, /data-act="renewNow"/);
});

test('Done on Ready offers the optional Unlock by voice step, which skips or finishes to the dashboard', async () => {
  const f = fixture();
  f.run(`Object.assign(S, { screen: 'ready', record: { mode: { version: '1.0' }, paid: true, ownVpn: true } }); render();`);
  await f.click('unlockVoice');
  assert.equal(f.run('S.screen'), 'unlockVoice');
  assert.match(f.app.innerHTML, /Unlock by voice/);
  assert.match(f.app.innerHTML, /Run Custom Gesture/);
  assert.match(f.app.innerHTML, /Mode never sees your passcode/);
  assert.doesNotMatch(f.app.innerHTML, /passcode is/i, 'no passcode is ever asked for or shown');
  await f.click('readyBack');
  assert.equal(f.run('S.screen'), 'ready');
  await f.click('unlockVoice');
  await f.click('device');
  assert.equal(f.run('S.screen'), 'device');
});

// ---- this computer, checked before setup starts ---------------------------------------------------
const WIN_MISSING = { os: 'windows', ready: false, checks: [
  { id: 'driver', state: 'missing', title: 'iTunes (iPhone driver)', detail: 'Install Apple’s iTunes from apple.com.', fix: { label: 'Download iTunes', url: 'https://www.apple.com/itunes/download/win64' } },
  { id: 'internet', state: 'ok', title: 'Internet', detail: 'Connected.' }] };
const MAC_READY = { os: 'macos', ready: true, checks: [
  { id: 'driver', state: 'ok', title: 'iPhone connection', detail: 'Built in to macOS.' },
  { id: 'xcode', state: 'ok', title: 'Xcode', detail: 'Not needed. Mode Setup brings everything it uses.' },
  { id: 'internet', state: 'ok', title: 'Internet', detail: 'Connected.' }] };

test('Windows without iTunes: the download link shows and Get started stays off', async () => {
  let result = WIN_MISSING;
  const f = fixture({ check_computer: () => result });
  await f.run("go('welcome')");
  await new Promise(setImmediate);
  assert.match(f.app.innerHTML, /Download iTunes/);
  assert.match(f.app.innerHTML, /apple\.com\/itunes\/download\/win64/);
  assert.match(f.app.innerHTML, /data-act="start" disabled/);
  await f.click('start');
  assert.equal(f.run('S.screen'), 'welcome');
  // iTunes installed: Check again turns it green and lets setup start.
  result = { ...WIN_MISSING, ready: true, checks: WIN_MISSING.checks.map(c => ({ ...c, state: 'ok', fix: null })) };
  await f.click('checkComputer');
  assert.doesNotMatch(f.app.innerHTML, /data-act="start" disabled/);
  await f.click('start');
  assert.notEqual(f.run('S.screen'), 'welcome'); // on to Connect (and on to Trust: the test phone is plugged in)
});

test('Mac: says Xcode is not needed, and setup can start', async () => {
  const f = fixture({ check_computer: MAC_READY });
  await f.run("go('welcome')");
  await new Promise(setImmediate);
  assert.match(f.app.innerHTML, /Xcode/);
  assert.match(f.app.innerHTML, /Not needed/);
  assert.doesNotMatch(f.app.innerHTML, /data-act="start" disabled/);
});

test('Linux without usbmuxd shows the command to copy', async () => {
  const f = fixture({ check_computer: { os: 'linux', ready: false, checks: [
    { id: 'driver', state: 'missing', title: 'usbmuxd (iPhone driver)', detail: 'Install usbmuxd.', command: 'sudo apt install usbmuxd' }] } });
  await f.run("go('welcome')");
  await new Promise(setImmediate);
  assert.match(f.app.innerHTML, /sudo apt install usbmuxd/);
  assert.match(f.app.innerHTML, /data-act="copyCheck:0"/);
});
