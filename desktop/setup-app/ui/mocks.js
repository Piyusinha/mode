// Sample states for each screen, used only with ?mock=<name> to compare the window with its board.
// Never loaded data: the real window talks to the engine.
'use strict';
(function () {
  const phone = { udid: '00008150-001E20D41484401C', name: 'Piyush’s iPhone', model: 'iPhone18,1', ios: '27.2', usb: true };
  const free = { id: 'ABCDE12345', name: 'Personal Team', kind: 'free' };
  const paid = { id: 'BH9TWW5HPR', name: 'Acme Labs', kind: 'paid' };
  const mode = { bundleId: 'com.piyush.mode.ABCDE12345', name: 'Mode', version: '1.4', teamId: 'ABCDE12345', expires: '2026-11-10T08:20:00Z' };
  const rec = (o) => Object.assign({ phone: phone.name, teamId: free.id, teamName: free.name, paid: false, ownVpn: false, mode, sidestore: { version: '0.6' } }, o);
  const stages = (vpn) => [
    ...(vpn ? [{ id: 'vpn', title: 'Turned on Mode’s own VPN', sub: 'Network Extensions is on for Mode in your developer account', state: 'done' }] : []),
    { id: 'signing', title: `Signed with ${vpn ? paid.name : free.name}`, sub: vpn ? 'Lasts until Tue 3 Nov 2027' : 'Lasts until Mon 10 Nov', state: 'done' },
    { id: 'installing', title: 'Installing Mode on your iPhone', sub: 'Keep it unlocked', state: 'wait', fraction: 0.62 },
    ...(vpn ? [] : [{ id: 'sidestore', title: 'Installing SideStore', state: 'todo' }]),
    { id: 'control', title: 'Connecting phone control', sub: 'So Mode can see and tap your screen', state: 'todo' },
  ];
  window.MOCKS = {
    welcome: {},
    connect: { screen: 'connect' },
    pickPhone: { screen: 'pickPhone', phones: [phone, { udid: 'x', name: 'Test iPhone', model: 'iPhone17,3', ios: '26.4', usb: true }], phone },
    trust: { screen: 'trust', phone, readiness: 'needsTrust' },
    devMode: { screen: 'devMode', phone, readiness: 'needsDeveloperMode' },
    signIn: { screen: 'signIn', phone, readiness: 'ready', email: 'piyush@icloud.com' },
    signInWrong: { screen: 'signIn', phone, readiness: 'ready', email: 'piyush@icloud.com', signInError: 'Wrong Apple Account or password.' },
    code: { screen: 'code', phone, readiness: 'ready', codeReq: { numbers: [[1, '+91 •••••• •••41']], lastFailed: false }, code: '481' },
    helperDown: { screen: 'helperDown', phone, readiness: 'ready', helpers: [{ name: 'iLoader', url: 'https://ani.iloader.app', ms: 120 }, { name: 'SideStore', url: 'https://ani.sidestore.io', error: 'timed out' }] },
    team: { screen: 'team', phone, readiness: 'ready', teams: [paid, free], team: paid },
    sidestore: { screen: 'sidestore', phone, readiness: 'ready', team: free, renew: 'sidestore' },
    install: { screen: 'install', phone, readiness: 'ready', team: free, renew: 'sidestore', stages: stages(false) },
    installPaid: { screen: 'install', phone, readiness: 'ready', team: paid, ownVpn: true, stages: stages(true) },
    unplugged: { screen: 'unplugged', phone, readiness: 'ready', team: free, renew: 'sidestore', stages: stages(false) },
    limit: { screen: 'limit', phone, readiness: 'ready', team: free, renew: 'sidestore', phoneApps: { sideloaded: [{ name: 'SideStore' }, { name: 'StikDebug' }, { name: 'Feather' }] } },
    vpn: { screen: 'vpn', phone, readiness: 'ready', team: paid, vpn: { appId: 'com.piyush.mode.BH9TWW5HPR', tunnelId: 'com.piyush.mode.BH9TWW5HPR.tunnel', appOn: false, tunnelOn: false } },
    vpnOn: { screen: 'vpn', phone, readiness: 'ready', team: paid, vpn: { appId: 'com.piyush.mode.BH9TWW5HPR', tunnelId: 'com.piyush.mode.BH9TWW5HPR.tunnel', appOn: true, tunnelOn: true } },
    onPhone: { screen: 'onPhone', phone, readiness: 'ready', installedSidestore: true, phoneApps: { localDevVpn: null } },
    ready: { screen: 'ready', phone, readiness: 'ready', record: rec({}) },
    readyPaid: { screen: 'ready', phone, readiness: 'ready', record: rec({ teamId: paid.id, teamName: paid.name, paid: true, ownVpn: true, sidestore: null, mode: Object.assign({}, mode, { expires: '2027-11-03T11:04:34Z' }) }) },
    device: { screen: 'device', phone, phones: [phone], readiness: 'ready', prefs: { email: 'piyush@icloud.com', phones: {} }, record: rec({ sidestore: null, mode: Object.assign({}, mode, { expires: new Date(Date.now() + 2 * 86400000).toISOString() }) }) },
    deviceOK: { screen: 'device', phone, phones: [], readiness: 'ready', prefs: { email: 'piyush@icloud.com', phones: {} }, record: rec({}) },
    settings: { screen: 'settings', phone, prefs: { email: 'piyush@icloud.com', phones: {} }, hasSaved: true, record: rec({}), helpers: [{ name: 'iLoader', url: 'https://ani.iloader.app', ms: 120 }, { name: 'SideStore', url: 'https://ani.sidestore.io', ms: 300 }] },
  };
})();
