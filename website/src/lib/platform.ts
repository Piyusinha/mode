/** Which Mode Setup fits this computer. Phones and tablets get null: Mode Setup runs on a computer. */
export type Platform = 'mac' | 'windows' | 'linux';

export const PLATFORMS: Record<Platform, { label: string; href: string }> = {
  mac: { label: 'Mac', href: '/download/mac' },
  windows: { label: 'Windows', href: '/download/windows' },
  linux: { label: 'Linux', href: '/download/linux' },
};

export function detectPlatform(): Platform | null {
  if (typeof navigator === 'undefined') return 'mac';
  const ua = navigator.userAgent;
  const nav = navigator as Navigator & { userAgentData?: { platform?: string; mobile?: boolean } };
  const p = nav.userAgentData?.platform || navigator.platform || '';
  if (nav.userAgentData?.mobile || /iPhone|iPad|iPod|Android/i.test(ua)) return null;
  if (/Mac/i.test(p) && navigator.maxTouchPoints > 1) return null;   // iPadOS reports itself as a Mac
  if (/Win/i.test(p) || /Windows/i.test(ua)) return 'windows';
  if (/Mac/i.test(p) || /Mac OS X/i.test(ua)) return 'mac';
  if (/Linux|X11|CrOS/i.test(p + ' ' + ua)) return 'linux';
  return 'mac';
}

/** The other two computers, in a fixed order, for "also for …" links. */
export const others = (p: Platform | null) => (['mac', 'windows', 'linux'] as Platform[]).filter((x) => x !== p);
