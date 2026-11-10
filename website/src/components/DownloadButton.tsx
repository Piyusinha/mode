/* "Download Mode installer for Mac / Windows / Linux": the installer for this visitor's computer, straight from heymode.app.
   Phones and tablets get "Get Mode", which scrolls to the instructions (Mode Setup runs on a computer). */
import { useMemo } from 'react';
import { withVisitor } from '../lib/analytics';
import { detectPlatform, PLATFORMS, type Platform } from '../lib/platform';
import { AppleLogo, DownloadIcon, WindowsLogo } from './Icons';

export const PlatformIcon = ({ p }: { p: Platform }) => (p === 'mac' ? <AppleLogo /> : p === 'windows' ? <WindowsLogo /> : <DownloadIcon />);

export function DownloadButton({ className = 'btn ink', phoneLabel = 'Get Mode', where }: { className?: string; phoneLabel?: string; where: string }) {
  const p = useMemo(detectPlatform, []);
  if (!p) return <a className={className} href="/#get" data-ev="get_mode_click" data-where={where} data-target="phone">{phoneLabel}</a>;
  return <a className={`${className} dl`} href={withVisitor(PLATFORMS[p].href, where)} data-ev="download_click" data-where={where} data-target={p} data-detail={`detected-${p}`}><PlatformIcon p={p} />Download Mode installer for {PLATFORMS[p].label}</a>;
}
