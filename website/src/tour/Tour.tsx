/* The pinned feature tour: the chapter list, the words, the iPhone with real Mode screens, and a close-up card. */
import { useEffect } from 'react';
import { CHAPTERS } from './chapters';
import { startTour } from './engine';

const SCREENS = `${import.meta.env.BASE_URL}screens/`;

export function Tour() {
  // Draw once, then hand the stage to the engine. Nothing here re-renders, so the engine owns the classes it sets.
  useEffect(() => startTour(CHAPTERS.map((c) => c.screens.length), CHAPTERS.map((c) => c.name)), []);

  return (
    <div id="story" data-section="tour">
      <div className="stage tour" id="stage">
        <ol className="tix" aria-label="What Mode does">
          {CHAPTERS.map((c, i) => (
            <li key={c.name}><button type="button" data-go={i} data-ev="tour_jump" data-where="list" data-target={String(i)}><span>{String(i + 1).padStart(2, '0')}</span>{c.name}</button></li>
          ))}
        </ol>

        <div className="copy">
          {CHAPTERS.map((c) => (
            <section className="panel" key={c.name}>
              <p className="kick">{c.kick}</p>
              <h2>{c.title}</h2>
              <p>{c.body}</p>
              {c.extra}
            </section>
          ))}
        </div>

        <div className="right">
          <div className="pbox" id="pbox">
            <div className="dw" id="dw">
              <div className="device" id="device"><div className="scr" id="scr">
                <div className="sbar" id="sbar"><span>9:41</span><span className="ic">
                  <svg width="18" height="12" viewBox="0 0 18 12" fill="currentColor" aria-hidden="true"><rect x="0" y="8" width="3" height="4" rx="1" /><rect x="5" y="5" width="3" height="7" rx="1" /><rect x="10" y="2.5" width="3" height="9.5" rx="1" /><rect x="15" y="0" width="3" height="12" rx="1" /></svg>
                  <svg width="27" height="13" viewBox="0 0 27 13" fill="none" aria-hidden="true"><rect x=".5" y=".5" width="23" height="12" rx="3.5" stroke="currentColor" opacity=".4" /><rect x="2" y="2" width="18" height="9" rx="2" fill="currentColor" /><rect x="25" y="4.5" width="1.5" height="4" rx=".75" fill="currentColor" opacity=".4" /></svg>
                </span></div>
                <div className="di" />
                {CHAPTERS.map((c, i) => (
                  <div className="L" key={c.name}>
                    {c.screens.map((sc, k) => {
                      // The first chapter loads with the page; the engine loads each next chapter just before you reach it.
                      const url = `${SCREENS}${sc.file}.webp`;
                      return <img key={sc.file} className={`sc v${k + 1}${sc.dark ? ' dk' : ''}`} {...(i === 0 ? { src: url } : { 'data-src': url })} alt={sc.alt} width={390} height={844} decoding="async" />;
                    })}
                  </div>
                ))}
                <div className="hbar" id="hbar" />
              </div></div>
            </div>
          </div>
        </div>

        <div className="dtl" aria-live="polite">
          {CHAPTERS.map((c) => <div className={c.darkCard ? 'D dark' : 'D'} key={c.name}>{c.detail}</div>)}
        </div>

        <div className="hint" id="hint">Scroll once for each step
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M6 10l6 6 6-6" /></svg>
        </div>
        <nav className="rail" id="rail" aria-label="Chapters">
          {CHAPTERS.map((c, i) => <button type="button" key={c.name} data-go={i} data-ev="tour_jump" data-where="rail" data-target={String(i)} aria-label={c.name} title={c.name}><b><i /></b></button>)}
        </nav>
      </div>
    </div>
  );
}
