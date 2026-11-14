/* "Download Mode installer for Mac / Windows / Linux": the installer for this visitor's computer, straight from heymode.app.
   Android gets "Download Mode for Android": the app itself, no computer needed.
   iPhone and iPad get "Get Mode", which scrolls to the instructions (Mode Setup runs on a computer). */
import { useMemo } from 'react';
import { withVisitor } from '../lib/analytics';
import { ANDROID_HREF, detectPlatform, isAndroid, PLATFORMS, type Platform } from '../lib/platform';
import { AndroidLogo, AppleLogo, DownloadIcon, WindowsLogo } from './Icons';

export const PlatformIcon = ({ p }: { p: Platform }) => (p === 'mac' ? <AppleLogo /> : p === 'windows' ? <WindowsLogo /> : <DownloadIcon />);

export function DownloadButton({ className = 'btn ink', phoneLabel = 'Get Mode', where }: { className?: string; phoneLabel?: string; where: string }) {
  const p = useMemo(detectPlatform, []);
  const android = useMemo(isAndroid, []);
  if (android) return <a className={`${className} dl`} href={withVisitor(ANDROID_HREF, where)} aria-label="Download Mode for Android (APK)" data-ev="download_click" data-where={where} data-target="android" data-detail="detected-android"><AndroidLogo />Download Mode for Android</a>;
  if (!p) return <a className={className} href="/#get" data-ev="get_mode_click" data-where={where} data-target="phone">{phoneLabel}</a>;
  return <a className={`${className} dl`} href={withVisitor(PLATFORMS[p].href, where)} data-ev="download_click" data-where={where} data-target={p} data-detail={`detected-${p}`}><PlatformIcon p={p} />Download Mode installer for {PLATFORMS[p].label}</a>;
}
