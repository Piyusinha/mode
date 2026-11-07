import license from '../../../LICENSE?raw';
import { REPO } from '../links';
import { Page } from './Page';

export function License() {
  return (
    <Page kick="Licence" title={<>Apache 2.0. <em>Yours to use.</em></>}>
      <div className="md">
        <p>Mode is open source under the Apache License 2.0. In short:</p>
        <ul>
          <li><b>Use it</b> for anything, personal or commercial, for free.</li>
          <li><b>Change it and share it</b>, as long as you keep the licence and say what you changed.</li>
          <li><b>Patent grant:</b> everyone who contributes gives you a licence to any of their patents the code uses.</li>
          <li><b>No warranty:</b> it's provided as is.</li>
        </ul>
        <p>This summary isn't the licence; the full text below is. The code is on <a href={REPO} target="_blank" rel="noopener">GitHub</a>.</p>
        <pre className="licence">{license}</pre>
      </div>
    </Page>
  );
}
