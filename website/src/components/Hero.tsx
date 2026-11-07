import { useNarrow } from '../lib/useNarrow';
import { DownloadButton } from './DownloadButton';
import { Film } from './Film';
import { ArrowDown } from './Icons';

export function Hero() {
  const narrow = useNarrow();
  return (
    <section className="hero" id="top">
      <p className="kick">Free &amp; open source · iPhone now · Android coming soon</p>
      <h1>Just say it. Your phone <em>does the rest.</em></h1>
      <p className="sub">Mode opens your apps, taps, types and gets it done, with every step in your Live Activity. It runs on the ChatGPT or Claude you already have.</p>
      <div className="row">
        <DownloadButton phoneLabel="Get Mode Setup" />
        {/* data-go="0" is picked up by the tour engine: it jumps to the first chapter. */}
        <button className="btn soft" type="button" data-go="0">See what else it does <ArrowDown /></button>
      </div>
      <ul className="proof"><li>No Mode account</li><li>No Mode server</li><li>Your AI, your keys</li><li>Apache 2.0</li></ul>
      {/* The main feature first. Phones get the vertical cut, pinned in view for about a screen of
          scrolling so a quick flick can't skip it; wider screens get the landscape film. */}
      {narrow
        ? <div className="pin-film"><div className="pin-in"><Film tall key="tall" /></div></div>
        : <Film key="wide" />}
    </section>
  );
}
