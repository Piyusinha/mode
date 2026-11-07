/* "Download Mode installer for Mac / Windows / Linux": the installer for this visitor's computer, straight from heymode.app.
   Phones and tablets get "Get Mode", which scrolls to the instructions (Mode Setup runs on a computer). */
import { useMemo } from 'react';
import { detectPlatform, PLATFORMS, type Platform } from '../lib/platform';
import { AppleLogo, DownloadIcon, WindowsLogo } from './Icons';

export const PlatformIcon = ({ p }: { p: Platform }) => (p === 'mac' ? <AppleLogo /> : p === 'windows' ? <WindowsLogo /> : <DownloadIcon />);

export function DownloadButton({ className = 'btn ink', phoneLabel = 'Get Mode' }: { className?: string; phoneLabel?: string }) {
  const p = useMemo(detectPlatform, []);
  if (!p) return <a className={className} href="/#get">{phoneLabel}</a>;
  return <a className={`${className} dl`} href={PLATFORMS[p].href}><PlatformIcon p={p} />Download Mode installer for {PLATFORMS[p].label}</a>;
}
