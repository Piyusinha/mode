// Sample states for each screen, used only with ?mock=<name> to compare the window with its board.
// Never loaded data: the real window talks to the engine.
'use strict';
(function () {
  const phone = { udid: '00008150-0012345678901234', name: 'Alex’s iPhone', model: 'iPhone18,1', ios: '27.2', usb: true };
  const free = { id: 'FGHIJ67890', name: 'Personal Team', kind: 'free' };
  const paid = { id: 'ABCDE12345', name: 'Acme Labs', kind: 'paid' };
  const mode = { bundleId: 'app.heymode.mode.ABCDE12345', name: 'Mode', version: '1.4', teamId: 'ABCDE12345', expires: '2026-11-10T08:20:00Z' };
  const rec = (o) => Object.assign({ phone: phone.name, teamId: free.id, teamName: free.name, paid: false, ownVpn: false, mode, sidestore: { version: '0.6' } }, o);
  const stages = (vpn) => [
    ...(vpn ? [{ id: 'vpn', title: 'Turned on Mode’s own VPN', sub: 'Network Extensions is on for Mode in your developer account', state: 'done' }] : []),
    { id: 'signing', title: `Signed with ${vpn ? paid.name : free.name}`, sub: vpn ? 'Lasts until Tue 3 Nov 2027' : 'Lasts until Mon 10 Nov', state: 'done' },
    { id: 'installing', title: 'Installing Mode on your iPhone', sub: 'Keep it unlocked', state: 'wait', fraction: 0.62 },
    ...(vpn ? [] : [{ id: 'sidestore', title: 'Installing SideStore', state: 'todo' }]),
    { id: 'control', title: 'Connecting phone control', sub: 'So Mode can see and tap your screen', state: 'todo' },
  ];
  window.MOCKS = {
    welcome: { computer: { os: 'macos', ready: true, checks: [
      { id: 'driver', state: 'ok', title: 'iPhone connection', detail: 'Built in to macOS.' },
      { id: 'xcode', state: 'ok', title: 'Xcode', detail: 'Not needed. Mode Setup brings everything it uses.' },
      { id: 'internet', state: 'ok', title: 'Internet', detail: 'Connected.' }] } },
    welcomeChecking: {},
    welcomeUpdate: { setupUpdate: { current: '0.1.2', latest: '0.1.3', available: true, url: 'https://heymode.app/download/mac' }, computer: { os: 'macos', ready: true, checks: [
      { id: 'driver', state: 'ok', title: 'iPhone connection', detail: 'Built in to macOS.' },
      { id: 'xcode', state: 'ok', title: 'Xcode', detail: 'Not needed. Mode Setup brings everything it uses.' },
      { id: 'internet', state: 'ok', title: 'Internet', detail: 'Connected.' }] } },
    welcomeWindows: { computer: { os: 'windows', ready: false, checks: [
      { id: 'driver', state: 'missing', title: 'iTunes (iPhone driver)', detail: 'Install Apple’s iTunes from apple.com (not the Microsoft Store version). It brings the driver this app uses to talk to your iPhone. You don’t need to open it.', fix: { label: 'Download iTunes', url: 'https://www.apple.com/itunes/download/win64' } },
      { id: 'internet', state: 'ok', title: 'Internet', detail: 'Connected.' }] } },
    welcomeLinux: { computer: { os: 'linux', ready: false, checks: [
      { id: 'driver', state: 'missing', title: 'usbmuxd (iPhone driver)', detail: 'Install usbmuxd, the service Linux uses to talk to iPhones, then plug your iPhone in. On Fedora: sudo dnf install usbmuxd.', command: 'sudo apt install usbmuxd' },
      { id: 'internet', state: 'missing', title: 'Internet', detail: 'Connect to the internet. Setup signs Mode with your Apple account, online.' }] } },
    connect: { screen: 'connect' },
    pickPhone: { screen: 'pickPhone', phones: [phone, { udid: 'x', name: 'Test iPhone', model: 'iPhone17,3', ios: '26.4', usb: true }], phone },
    trust: { screen: 'trust', phone, readiness: 'needsTrust' },
    devMode: { screen: 'devMode', phone, readiness: 'needsDeveloperMode' },
    signIn: { screen: 'signIn', phone, readiness: 'ready', email: 'alex@example.com' },
    signInWrong: { screen: 'signIn', phone, readiness: 'ready', email: 'alex@example.com', signInError: 'Wrong Apple Account or password.' },
    code: { screen: 'code', phone, readiness: 'ready', codeReq: { numbers: [[1, '+91 •••••• •••41']], lastFailed: false }, code: '481' },
    helperDown: { screen: 'helperDown', phone, readiness: 'ready', helpers: [{ name: 'iLoader', url: 'https://ani.iloader.app', ms: 120 }, { name: 'SideStore', url: 'https://ani.sidestore.io', error: 'timed out' }] },
    team: { screen: 'team', phone, readiness: 'ready', teams: [paid, free], team: paid },
    sidestore: { screen: 'sidestore', phone, readiness: 'ready', team: free, renew: 'sidestore' },
    install: { screen: 'install', phone, readiness: 'ready', team: free, renew: 'sidestore', stages: stages(false) },
    installPaid: { screen: 'install', phone, readiness: 'ready', team: paid, ownVpn: true, stages: stages(true) },
    unplugged: { screen: 'unplugged', phone, readiness: 'ready', team: free, renew: 'sidestore', stages: stages(false) },
    limit: { screen: 'limit', phone, readiness: 'ready', team: free, renew: 'sidestore', phoneApps: { sideloaded: [{ name: 'SideStore' }, { name: 'StikDebug' }, { name: 'Feather' }] } },
    vpn: { screen: 'vpn', phone, readiness: 'ready', team: paid, vpn: { appId: 'app.heymode.mode.ABCDE12345', tunnelId: 'app.heymode.mode.ABCDE12345.tunnel', appOn: false, tunnelOn: false } },
    vpnOn: { screen: 'vpn', phone, readiness: 'ready', team: paid, vpn: { appId: 'app.heymode.mode.ABCDE12345', tunnelId: 'app.heymode.mode.ABCDE12345.tunnel', appOn: true, tunnelOn: true } },
    onPhone: { screen: 'onPhone', phone, readiness: 'ready', installedSidestore: true, phoneApps: { localDevVpn: null } },
    onPhonePaid: { screen: 'onPhone', phone, ownVpn: true, readiness: 'ready' },
    onPhoneConfirmed: { screen: 'onPhone', phone, ownVpn: true, phoneTestConfirmed: true, phonePresenceKnown: true, cableDisconnected: true },
    ready: { screen: 'ready', phone, readiness: 'ready', record: rec({}) },
    readyPaid: { screen: 'ready', phone, readiness: 'ready', record: rec({ teamId: paid.id, teamName: paid.name, paid: true, ownVpn: true, sidestore: null, mode: Object.assign({}, mode, { expires: '2027-11-03T11:04:34Z' }) }) },
    unlockVoice: { screen: 'unlockVoice', phone, readiness: 'ready', record: rec({}) },
    device: { screen: 'device', phone, phones: [phone], readiness: 'ready', prefs: { email: 'alex@example.com', phones: {} }, record: rec({ sidestore: null, mode: Object.assign({}, mode, { expires: new Date(Date.now() + 2 * 86400000).toISOString() }) }) },
    deviceMissing: { screen: 'device', phone, phones: [phone], readiness: 'ready', prefs: { email: 'alex@example.com', phones: {} }, phoneAppsFor: phone.udid, phoneApps: { modes: [{ bundleId: 'app.heymode.mode', name: 'Mode', version: '0.1' }], sideloaded: [] }, record: rec({ teamId: paid.id, teamName: paid.name, paid: true, ownVpn: true, sidestore: null, mode: Object.assign({}, mode, { expires: '2027-11-03T11:04:34Z' }) }) },
    deviceOK: { screen: 'device', phone, phones: [], readiness: 'ready', prefs: { email: 'alex@example.com', phones: {} }, record: rec({}) },
    settingsUpdate: { screen: 'settings', phone, setupVersion: '0.1.2', setupUpdate: { current: '0.1.2', latest: '0.1.3', available: true, url: 'https://heymode.app/download/mac' }, prefs: { email: 'alex@example.com', phones: {} }, hasSaved: true, record: rec({}) },
    settings: { screen: 'settings', setupVersion: '0.1.2', setupUpdate: { current: '0.1.2', latest: '0.1.2', available: false, url: 'https://heymode.app/download/mac' }, phone, prefs: { email: 'alex@example.com', phones: {} }, hasSaved: true, record: rec({}), helpers: [{ name: 'iLoader', url: 'https://ani.iloader.app', ms: 120 }, { name: 'SideStore', url: 'https://ani.sidestore.io', ms: 300 }] },
  };
})();
