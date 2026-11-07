/* Mode Setup releases: the desktop installer only (phone builds are left out until there's an Android app to list).
   The newest release is a card with its downloads and what's new; older ones are compact rows.
   Data comes from heymode.app (/api/releases); downloads from /download/v/<tag>/<file>. */
import { marked } from 'marked';
import { useEffect, useMemo, useState } from 'react';
import { PlatformIcon } from '../components/DownloadButton';
import { detectPlatform, type Platform } from '../lib/platform';
import { Page } from './Page';

type Release = { tag: string; name: string; date: string; prerelease: boolean; notes: string; files: { name: string; size: number }[] };
type Setup = { tag: string; version: string; date: string; notes: string; files: Map<string, { name: string; size: number }> };

/** Each installer, by the part of its file name after the version. */
const MAIN: { key: string; platform: Platform; label: string }[] = [
  { key: 'mac.dmg', platform: 'mac', label: 'Mac' },
  { key: 'windows.exe', platform: 'windows', label: 'Windows' },
  { key: 'linux.AppImage', platform: 'linux', label: 'Linux' },
];
const EXTRA = [{ key: 'windows.msi', label: '.msi for Windows' }, { key: 'linux.deb', label: '.deb for Linux' }];

const versionOf = (tag: string) => (tag.match(/^(?:Mode-Desktop-App-|v)(\d[\d.]*)$/) || [])[1] ?? null;
const newer = (a: string, b: string) => {
  const x = a.split('.').map(Number), y = b.split('.').map(Number);
  for (let i = 0; i < Math.max(x.length, y.length); i++) if ((x[i] ?? 0) !== (y[i] ?? 0)) return (x[i] ?? 0) > (y[i] ?? 0);
  return false;
};
/** Just the "What's new" part of a release's notes; install steps and file lists are the same every time. */
const whatsNew = (notes: string) => {
  const m = notes.replace(/\r/g, '').match(/## What[’']s new\s*\n([\s\S]*?)(?=\n## |$)/);
  return m ? m[1].replace(/^#{3,} Mode Setup\s*$/gm, '').trim() : '';
};
const html = (md: string) => marked.parse(md, { async: false }) as string;
const mb = (n: number) => `${Math.round(n / 1048576)} MB`;
const day = (d: string) => new Date(d).toLocaleDateString(undefined, { day: 'numeric', month: 'long', year: 'numeric' });

function toSetups(list: Release[]): Setup[] {
  return list
    .filter((r) => !r.prerelease)
    .map((r) => {
      const version = versionOf(r.tag);
      if (!version) return null;
      const files = new Map<string, { name: string; size: number }>();
      for (const f of r.files) {
        const k = f.name.match(new RegExp(`^Mode-Setup-${version.replace(/\./g, '\\.')}-(.+)$`))?.[1];
        if (k) files.set(k, f);
      }
      return files.size ? { tag: r.tag, version, date: r.date, notes: whatsNew(r.notes), files } : null;
    })
    .filter((s): s is Setup => !!s)
    .sort((a, b) => (newer(a.version, b.version) ? -1 : 1));
}

const href = (s: Setup, key: string) => `/download/v/${s.tag}/${s.files.get(key)!.name}`;

export function Releases() {
  const [list, setList] = useState<Release[] | null>(null);
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    fetch('/api/releases').then((r) => (r.ok ? r.json() : Promise.reject())).then(setList).catch(() => setFailed(true));
  }, []);
  const setups = useMemo(() => (list ? toSetups(list) : null), [list]);
  const mine = useMemo(detectPlatform, []);
  const order = (s: Setup) => [...MAIN].sort((a, b) => Number(b.platform === mine) - Number(a.platform === mine)).filter((m) => s.files.has(m.key));
  const [latest, ...older] = setups ?? [];

  return (
    <Page kick="Releases" title={<>Mode Setup, <em>every version.</em></>}>
      <p className="lead">Mode Setup is the small app for your computer that installs Mode on your iPhone and keeps it up to date. Download it, open it, plug in your iPhone, and follow the steps.</p>

      {failed && <p className="lead">Couldn't load the releases just now. Try again in a moment.</p>}
      {!setups && !failed && <p className="lead">Loading…</p>}
      {setups?.length === 0 && <p className="lead">No releases yet.</p>}

      {latest && (
        <article className="rel-now">
          <header>
            <h2>Mode Setup {latest.version}</h2>
            <span>{day(latest.date)}<b className="latest">Latest</b></span>
          </header>
          <div className="files">
            {order(latest).map((m, i) => (
              <a key={m.key} className={`btn ${i === 0 ? 'ink' : 'soft'} dl`} href={href(latest, m.key)}>
                <PlatformIcon p={m.platform} />{m.label}<small>{mb(latest.files.get(m.key)!.size)}</small>
              </a>
            ))}
          </div>
          {EXTRA.some((x) => latest.files.has(x.key)) && (
            <p className="also">Also: {EXTRA.filter((x) => latest.files.has(x.key)).map((x, i) => (
              <span key={x.key}>{i > 0 && ' · '}<a href={href(latest, x.key)}>{x.label}</a></span>
            ))}</p>
          )}
          {latest.notes && <><h3>What's new</h3><div className="md" dangerouslySetInnerHTML={{ __html: html(latest.notes) }} /></>}
        </article>
      )}

      {older.length > 0 && (
        <section className="rel-old" aria-label="Earlier versions">
          <h3>Earlier versions</h3>
          {older.map((s) => (
            <details key={s.tag}>
              <summary>
                <b>{s.version}</b><span className="when">{day(s.date)}</span>
                <span className="links">
                  {[...MAIN, ...EXTRA.map((x) => ({ ...x, platform: null }))].filter((m) => s.files.has(m.key)).map((m) => (
                    <a key={m.key} href={href(s, m.key)} onClick={(e) => e.stopPropagation()}>{m.label}</a>
                  ))}
                </span>
              </summary>
              <div className="md" dangerouslySetInnerHTML={{ __html: html(s.notes || 'The first release.') }} />
            </details>
          ))}
        </section>
      )}
    </Page>
  );
}
