import { StarButton } from './StarButton';
import { DownloadButton } from './DownloadButton';
import { Mo } from './Mo';

export function TopBar() {
  return (
    <header className="topbar">
      <a className="brand" href="/#top" aria-label="Mode"><Mo className="m" />Mode</a>
      <span className="sp" />
      <nav className="links" aria-label="Sections">
        <a href="/#film">Watch</a><a href="/#story">What it does</a><a href="/#get">Download</a>
        <a href="/#appstore">Why not the App Store?</a><a href="/#faq">Questions</a>
      </nav>
      <StarButton />
      <DownloadButton className="btn ink sm" />
    </header>
  );
}
