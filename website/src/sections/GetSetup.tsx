import { useMemo } from 'react';
import { PlatformIcon } from '../components/DownloadButton';
import { REPO } from '../links';
import { withVisitor } from '../lib/analytics';
import { detectPlatform, others, PLATFORMS } from '../lib/platform';

const STEPS = [
  ['Download Mode Setup', 'Open it on your Mac, Windows or Linux computer. It walks you through each step.'],
  ['Plug in your iPhone', 'Connect with a cable. Mode Setup signs Mode with your Apple account and installs it.'],
  ['Say hello to Mo', 'Open Mode, connect your ChatGPT or Claude, and give it its first task.'],
];

export function GetSetup() {
  // This computer's installer first; on a phone, Mac first (Mode Setup runs on a computer).
  const mine = useMemo(detectPlatform, []);
  const first = mine ?? 'mac';
  return (
    <section className="wrap" id="get" data-section="get" aria-labelledby="get-h" style={{ scrollMarginTop: 'calc(24px + var(--sat))' }}>
      <div className="droid setup rv">
        <ul className="pills"><li className="now">Free · open source · a few minutes, once</li></ul>
        <h2 id="get-h">Download <em>Mode Setup.</em></h2>
        <p>A small app for your computer that installs Mode on your iPhone for you. Plug in your iPhone, and it signs Mode with your Apple account and installs it.</p>
        <div className="row">
          <a className="btn ink dl" href={withVisitor(PLATFORMS[first].href, 'get')} data-ev="download_click" data-where="get" data-target={first} data-detail={mine ? `detected-${mine}` : 'phone'}><PlatformIcon p={first} />Download Mode installer for {PLATFORMS[first].label}</a>
          {others(first).map((p) => <a key={p} className="btn soft dl" href={withVisitor(PLATFORMS[p].href, 'get')} data-ev="download_click" data-where="get" data-target={p} data-detail={mine ? `detected-${mine}` : 'phone'}><PlatformIcon p={p} />{PLATFORMS[p].label}</a>)}
        </div>
        <p className="oss">
          <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M12 2a10 10 0 0 0-3.2 19.5c.5.1.7-.2.7-.5v-1.7c-2.8.6-3.4-1.3-3.4-1.3-.5-1.2-1.1-1.5-1.1-1.5-.9-.6.1-.6.1-.6 1 .1 1.5 1 1.5 1 .9 1.5 2.4 1.1 2.9.8.1-.7.4-1.1.7-1.3-2.2-.3-4.6-1.1-4.6-5 0-1.1.4-2 1-2.7-.1-.3-.4-1.3.1-2.7 0 0 .8-.3 2.8 1a9.6 9.6 0 0 1 5 0c1.9-1.3 2.8-1 2.8-1 .5 1.4.2 2.4.1 2.7.6.7 1 1.6 1 2.7 0 3.9-2.3 4.7-4.6 5 .4.3.7.9.7 1.9V21c0 .3.2.6.7.5A10 10 0 0 0 12 2z" /></svg>
          <span><b>Open source, Apache 2.0.</b> Every line of Mode is <a href={REPO} target="_blank" rel="noopener" data-ev="github_click" data-where="get">on GitHub</a>. You don't need to build anything though: Mode Setup downloads, signs and installs Mode for you.</span>
        </p>
        <ol className="steps3">
          {STEPS.map(([title, text]) => <li key={title}><b>{title}</b><span>{text}</span></li>)}
        </ol>
        {!mine && <p className="need" style={{ marginTop: 14, opacity: 0.85 }}>On your phone? Open heymode.app on your computer to download it there.</p>}
        <p className="need">Also as <a href={withVisitor('/download/windows-msi', 'get')} data-ev="download_click" data-where="get" data-target="windows-msi">.msi</a> for Windows and <a href={withVisitor('/download/linux-deb', 'get')} data-ev="download_click" data-where="get" data-target="linux-deb">.deb</a> for Linux, or see <a href="/releases" data-ev="nav_click" data-where="get" data-target="releases">all releases</a>. You'll need an iPhone on iOS 27 or later, a Mac, Windows or Linux computer once for setup, a free or paid Apple account, and a ChatGPT or Claude plan or an OpenRouter key.</p>
      </div>
    </section>
  );
}
