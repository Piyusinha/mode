import { useEffect, useState } from 'react';

/* The repo's GitHub star count, shared by every star button on the page.
   It loads once, and again (fresh) whenever you come back to the tab, so a star you just gave on GitHub shows up. */
let stars: number | null = null;
const listeners = new Set<(n: number | null) => void>();
let started = false;

async function load(fresh: boolean) {
  try {
    const r = await fetch(fresh ? '/api/stars?fresh' : '/api/stars', { cache: 'no-store' });
    const j = r.ok ? await r.json() : null;
    if (typeof j?.stars === 'number') { stars = j.stars; listeners.forEach((f) => f(stars)); }
  } catch { /* keep the last count */ }
}

function start() {
  if (started) return;
  started = true;
  load(false);
  document.addEventListener('visibilitychange', () => { if (!document.hidden) load(true); });
}

export function useStars(): number | null {
  const [n, setN] = useState<number | null>(stars);
  useEffect(() => {
    listeners.add(setN); start();
    return () => { listeners.delete(setN); };
  }, []);
  return n;
}

export const formatStars = (n: number) => (n >= 1000 ? (n / 1000).toFixed(n >= 10000 ? 0 : 1).replace(/\.0$/, '') + 'k' : String(n));
