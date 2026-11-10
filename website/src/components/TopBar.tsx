import { StarButton } from './StarButton';
import { DownloadButton } from './DownloadButton';
import { Mo } from './Mo';

export function TopBar() {
  return (
    <header className="topbar">
      <a className="brand" href="/#top" aria-label="Mode" data-ev="nav_click" data-where="header" data-target="logo"><Mo className="m" />Mode</a>
      <span className="sp" />
      <nav className="links" aria-label="Sections">
        <a href="/#film" data-ev="nav_click" data-where="header" data-target="film">Watch</a><a href="/#story" data-ev="nav_click" data-where="header" data-target="tour">What it does</a><a href="/#get" data-ev="nav_click" data-where="header" data-target="get">Download</a>
        <a href="/#appstore" data-ev="nav_click" data-where="header" data-target="appstore">Why not the App Store?</a><a href="/#faq" data-ev="nav_click" data-where="header" data-target="faq">Questions</a>
      </nav>
      <StarButton where="header" />
      <DownloadButton className="btn ink sm" where="header" />
    </header>
  );
}
