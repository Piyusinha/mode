import { useMemo } from 'react';
import { withVisitor } from '../lib/analytics';
import { ANDROID_HREF, detectPlatform, isAndroid } from '../lib/platform';
import { useNarrow } from '../lib/useNarrow';
import { DownloadButton } from './DownloadButton';
import { Film } from './Film';
import { ArrowDown } from './Icons';

export function Hero() {
  const narrow = useNarrow();
  const android = useMemo(isAndroid, []);
  const computer = useMemo(() => detectPlatform() !== null, []);
  return (
    <section className="hero" id="top" data-section="hero">
      <p className="kick">Free &amp; open source · iPhone and Android</p>
      <h1>Just say it. Your phone <em>does the rest.</em></h1>
      <p className="sub">Mode opens your apps, taps, types and gets it done, and shows you every step. It runs on the ChatGPT, Claude or OpenRouter account you already have.</p>
      <div className="row">
        <DownloadButton phoneLabel="Get Mode Setup" where="hero" />
        {/* data-go="0" is picked up by the tour engine: it jumps to the first chapter. */}
        <button className="btn soft" type="button" data-go="0" data-ev="tour_jump" data-where="hero" data-target="0">See what else it does <ArrowDown /></button>
      </div>
      {/* Under the buttons: what Android needs, or (on a computer) the way to the Android app. iPhone and iPad get nothing here. */}
      {android
        ? <p className="need">Android 13 or later. Your phone may ask you to allow installs from your browser.</p>
        : computer && <p className="need">On Android? Open heymode.app on your phone, or <a href={withVisitor(ANDROID_HREF, 'hero')} data-ev="download_click" data-where="hero" data-target="android" data-detail="desktop">download the app</a>.</p>}
      <ul className="proof"><li>No Mode account</li><li>No Mode server</li><li>Your AI, your keys</li><li>Apache 2.0</li></ul>
      {/* The main feature first. Phones get the vertical cut, pinned in view for about a screen of
          scrolling so a quick flick can't skip it; wider screens get the landscape film. */}
      {narrow
        ? <div className="pin-film"><div className="pin-in"><Film tall key="tall" /></div></div>
        : <Film key="wide" />}
    </section>
  );
}
