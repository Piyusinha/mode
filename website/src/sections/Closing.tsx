import { AndroidLogo, AppleLogo } from '../components/Icons';
import { DownloadButton } from '../components/DownloadButton';
import { Footer } from '../components/Footer';
import { StarButton } from '../components/StarButton';
import { Mo } from '../components/Mo';
import { withVisitor } from '../lib/analytics';
import { ANDROID_HREF } from '../lib/platform';

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
          <a className="ab and" href={withVisitor(ANDROID_HREF, 'closing')} data-ev="download_click" data-where="closing" data-target="android"><AndroidLogo /><span><small>Available on</small>Android</span></a>
        </div>
      </div>

      <Footer />
    </section>
  );
}
