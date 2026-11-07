/*
 * The tour engine. Tour.tsx draws the stage once; this plays it.
 *
 * Each chapter has a few steps (one per screen). A wheel tick, a swipe (however long) or an arrow key
 * moves exactly one step; the move is a short scroll tween. A fling that carries the page into the
 * tour from outside lands on its first (or last) step instead of flying through. The phone layer and the close-up
 * card of the current chapter get classes s1..sN (steps reached) and cN (the current step); CSS
 * does the rest. Past the last step the page scrolls normally again.
 */
import { clamp, reduced } from '../lib/anim';

/** Phone-width screens (they get a little more scroll per step). */
const narrow = () => matchMedia('(max-width: 900px)').matches;
/** Screen heights of scroll per step. */
const B = () => (narrow() ? 0.85 : 0.7);

export function startTour(steps: number[]): () => void {
  const ac = new AbortController();
  const on = <K extends keyof WindowEventMap>(type: K, fn: (e: WindowEventMap[K]) => void, opts: AddEventListenerOptions = {}) =>
    addEventListener(type, fn, { ...opts, signal: ac.signal });
  const $ = (id: string) => document.getElementById(id) as HTMLElement;

  const story = $('story'), stage = $('stage');
  const panels = [...document.querySelectorAll<HTMLElement>('#stage .panel')];
  const layers = [...document.querySelectorAll<HTMLElement>('#scr .L')];
  const cards = [...document.querySelectorAll<HTMLElement>('.dtl .D')];
  const tix = [...document.querySelectorAll<HTMLElement>('.tix button')];
  const railFills = [...document.querySelectorAll<HTMLElement>('#rail i')];

  const BEATS: [number, number][] = [];
  steps.forEach((n, i) => { for (let k = 1; k <= n; k++) BEATS.push([i, k]); });
  const FIRST = steps.map((_, i) => BEATS.findIndex((b) => b[0] === i));

  const vh = () => stage.clientHeight || innerHeight;
  const sizeStory = () => { story.style.height = ((BEATS.length - 1) * B() + 1) * vh() + 'px'; };

  /* ---------- one scroll, one step ---------- */
  let tween = 0, goal = 0, lastWheel = 0;
  const stopAt = (k: number) => story.offsetTop + k * B() * vh();
  const inStory = () => scrollY > story.offsetTop - 2 && scrollY < stopAt(BEATS.length - 1) + 2;
  const stopFor = (dir: number) => {
    const b = BEATS.map((_, k) => stopAt(k)), y = tween ? goal : scrollY;
    if (dir > 0) return b.find((v) => v > y + 2);
    for (let i = b.length - 1; i >= 0; i--) if (b[i] < y - 2) return b[i];
    return undefined;
  };
  function tweenTo(y: number) {
    cancelAnimationFrame(tween); tween = 0; goal = y;
    const from = scrollY, d = y - from, H = vh();
    if (reduced() || Math.abs(d) < 2) { scrollTo({ top: y, behavior: 'instant' }); return; }
    const dur = 420 + 380 * Math.min(2, Math.abs(d) / H), t0 = performance.now();
    document.documentElement.classList.add('tween');
    const run = (now: number) => {
      const k = Math.min(1, (now - t0) / dur), e = k < 0.5 ? 2 * k * k : 1 - Math.pow(-2 * k + 2, 2) / 2;
      scrollTo({ top: from + d * e, behavior: 'instant' });
      if (k < 1) tween = requestAnimationFrame(run);
      else { tween = 0; document.documentElement.classList.remove('tween'); }
    };
    tween = requestAnimationFrame(run);
  }
  const stepCh = (dir: number) => { const y = stopFor(dir); if (y === undefined) return false; tweenTo(y); return true; };

  // Anything with data-go="<chapter>" jumps to that chapter: the hero button, the chapter list, the rail.
  document.addEventListener('click', (e) => {
    const b = (e.target as Element).closest<HTMLElement>('[data-go]');
    if (b) tweenTo(stopAt(FIRST[+b.dataset.go!]));
  }, { signal: ac.signal });

  on('wheel', (e) => {
    if (!inStory() || e.ctrlKey) return;
    if (Math.abs(e.deltaY) < 6 || Math.abs(e.deltaY) < Math.abs(e.deltaX)) return;
    const dir = Math.sign(e.deltaY);
    if (stopFor(dir) === undefined) return;            // past the last step: scroll the page normally
    e.preventDefault();
    const now = performance.now(), fresh = now - lastWheel > 160; lastWheel = now;
    if (fresh) stepCh(dir);                             // a new scroll always moves, even mid-move; trackpad glide doesn't
  }, { passive: false });

  // One swipe, one step: inside the tour the finger doesn't scroll the page; the swipe's direction picks the next step.
  let ty: number | null = null, tdir = 0, held = false, touching = false;
  on('touchstart', (e) => { touching = true; if (e.touches.length !== 1) { ty = null; return; } ty = e.touches[0].clientY; tdir = 0; held = false; }, { passive: true });
  on('touchmove', (e) => {
    if (ty === null || !inStory()) return;
    const dy = ty - e.touches[0].clientY;
    if (!tdir) { if (Math.abs(dy) < 8) return; tdir = Math.sign(dy); held = stopFor(tdir) !== undefined; }
    if (held) e.preventDefault();
  }, { passive: false });
  on('touchend', () => { touching = false; if (held && tdir) stepCh(tdir); ty = null; held = false; }, { passive: true });

  on('keydown', (e) => {
    if ((e.target as Element).closest?.('input,textarea,summary,video,button,a')) return;
    if (!inStory()) return;
    const next = ['ArrowRight', 'ArrowDown', 'PageDown', ' '].includes(e.key), prev = ['ArrowLeft', 'ArrowUp', 'PageUp'].includes(e.key);
    if (!next && !prev) return;
    e.preventDefault();
    if (!stepCh(next ? 1 : -1) && next) scrollBy({ top: vh() * 0.8, behavior: 'smooth' });
  });

  /* ---------- the phone scales to fit its column ---------- */
  const pbox = $('pbox'), dw = $('dw'), device = $('device');
  function fit() {
    const w = pbox.clientWidth, h = pbox.clientHeight;
    const S = Math.max(0.28, Math.min(1, h / 868, (w || 414) / 414));
    dw.style.width = 414 * S + 'px'; dw.style.height = 868 * S + 'px'; device.style.transform = `scale(${S})`;
  }

  /* ---------- one frame: which chapter and step the scroll is on ---------- */
  const stepCls = (el: HTMLElement, s: number) => {
    for (let k = 1; k <= 4; k++) { el.classList.toggle('s' + k, k <= s); el.classList.toggle('c' + k, k === s); }
  };
  let last = '';
  function frame() {
    const k = clamp(Math.round((scrollY - story.offsetTop) / (B() * vh())), 0, BEATS.length - 1);
    const [ci, s] = BEATS[k];
    $('hint').classList.toggle('on', k === 0 && scrollY < story.offsetTop + 8);
    railFills.forEach((f, i) => { f.style.transform = `scaleX(${i < ci ? 1 : i === ci ? s / steps[ci] : 0})`; });
    const key = ci + ':' + s; if (key === last) return; last = key;
    // Load screens one chapter ahead (and anything you jumped past), so the page doesn't fetch them all up front.
    layers.slice(0, ci + 2).forEach((L) => L.querySelectorAll<HTMLImageElement>('img[data-src]').forEach((img) => {
      img.src = img.dataset.src!; img.removeAttribute('data-src');
    }));
    panels.forEach((p, i) => { p.classList.toggle('on', i === ci); p.classList.toggle('past', i < ci); p.setAttribute('aria-hidden', i === ci ? 'false' : 'true'); });
    // Chapters behind you stay finished; ones ahead show their first screen while they fade in.
    [layers, cards].forEach((list) => list.forEach((el, i) => { el.classList.toggle('on', i === ci); stepCls(el, i === ci ? s : i < ci ? steps[i] : 1); }));
    tix.forEach((b, i) => { b.classList.toggle('on', i === ci); if (i === ci) b.setAttribute('aria-current', 'step'); else b.removeAttribute('aria-current'); });
    // Light status bar over the dark screens (Lock Screen, Siri).
    const dark = !!layers[ci].querySelector('.v' + s + '.dk');
    $('sbar').classList.toggle('lt', dark); $('hbar').classList.toggle('lt', dark);
  }

  let raf = 0;
  // A fling or a drag that crosses into the tour from outside lands on its first (or last) step.
  // Menu links (#get, #faq…) scroll straight through, so they're left alone for a moment.
  let navUntil = 0;
  document.addEventListener('click', (e) => { if ((e.target as Element).closest('a[href*="#"]')) navUntil = performance.now() + 2500; }, { signal: ac.signal });
  // Stopping the page's own momentum first (overflow hidden for two frames) keeps it from carrying on past the step.
  const land = (y: number) => {
    const html = document.documentElement;
    html.style.overflow = 'hidden';
    scrollTo({ top: y, behavior: 'instant' });
    requestAnimationFrame(() => requestAnimationFrame(() => { html.style.overflow = ''; scrollTo({ top: y, behavior: 'instant' }); }));
  };
  let prevY = scrollY;
  on('scroll', () => {
    const y = scrollY, first = stopAt(0), lastStop = stopAt(BEATS.length - 1);
    if (!tween && !touching && performance.now() > navUntil) {
      if (prevY < first + 2 && y > first + 2 && y < lastStop) land(first);
      else if (prevY > lastStop - 2 && y < lastStop - 2 && y > first) land(lastStop);
    }
    prevY = y;
    if (!raf) raf = requestAnimationFrame(() => { raf = 0; frame(); });
  }, { passive: true });
  on('resize', () => { sizeStory(); fit(); last = ''; frame(); });
  // On phones the card under the phone changes height between chapters: refit the phone to the space left.
  const ro = new ResizeObserver(() => fit());
  ro.observe(pbox);
  sizeStory(); fit(); frame();
  document.fonts?.ready.then(() => { if (!ac.signal.aborted) { fit(); frame(); } });

  return () => {
    ac.abort(); ro.disconnect();
    cancelAnimationFrame(tween); cancelAnimationFrame(raf);
    document.documentElement.classList.remove('tween');
    story.style.height = '';
  };
}
