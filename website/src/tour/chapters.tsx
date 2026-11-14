/* The feature tour: one chapter per thing Mode does, one real screen per scroll step.
   Screens are boards from the "Mode · Mo theme · all screens" canvas, rendered to public/screens/. */
import type { ReactNode } from 'react';
import { AndroidLogo, AppleLogo } from '../components/Icons';
import { Mo } from '../components/Mo';
import { withVisitor } from '../lib/analytics';
import { ANDROID_HREF } from '../lib/platform';

export type Screen = { file: string; alt: string; dark?: boolean };
export type Chapter = {
  name: string;          // short label for the chapter list
  kick: string;
  title: ReactNode;
  body: ReactNode;
  screens: Screen[];     // one per scroll step
  detail: ReactNode;     // the close-up card beside the phone; .a2/.b2 and .h1–.h4 react to the step
  darkCard?: boolean;
  extra?: ReactNode;
};

export const CHAPTERS: Chapter[] = [
  {
    name: 'Your AI', kick: 'Bring your own AI',
    title: <>Runs on the AI <em>you already pay for.</em></>,
    body: <>Sign in with your ChatGPT Plus or Pro plan and Mode runs on it, at no extra cost per task. Claude sign-in is experimental, or bring an OpenRouter key and pick any model.</>,
    screens: [
      { file: 'ProviderSheet', alt: 'Settings, AI providers: ChatGPT in use, Claude connected, OpenRouter to connect' },
      { file: 'ProviderDetail', alt: 'ChatGPT account: Mode on your plan this week, 186 steps, plan limit available' },
    ],
    detail: (
      <>
        <div className="D-h"><Mo className="mo" />Your AI</div>
        <div className="swap">
          <div className="b2">
            <h4>No new subscription.</h4>
            <div className="r2"><i className="pdot" style={{ background: '#10A37F' }} /><span><b>ChatGPT</b>Your Plus or Pro plan. No cost per task.</span></div>
            <div className="r2"><i className="pdot" style={{ background: '#D97757' }} /><span><b>Claude</b>Your Claude plan. Experimental for now.</span></div>
            <div className="r2"><i className="pdot" style={{ background: '#6467F2' }} /><span><b>OpenRouter</b>Pay as you go, any model. Usually under a cent a task.</span></div>
          </div>
          <div className="a2">
            <h4>See what Mode used.</h4>
            <p>Each provider shows Mode's use of your plan this week, today, and whether the plan limit is reached.</p>
            <div className="r2" style={{ marginTop: 12 }}><span><b>Your sign-ins stay on your iPhone</b>In its Keychain. They only go to that provider, never to us.</span></div>
          </div>
        </div>
      </>
    ),
  },
  {
    name: 'Learns you', kick: 'It learns you',
    title: <>It remembers <em>who and how.</em></>,
    body: <>Who you text where, your usual order, how you write. Every memory says where it came from, stays on this iPhone, and you can forget any of it.</>,
    screens: [
      { file: 'Memory', alt: 'Memory: About You, Things Mode Can Do, App Tips, kept only on this iPhone' },
      { file: 'MemoryAboutYou', alt: 'About You: Lucy is on WhatsApp, short Hinglish messages, your usual oats, home' },
    ],
    detail: (
      <>
        <div className="D-h"><Mo className="mo" />It learns you</div>
        <h4>Learned from what you do.</h4>
        <span className="bub2 you">Text Lucy I'm leaving in 10</span>
        <span className="bub2 mode">Sent to Lucy on WhatsApp.</span>
        <div className="lrn a2"><span className="kl">Remembered</span>Lucy is on WhatsApp. You chat in Hinglish.</div>
        <div className="a2" style={{ marginTop: 12 }}><span className="kl now">Next time</span><span className="bub2 you" style={{ marginTop: 4 }}>Text Lucy</span></div>
      </>
    ),
  },
  {
    name: 'Teach once', kick: 'Teach it as it works',
    title: <>Got it wrong? <em>Tell it once.</em></>,
    body: <>Speak up while Mode works and it changes course. Ask it to remember, or fix a step afterwards from the task's screens. Every lesson becomes a tip for that app.</>,
    screens: [
      { file: 'TeachSteer', alt: "While Mode works: you say it's not there any more, search Settings instead" },
      { file: 'TeachLearned', alt: 'Teach it for next time: saved, next time use Control Center for Dark Mode' },
      { file: 'TeachStep', alt: 'Fix a step afterwards: what should Mode have done, every time' },
      { file: 'TeachMemory', alt: 'Settings tips in Memory: the ones you taught and the ones Mode learned' },
    ],
    detail: (
      <>
        <div className="D-h"><Mo mood="working" className="mo" />Teach it</div>
        <div className="way h1"><b>Steer it while it works</b>“It's not there any more. Search for Dark Mode.” Mode changes course right away.</div>
        <div className="way h2"><b>Teach it for next time</b>Say it or type it, and Mode saves it for that app.</div>
        <div className="way h3"><b>Fix a step afterwards</b>Open the task's screens, pick the step, say what it should have done.</div>
        <div className="way h4"><b>See what you taught</b>Every tip lives in Memory. Edit it or delete it any time.</div>
      </>
    ),
  },
  {
    name: 'Quicker', kick: 'It gets quicker',
    title: <>Next time, it goes <em>straight there.</em></>,
    body: <>Routines from tasks that worked are kept, with how often they worked. Ask again and Mode skips the looking around, so it takes fewer steps.</>,
    screens: [
      { file: 'MemoryRoutines', alt: 'Things Mode Can Do: book a ride in Uber, worked 3 of 3 times; reply in WhatsApp, 5 of 5' },
      { file: 'MemoryApp', alt: 'MyFitnessPal tips Mode uses on every task there' },
    ],
    detail: (
      <>
        <div className="D-h"><Mo mood="working" className="mo" />It gets quicker</div>
        <div className="ft">
          <div><span className="kl">First time</span><b>Looks around the app to find its way</b><span className="big">7</span><small>steps · 37 s</small></div>
          <div><span className="kl now">Next time</span><b>Follows the routine that worked</b><span className="big">Fewer</span><small>steps, no looking around</small></div>
        </div>
        <p className="a2">Mode reads these before every task, so it gets things right the first time and takes fewer steps.</p>
      </>
    ),
  },
  {
    name: 'Hands-free', kick: 'Hands-free',
    title: <>Ask from <em>anywhere.</em></>,
    body: <>“Hey Siri, start Mode”, then just talk. Lock your phone if you like: Mode keeps listening on the Lock Screen, starts when you stop talking, and leaves the result there. Or use the Action Button, your Watch, or a Shortcut.</>,
    screens: [
      { file: 'Main', alt: 'Hey Siri, start Mode: Mode is listening', dark: true },
      { file: 'Listening', alt: "The Lock Screen listening: Text Lucy I'm leaving in 10, then tell me what's on my calendar", dark: true },
      { file: 'Working', alt: 'Working in WhatsApp, with every step in the Live Activity' },
      { file: 'Done', alt: "Done on the Lock Screen: sent to Lucy, and today's calendar", dark: true },
    ],
    detail: (
      <>
        <div className="D-h"><Mo mood="listening" className="mo" />Hands-free</div>
        <div className="way h1"><b>“Hey Siri, start Mode”</b>Built in. Nothing to set up.</div>
        <div className="way h2"><b>Keep talking, even locked</b>Mode listens on your Lock Screen and starts by itself when you stop talking.</div>
        <div className="way h3"><b>Watch it work</b>Each step in the Live Activity, with Stop always there.</div>
        <div className="way h4"><b>The answer waits for you</b>On the Lock Screen, out loud, with “Another task” one tap away.</div>
      </>
    ),
  },
  {
    name: 'Routines', kick: 'Make it a routine',
    title: <>Say it once. <em>It happens every day.</em></>,
    body: <>Tap Repeat on any task: read me today's calendar, at 8 every weekday. Your repeats sit in Tasks, next to what's running and what's waiting for you.</>,
    screens: [
      { file: 'Repeat', alt: "Repeat: read me today's calendar, at 8:00 on weekdays" },
      { file: 'Tasks', alt: 'Tasks: running, waiting for you, up next, and repeats' },
    ],
    detail: (
      <>
        <div className="D-h"><Mo className="mo" />Routines</div>
        <h4>Every weekday, at 8.</h4>
        <div className="notif"><Mo className="mo" /><span style={{ flex: 1 }}><small><span>Mode</span><span>8:00</span></small><p>Today: Biozyme with lunch at 14:00, then Shelcal at 21:00.</p></span></div>
        <p>Runs at 8:00 while Mode is open. If it isn't, you get a notification at 8: tap it and Mode does the task.</p>
      </>
    ),
  },
  {
    name: 'In charge', kick: "You're in charge",
    title: <>Your rules. <em>Your PIN.</em></>,
    body: <>Full access does the whole task. Ask first stops before it pays, orders or sends, and a word like “just do it” gives one task full access. Your PIN is always yours to type.</>,
    screens: [
      { file: 'AccessFull', alt: 'Access: full access, spending, one-time codes' },
      { file: 'AccessAsk', alt: 'Access: ask first, with words that give one task full access' },
      { file: 'LAYourTurn', alt: "Lock Screen: enter your UPI PIN in PhonePe, Mode carries on when you're done", dark: true },
    ],
    detail: (
      <>
        <div className="D-h"><Mo mood="working" className="mo" />You're in charge</div>
        <div className="lvcard">
          <div className="lv">
            <div className="top"><Mo mood="working" className="mo" /><span className="stp" aria-hidden="true"><i /></span></div>
            <div className="clk">Working · 0:21</div>
            <div className="ttl">Paying with your usual card</div>
            <div className="bars"><i className="f" /><i className="f" /><i className="n" /><i /></div>
            <div className="labs"><span>Open app</span><span>Your usual</span><span><b>Pay</b></span><span>Order</span></div>
          </div>
        </div>
        <p>Stop sits in the Live Activity on every task. Tap it, and Mode stops right there.</p>
      </>
    ),
  },
  {
    name: 'Private', kick: 'Private by design',
    title: <>No Mode server. <em>No Mode account.</em></>,
    body: <>Your chats and memory stay on your iPhone, your keys stay in its Keychain, and the screen only goes to the AI you chose. Back it all up into one file you keep, locked with your password.</>,
    screens: [
      { file: 'Backup', alt: 'Backup and restore: chats, what Mode knows, tasks, repeats, settings' },
      { file: 'BackupPassword', alt: 'Include your sign-ins: the whole backup is locked with a password you choose' },
    ],
    detail: (
      <>
        <div className="D-h"><Mo mood="sleep" className="mo" />Private</div>
        <h4>Two parties. Not three.</h4>
        <div className="flow"><span className="nd">Your iPhone<small>chats, memory, keys</small></span><i className="ar" /><span className="nd">Your AI<small>ChatGPT, Claude or OpenRouter</small></span></div>
        <div className="nope"><s>Mode server</s>There isn't one.</div>
        <div className="r2 a2"><span><b>Locked with your password</b>Mode can't get it back for you. Without it, the backup can't be opened.</span></div>
      </>
    ),
  },
  {
    name: 'Get Mode', kick: 'Get Mode',
    title: <>Stop tapping. <em>Start asking.</em></>,
    body: <>Free and open source. On Android, download it straight to your phone. On iPhone, Mode Setup on your computer installs it for you.</>,
    screens: [{ file: 'HomeTips', alt: 'Home: what can I do for you, with a tip' }],
    darkCard: true,
    detail: (
      <>
        <div className="D-h"><Mo mood="done" className="mo" />Get Mode</div>
        <h4>Mode Setup, for your Mac.</h4>
        <p>A small app that installs Mode on your iPhone. A few minutes, once.</p>
        <div className="avail">
          <a className="ab ios" href="#get"><AppleLogo /><span><small>Available on</small>iOS</span></a>
          <a className="ab and" href={withVisitor(ANDROID_HREF, 'tour')} data-ev="download_click" data-where="tour" data-target="android"><AndroidLogo /><span><small>Available on</small>Android</span></a>
        </div>
      </>
    ),
    extra: <div className="row"><a className="btn ink" href="#get">Download Mode Setup</a></div>,
  },
];
