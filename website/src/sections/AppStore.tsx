/* Why Mode isn't on the App Store: the question everyone asks before installing. */
import { Words } from '../components/Words';

const POINTS: [string, string][] = [
  ['Apple doesn’t allow it', 'App Store apps aren’t allowed to see or tap other apps. Using your apps for you is exactly what Mode does.'],
  ['So you install it yourself', 'Mode uses Apple’s developer tools on your own iPhone, the same ones Xcode uses to test apps. Mode Setup signs it with your own Apple account and installs it over a cable, once.'],
  ['Nothing is hidden', 'Every line of Mode is open source, so anyone can read exactly what it does. It has no server and no account of its own.'],
  ['Free account? Renew weekly', 'With a free Apple account, Apple asks for apps installed this way to be re-signed every 7 days. Mode Setup or SideStore does it for you. A paid developer account lasts a year.'],
];

export function AppStore() {
  return (
    <section className="sec wrap" id="appstore" aria-labelledby="appstore-h">
      <p className="kick rv">Before you install</p>
      <Words id="appstore-h">Why isn’t Mode <em>on the App Store?</em></Words>
      <ol className="why">
        {POINTS.map(([t, d], i) => <li className="rv" key={t}><span className="n">{i + 1}</span><b>{t}</b><span>{d}</span></li>)}
      </ol>
      <p className="note rv">We’re working on an App Store version with the parts Apple does allow, like asking by voice or Siri, your calendar, reminders and contacts, and Shortcuts. Using your other apps on screen will stay in this version.</p>
    </section>
  );
}
