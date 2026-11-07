import { REPO } from '../links';
import { Mo } from './Mo';

/** The footer on every page. Privacy, licence and releases live on heymode.app; only GitHub goes to GitHub. */
export function Footer() {
  return (
    <footer className="foot">
      <a className="brand" href="/" style={{ fontSize: 18 }}><Mo mood="sleep" className="m" style={{ width: 26, height: 26 }} />Mode</a>
      <nav aria-label="Links">
        <a href="/privacy">Privacy</a><a href="/license">Licence</a><a href="/releases">Releases</a>
        <a href={REPO} target="_blank" rel="noopener">GitHub</a>
      </nav>
      <p>Made in Bengaluru. Mode is not affiliated with Apple, OpenAI, Anthropic, WhatsApp or any app shown. iPhone and iOS are trademarks of Apple Inc. Android is a trademark of Google LLC.</p>
    </footer>
  );
}
