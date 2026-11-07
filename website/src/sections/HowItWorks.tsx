import type { ReactNode } from 'react';
import { Words } from '../components/Words';

const Stroke = ({ children }: { children: ReactNode }) => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{children}</svg>
);

const STEPS: { icon: ReactNode; title: string; text: string }[] = [
  { icon: <Stroke><path d="M12 3a3 3 0 0 1 3 3v6a3 3 0 0 1-6 0V6a3 3 0 0 1 3-3z" /><path d="M19 11a7 7 0 0 1-14 0M12 18v3" /></Stroke>,
    title: 'You ask', text: 'Through Siri, the Action Button, or the Mode app itself. In your words, in English or Hindi.' },
  { icon: <Stroke><path d="M2 12s3.5-6 10-6 10 6 10 6-3.5 6-10 6S2 12 2 12z" /><circle cx="12" cy="12" r="3" /></Stroke>,
    title: 'Mode looks', text: "It reads what's on screen the way you do: buttons, labels, text fields, lists. Any app, no special support needed." },
  { icon: <Stroke><path d="M12 2l1.8 5.2L19 9l-5.2 1.8L12 16l-1.8-5.2L5 9l5.2-1.8z" /><path d="M19 15l.9 2.1L22 18l-2.1.9L19 21l-.9-2.1L16 18l2.1-.9z" /></Stroke>,
    title: 'Your AI decides', text: 'The screen and your request go to the ChatGPT, Claude or OpenRouter key you chose, and nowhere else. One step at a time.' },
  { icon: <Stroke><path d="M9 11V5a2 2 0 1 1 4 0v6" /><path d="M13 10a2 2 0 1 1 4 0v2a2 2 0 1 1 4 0v3a6 6 0 0 1-6 6h-2a6 6 0 0 1-5-2.7L5 14a2 2 0 0 1 3.3-2.3L9 13" /></Stroke>,
    title: 'Mode taps, types, checks', text: 'On your screen, where you can see it, with every step in your Live Activity. It only says done when it can see it worked.' },
];

export function HowItWorks() {
  return (
    <section className="sec wrap" id="how" aria-labelledby="how-h">
      <p className="kick rv">Under the hood</p>
      <Words id="how-h">No plugins. No per-app setup. <em>It just looks.</em></Words>
      <p className="lead rv">Mode doesn't need every app to support it. It uses your phone the way you do, on the screen in front of you: look, decide, tap.</p>
      <ol className="steps">
        {STEPS.map((s) => <li className="rv" key={s.title}><span className="ic">{s.icon}</span><h3>{s.title}</h3><p>{s.text}</p></li>)}
      </ol>
      <div className="duo">
        <div className="tile rv">
          <h3>It works in front of you</h3>
          <p>Mode isn't a hidden robot in the background. It opens the app and taps on your screen, so you can watch every step, stop it from the top of the screen, or take over yourself.</p>
          <p>Look away if you like: it tells you out loud when it's done, and the result waits on the Lock Screen.</p>
        </div>
        <div className="tile rv">
          <h3>Bring the AI you already pay for</h3>
          <p>No new subscription, no per-task charge. Sign in once, and Mode uses your plan.</p>
          <div className="ai">
            <span><i style={{ background: '#10A37F' }} />ChatGPT</span><span><i style={{ background: '#D97757' }} />Claude</span><span><i style={{ background: '#6467F2' }} />OpenRouter key</span>
          </div>
          <p style={{ fontSize: 13, marginTop: 'auto' }}>Your chats and what Mode remembers stay on your phone. You can make it forget anything.</p>
        </div>
        <div className="tile dark rv">
          <h3>Set it up once <span style={{ fontFamily: 'var(--body)', fontSize: 13, fontWeight: 700, opacity: 0.6, letterSpacing: 0 }}>iPhone</span></h3>
          <ol>
            <li><b>1</b><span>Download the latest release on your Mac, Windows or Linux computer.</span></li>
            <li><b>2</b><span>Plug in your iPhone with a cable and install Mode. A free Apple account works.</span></li>
            <li><b>3</b><span>Mode renews every 7 days from your computer, or on its own with SideStore.</span></li>
          </ol>
          <div className="row"><a className="btn soft sm" href="/releases">Latest release</a><a className="btn soft sm" href="/#get">Setup guide</a></div>
        </div>
      </div>
    </section>
  );
}
