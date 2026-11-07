import { Words } from '../components/Words';

const MORE = [
  ['Health and repair', 'Mode checks each part on this iPhone, says what needs attention, and repairs what it can by itself.'],
  ['Backup and restore', 'Everything Mode has, in one file you keep. Restore it here or on a new iPhone.'],
  ['Measured on your iPhone', 'Mode runs the same short screen test on each model and picks the fastest and most accurate for your plan.'],
  ['One conversation', "When one thing's done, Mo asks what's next. As many tasks as you like, in one go."],
];

export function More() {
  return (
    <section className="sec wrap" id="more" aria-labelledby="more-h">
      <p className="kick rv">And the small things</p>
      <Words id="more-h">Built to be <em>lived with.</em></Words>
      <ul className="minis">
        {MORE.map(([title, text]) => <li className="rv" key={title}><b>{title}</b><span>{text}</span></li>)}
      </ul>
    </section>
  );
}
