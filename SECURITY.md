# Security

Mode controls an iPhone and holds AI and voice provider credentials, so
security reports are welcome and handled first.

## Reporting a vulnerability

Please don’t open a public issue. Use GitHub’s private
[Report a vulnerability](https://github.com/heymode/mode/security/advisories/new)
form with what you found, how to reproduce it, and its impact. You’ll get a reply
within a few days, and credit in the fix’s release notes if you want it.

## In scope

- Mode on the iPhone: the agent acting beyond the task, credentials leaving the
  Keychain, data sent somewhere other than the providers the user chose.
- Mode Setup: Apple account credentials, signing certificates, pairing files.
- The release files: Mode’s `.ipa` and Mode Setup downloads.

Weaknesses in Apple’s, the AI providers’ or the anisette helpers’ own services
are out of scope; report those to them.
