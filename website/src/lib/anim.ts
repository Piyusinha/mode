export const clamp = (x: number, a = 0, b = 1) => Math.min(b, Math.max(a, x));
/** Progress of t through [a, b], clamped to 0–1. */
export const seg = (t: number, a: number, b: number) => clamp((t - a) / (b - a));
export const ease = (x: number) => (x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2);
export const lerp = (a: number, b: number, k: number) => a + (b - a) * k;
export const reduced = () => matchMedia('(prefers-reduced-motion: reduce)').matches;
