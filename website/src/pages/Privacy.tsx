import { marked } from 'marked';
import privacy from '../../../PRIVACY.md?raw';
import { Page } from './Page';

/** PRIVACY.md from the repo, shown on heymode.app (its own "# Privacy" heading is replaced by the page title). */
const html = marked.parse(privacy.replace(/^# .*\n/, ''), { async: false }) as string;

export function Privacy() {
  return (
    <Page kick="Privacy" title={<>No server. <em>No account.</em></>}>
      <div className="md" dangerouslySetInnerHTML={{ __html: html }} />
    </Page>
  );
}
