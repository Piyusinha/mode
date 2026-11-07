/* Every published release, its notes and its files, served from heymode.app (/api/releases, /download/v/…). */
import { marked } from 'marked';
import { useEffect, useState } from 'react';
import { Page } from './Page';

type Release = { tag: string; name: string; date: string; prerelease: boolean; notes: string; files: { name: string; size: number }[] };

/** The files people want, with friendly names: Mode Setup for each computer, and the iPhone app for SideStore. */
const KINDS: [RegExp, string][] = [
  [/^Mode-Setup-[\d.]+-mac\.dmg$/, 'Mac'],
  [/^Mode-Setup-[\d.]+-windows\.exe$/, 'Windows'],
  [/^Mode-Setup-[\d.]+-windows\.msi$/, 'Windows (.msi)'],
  [/^Mode-Setup-[\d.]+-linux\.AppImage$/, 'Linux'],
  [/^Mode-Setup-[\d.]+-linux\.deb$/, 'Linux (.deb)'],
  [/^Mode\.ipa$/, 'iPhone app (.ipa)'],
];
const mb = (n: number) => `${Math.round(n / 1048576)} MB`;
const day = (d: string) => new Date(d).toLocaleDateString(undefined, { day: 'numeric', month: 'long', year: 'numeric' });

export function Releases() {
  const [list, setList] = useState<Release[] | null>(null);
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    fetch('/api/releases').then((r) => (r.ok ? r.json() : Promise.reject())).then(setList).catch(() => setFailed(true));
  }, []);

  return (
    <Page kick="Releases" title={<>What's new <em>in Mode.</em></>}>
      {failed && <p className="lead">Couldn't load the releases just now. Try again in a moment.</p>}
      {!list && !failed && <p className="lead">Loading…</p>}
      {list?.length === 0 && <p className="lead">No releases yet.</p>}
      {list?.map((r, i) => (
        <article className="rel" key={r.tag}>
          <header>
            <h2>{r.name}</h2>
            <span>{day(r.date)}{i === 0 && <b className="latest">Latest</b>}{r.prerelease && <b className="pre">Pre-release</b>}</span>
          </header>
          <div className="files">
            {KINDS.flatMap(([re, label]) => r.files.filter((f) => re.test(f.name)).map((f) => (
              <a key={f.name} className={label === 'Mac' ? 'btn ink sm' : 'btn soft sm'} href={`/download/v/${r.tag}/${f.name}`}>
                {label}<small>{mb(f.size)}</small>
              </a>
            )))}
          </div>
          <div className="md" dangerouslySetInnerHTML={{ __html: marked.parse(r.notes, { async: false }) as string }} />
        </article>
      ))}
    </Page>
  );
}
