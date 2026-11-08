// Sets the stats.heymode.app password. Asks for it twice (nothing shown on screen), turns it into a
// PBKDF2-SHA256 hash and stores only the hash as the Worker secret PASSWORD_HASH. The password itself
// is never saved, printed or sent anywhere.
//
//   npm run set-password
import { spawnSync } from 'node:child_process';
import { pbkdf2Sync, randomBytes } from 'node:crypto';

function ask(prompt) {
  return new Promise((resolve) => {
    const { stdin, stdout } = process;
    stdout.write(prompt);
    stdin.setRawMode(true); stdin.resume(); stdin.setEncoding('utf8');
    let value = '';
    const onData = (ch) => {
      if (ch === '\r' || ch === '\n' || ch === '\u0004') { stdin.setRawMode(false); stdin.pause(); stdin.off('data', onData); stdout.write('\n'); resolve(value); }
      else if (ch === '\u0003') { stdout.write('\n'); process.exit(1); }
      else if (ch === '\u007f') value = value.slice(0, -1);
      else value += ch;
    };
    stdin.on('data', onData);
  });
}

const b64u = (b) => b.toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
const a = await ask('New stats password: ');
const b = await ask('Same password again: ');
if (a !== b) { console.error('They don\'t match. Nothing changed.'); process.exit(1); }
if (a.length < 12) { console.error('Use at least 12 characters. Nothing changed.'); process.exit(1); }

const iterations = 100000; // the most Cloudflare Workers allow for PBKDF2
const salt = randomBytes(16);
const hash = `pbkdf2$${iterations}$${b64u(salt)}$${b64u(pbkdf2Sync(a, salt, iterations, 32, 'sha256'))}`;
const r = spawnSync('npx', ['--yes', 'wrangler@4', 'secret', 'put', 'PASSWORD_HASH'], { input: hash, stdio: ['pipe', 'inherit', 'inherit'] });
process.exit(r.status ?? 1);
