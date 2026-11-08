import { AndroidLogo, AppleLogo } from '../components/Icons';
import { DownloadButton } from '../components/DownloadButton';
import { Footer } from '../components/Footer';
import { StarButton } from '../components/StarButton';
import { Mo } from '../components/Mo';

export function Closing() {
  return (
    <section className="wrap" data-section="closing">
      <div className="cta rv">
        <Mo mood="done" className="mo" />
        <div style={{ flex: '1 1 340px' }}>
          <h2>Say it once. <em>Mode handles it.</em></h2>
          <p>Free, open source, and yours. Your phone, your AI, your business.</p>
        </div>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10 }}>
          <DownloadButton phoneLabel="Get Mode Setup" where="closing" />
          <StarButton className="btn soft star" label="Star on GitHub" where="closing" />
        </div>
        <div className="avail">
          <a className="ab ios" href="/#get" data-ev="nav_click" data-where="closing" data-target="get"><AppleLogo /><span><small>Available on</small>iOS</span></a>
          <span className="ab and" aria-label="Android, coming soon"><AndroidLogo /><span><small>Coming soon</small>Android</span></span>
        </div>
      </div>

      <Footer />
    </section>
  );
}
