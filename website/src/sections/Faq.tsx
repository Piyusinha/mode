import { Words } from '../components/Words';
import { track } from '../lib/analytics';

const QA: [string, string, boolean?][] = [
  ['Is it safe to let Mode use my phone?', 'You see every step Mode takes as it happens, with a Stop button always there. Prefer to approve things? Switch to Ask first and it waits for your yes before it pays, books, orders or sends. Your passwords and PINs are only ever typed by you.', true],
  ['What does it cost?', 'Mode is free. It uses the ChatGPT or Claude plan you already have, with no extra charge per task. You can also bring an OpenRouter key if you prefer.'],
  ['Does it work in the background?', "On Android 14 and later, yes: background mode lets Mode work while you use other apps, on Wi-Fi. On iPhone, Mode works on your screen, the same way you would, so you can see what it does. Your phone is busy while it works, usually under a minute, and you can stop it or take over any time. Quick questions, like what's on your calendar, are answered without opening an app."],
  ['Does it get better over time?', 'Yes. It remembers your usual orders, which app you use for each person, and how to get around your apps, so later tasks take fewer words and fewer steps. You can see, edit and erase everything it knows.'],
  ['Does Mode see everything on my phone?', "Only while it's doing something you asked for. The current screen and your request go to the AI you chose, and nowhere else. Your chats and what Mode remembers stay on your phone, and you can make it forget anything."],
  ['What do I need?', 'On Android: a phone on Android 13 or later, no computer needed. On iPhone: iOS 27, and a Mac, Windows or Linux computer for a one-time setup with a cable. A free Apple account works: Mode renews every 7 days from your computer, or on its own with SideStore.'],
  ['Can I read the code, or help?', 'Yes. All of Mode is on GitHub under the Apache 2.0 licence. Issues, ideas and pull requests are welcome.'],
  ['Is there an Android version?', 'Yes. Mode for Android is a free download for Android 13 and later, no computer needed. Download it from this page or from GitHub. Background mode, where Mode works while you use other apps, needs Android 14 and Wi-Fi.', true],
];

export function Faq() {
  return (
    <section className="sec wrap" id="faq" data-section="faq" aria-labelledby="faq-h">
      <p className="kick rv">Questions</p>
      <Words id="faq-h">Fair questions, <em>straight answers.</em></Words>
      <div className="faq rv">
        {QA.map(([q, a, open]) => <details key={q} open={open} onToggle={(e) => { if ((e.target as HTMLDetailsElement).open && !open) track('faq_open', { t: q }); }}><summary>{q}</summary><p>{a}</p></details>)}
      </div>
    </section>
  );
}
