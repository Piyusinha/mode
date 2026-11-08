import { Words } from '../components/Words';

const IDEAS = [
  ["Text Mom I'll call her after dinner", 'WhatsApp · sends it'],
  ['Book a cab to the office', 'Uber · books it'],
  ['Order my usual from Swiggy', 'Swiggy · orders and pays'],
  ['Find my flight back to Bangalore', 'Gmail · just finds it'],
  ["What's on my calendar today?", 'Calendar · just answers'],
  ['Turn off Dark Mode', 'Settings · tells you after'],
];

export function TrySaying() {
  return (
    <section className="sec wrap" id="try" data-section="try" aria-labelledby="try-h">
      <p className="kick rv">Try saying</p>
      <Words id="try-h">Start with the things <em>you keep putting off.</em></Words>
      <ul className="say-grid">
        {IDEAS.map(([q, how]) => <li className="rv" key={q}><q>{q}</q><span><i />{how}</span></li>)}
      </ul>
      <div className="legend rv">
        <span><i style={{ background: 'var(--coral)' }} />With Full access, Mode does the whole task</span>
        <span><i style={{ background: '#3D63F0' }} />Switch to Ask first and it checks with you before it pays, books, orders or sends</span>
      </div>
    </section>
  );
}
