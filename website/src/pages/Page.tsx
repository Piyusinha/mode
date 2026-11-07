/* A plain page on heymode.app (Privacy, Licence, Releases): the top bar, the content, the footer. */
import { useEffect, type ReactNode } from 'react';
import { Footer } from '../components/Footer';
import { TopBar } from '../components/TopBar';

export function Page({ title, kick, children }: { title: ReactNode; kick: string; children: ReactNode }) {
  useEffect(() => { document.title = `${kick} · Mode`; }, [kick]);
  return (
    <>
      <TopBar />
      <main className="doc">
        <p className="kick">{kick}</p>
        <h1>{title}</h1>
        {children}
      </main>
      <section className="wrap"><Footer /></section>
    </>
  );
}
