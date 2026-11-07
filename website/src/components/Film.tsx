/* The film under the headline.
 *
 * Silent and looping by default. "Play with sound" restarts it from the top with sound and controls
 * (full screen on phones); when that run ends it goes back to the silent loop.
 *
 * Two cuts: the 71 s landscape film for wide screens, and a ~44 s vertical cut for phones, with the
 * iPhone filling the frame and big captions, so it reads on mute.
 *
 * Smart playback:
 *  - It only starts loading once you get near it, then streams as it plays (it never downloads the
 *    whole file up front), and it only plays while at least half of it is on screen.
 *  - Scroll it away or switch tabs and it pauses, sound or not; come back and it carries on.
 *  - If you pause it yourself, it stays paused until you press play.
 *  - With reduced motion the silent loop never starts on its own.
 * It also grows from a rounded card to full width as you scroll to it.
 */
import { useEffect, useRef, useState } from 'react';
import { clamp, ease, reduced } from '../lib/anim';
import { FILM_FALLBACK } from '../links';
import { Mo } from './Mo';

const WIDE = { src: `${import.meta.env.BASE_URL}story.mp4`, length: '1:11' };
const TALL = { src: `${import.meta.env.BASE_URL}story-vertical.mp4`, length: '0:44' };

export function Film({ tall = false }: { tall?: boolean }) {
  const { src: SRC, length } = tall ? TALL : WIDE;
  const boxRef = useRef<HTMLDivElement>(null), inRef = useRef<HTMLDivElement>(null);
  const scrRef = useRef<HTMLDivElement>(null), vRef = useRef<HTMLVideoElement>(null);
  const [sound, setSound] = useState(false);
  const [broken, setBroken] = useState(false);
  const [src, setSrc] = useState<string | undefined>(undefined);

  // What the observers read; refs so they don't re-subscribe on every render.
  const inView = useRef(false);        // at least half of the film is on screen
  const userPaused = useRef(false);    // the viewer paused it with the controls
  const autoPausing = useRef(false);   // we are pausing it ourselves (scrolled away, tab hidden)
  const soundRef = useRef(false);

  const wantsPlay = () => inView.current && !document.hidden && !userPaused.current && (soundRef.current || !reduced());
  const sync = () => {
    const v = vRef.current; if (!v || !v.src) return;
    if (wantsPlay()) { if (v.paused) v.play()?.catch(() => {}); }
    else if (!v.paused) { autoPausing.current = true; v.pause(); }
  };

  useEffect(() => {
    const box = boxRef.current!, v = vRef.current!;
    v.muted = true; // React doesn't set the muted attribute itself, and silent autoplay needs it

    // Load the file only when the film is getting close (one screen away).
    const near = new IntersectionObserver((es) => {
      if (es.some((e) => e.isIntersecting)) { setSrc(SRC); near.disconnect(); }
    }, { rootMargin: '100% 0px' });
    near.observe(box);

    // Play while half or more is visible; pause otherwise.
    const seen = new IntersectionObserver((es) => {
      for (const e of es) inView.current = e.intersectionRatio >= 0.5;
      sync();
    }, { threshold: [0, 0.5, 1] });
    seen.observe(box);

    const onVisibility = () => sync();
    document.addEventListener('visibilitychange', onVisibility);

    // A pause we didn't cause is the viewer's own; a play clears it.
    const onPause = () => { if (autoPausing.current) autoPausing.current = false; else if (!v.ended) userPaused.current = true; };
    const onPlay = () => { userPaused.current = false; };
    v.addEventListener('pause', onPause);
    v.addEventListener('play', onPlay);

    // Grow from a rounded card to full size as it scrolls into view.
    let raf = 0;
    const grow = () => {
      raf = 0;
      const r = box.getBoundingClientRect(), H = innerHeight;
      const k = reduced() ? 1 : ease(clamp((H - r.top) / (H * 0.8)));
      inRef.current!.style.transform = `scale(${(0.84 + 0.16 * k).toFixed(4)})`;
      scrRef.current!.style.borderRadius = Math.round(40 - 16 * k) + 'px';
    };
    const onScroll = () => { if (!raf) raf = requestAnimationFrame(grow); };
    addEventListener('scroll', onScroll, { passive: true });
    addEventListener('resize', grow);
    grow();

    return () => {
      near.disconnect(); seen.disconnect(); cancelAnimationFrame(raf);
      document.removeEventListener('visibilitychange', onVisibility);
      v.removeEventListener('pause', onPause); v.removeEventListener('play', onPlay);
      removeEventListener('scroll', onScroll); removeEventListener('resize', grow);
    };
  }, [SRC]);

  const playWithSound = () => {
    if (broken) { open(FILM_FALLBACK, '_blank'); return; }
    const v = vRef.current!;
    if (!v.src) setSrc(SRC);
    soundRef.current = true; userPaused.current = false; setSound(true);
    v.muted = false; v.loop = false; v.currentTime = 0; v.controls = true;
    v.play()?.catch(() => {});
    // On phones, go full screen so the film fills the display (iOS has its own call for video).
    if (matchMedia('(max-width: 900px)').matches) {
      const fs = v as HTMLVideoElement & { webkitEnterFullscreen?: () => void };
      if (v.requestFullscreen) v.requestFullscreen().catch(() => fs.webkitEnterFullscreen?.());
      else fs.webkitEnterFullscreen?.();
    }
  };
  const ended = () => {
    const v = vRef.current!;
    soundRef.current = false; userPaused.current = false; setSound(false);
    v.muted = true; v.loop = true; v.controls = false; v.currentTime = 0;
    sync();
  };

  return (
    <div className={`reel${tall ? ' tall' : ''}${sound ? ' sound' : ''}`} id="film" ref={boxRef}>
      <div className="reel-in" ref={inRef}>
        <Mo mood={sound ? 'listening' : 'idle'} className="reel-mo" />
        <div className="reel-scr" ref={scrRef}>
          <video ref={vRef} src={src} muted loop playsInline preload={src ? 'metadata' : 'none'}
            onLoadedData={sync} onError={() => { if (src) setBroken(true); }} onEnded={ended}
            aria-label={tall ? 'Mode, the short film: texting Sam and ordering the usual coffee, by voice from the car' : 'Mode, the film: three errands done by voice from the car'} />
          <span className="reel-shade" />
          <button className="snd" type="button" onClick={playWithSound}>
            <i><svg width="18" height="18" viewBox="0 0 24 24" fill="#1A1A1F" aria-hidden="true"><path d="M8 5v14l11-7z" /></svg></i>
            <span>{broken ? 'Watch it on GitHub' : 'Play with sound'}</span><small>{length}</small>
          </button>
        </div>
      </div>
    </div>
  );
}
