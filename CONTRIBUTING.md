# Contributing to Mode

Thanks for helping. Bug reports, ideas, fixes, docs and design work are all welcome.

## Ways to help

- **Report a bug.** Open an issue with the *Bug report* form: what you asked Mode to do,
  what happened, your iPhone and iOS version, and the AI you use. If a task went wrong,
  Mode’s *What went wrong* page shows the steps; a screenshot of it helps a lot. Please
  remove anything private first.
- **Suggest something.** Use the *Feature request* form. Say what you were trying to do,
  not only the feature you want.
- **Send a change.** For anything bigger than a small fix, open an issue first so we can
  agree on the approach before you spend time on it.
- **Security issues:** please don’t open a public issue. See [SECURITY.md](SECURITY.md).

## Getting set up

Follow [DEVELOPERS.md](DEVELOPERS.md) to build Mode and run the tests. Most of the logic
(the agent, memory, presenters) can be worked on and tested without an iPhone.

## The rules of the codebase

These keep Mode working for everyone. Pull requests are checked against them.

1. **No scripts for particular apps or tasks.** Mode must never contain code that says
   “in WhatsApp, tap here”. The AI decides every step; code only gives it eyes (the
   screen) and hands (tap, type, open). Improve the general harness instead.
2. **Logic in Kotlin, views dumb.** State and decisions live in Kotlin presenters in
   `app/screens`, with tests. SwiftUI views render state and forward events, nothing more.
3. **Design-system tokens only.** No literal colours, sizes, fonts or strings in screens:
   add a token or component to `iosApp/Packages/ModeDesignSystem`, and strings to
   `iosApp/Mode/Resources/L10n*.swift`. `tools/check-ui-literals.sh` enforces this.
4. **Respect the module layers.** `core → engine → feature → app`.
   `./gradlew checkModuleRules` must pass.
5. **Privacy first.** Keys and sign-ins only in the Keychain. Never log keys, passwords,
   message contents or screenshots outside the phone. Mode never stores or types the
   user’s passcode or card numbers.
6. **Plain words in the app.** No model names, costs, step counts or other jargon in what
   users see; say what happened and the result.
7. **Paid and free Apple accounts.** Anything touching install, signing or the VPN must
   work for both.

## Before you open a pull request

```sh
./gradlew jvmTest checkModuleRules
sh tools/check-ui-literals.sh
```

- Add or update tests for any logic you change.
- For UI changes, add screenshots in light and dark mode to the pull request.
- Keep commits small, with messages that say what changed in plain words.
- Fill in the pull request template.

## Code of conduct

Everyone taking part is expected to follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## License

By contributing, you agree that your contributions are licensed under the
[Apache License 2.0](LICENSE), the same as Mode.
