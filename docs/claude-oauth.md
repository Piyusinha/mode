# Direct Claude OAuth in Mode

Mode adapts the native OAuth/Messages protocol from
[finnvoor/OAuthLanguageModels](https://github.com/finnvoor/OAuthLanguageModels)
(CC0 1.0). Requests run directly from the iPhone through Ktor. There is no
proxy, embedded Claude Code runtime, or always-on Mac dependency.

## Code boundaries

- `engine/llm/ClaudeOAuth`: authorization-code PKCE and refresh protocol;
  validates state and login lifetime, never includes token responses in errors.
- `engine/llm/ClaudeOAuthClient`: native Messages request/response translation,
  image blocks, tool calls, prompt caching and one refresh attempt after 401.
- `engine/llm/ModelProvider` and `ProviderClients`: explicit provider identity
  and client registry. Provider selection is independent of model names.
- `feature/settings/data/ClaudeCredentialStore`: one Keychain entry for the
  credential bundle. Readiness is derived, never persisted separately.
- `feature/settings/data/ClaudeAccount`: login lifecycle, serialized refresh,
  rotated credentials, cancellation and sign-out races. No model preferences.
- `feature/settings/domain/ProviderModels`: provider defaults and model-ID rules.
- `app/screens/settings/ClaudeConnectionPresenter`: sign-in and connection-check
  state, separate from ordinary Settings. SwiftUI currently draws it in Settings;
  it can move to a separate screen without changing auth or transport.
- `engine/llm/ModelConnectionCheck`: reusable synthetic image/tool verification.

Settings retains independent main, strong and reflection model choices for each
provider. Existing installations default to OpenRouter and keep their key and
model selections. A task snapshots its provider in `AgentConfig`; planning,
steps and post-task reflection keep that provider if Settings changes mid-run.
An OAuth error never falls back to paid OpenRouter. Disconnecting Claude keeps
Claude selected but unavailable until the user explicitly switches providers.

Keychain writes update an existing item instead of deleting it first; failures
are surfaced, so a failed credential rotation does not silently erase old data.

## Install and test

Build the normal paid Mode app from the main worktree:

```sh
tools/gen-project.sh paid
xcodebuild -project iosApp/Mode.xcodeproj -scheme Mode \
  -destination 'generic/platform=iOS' -derivedDataPath build/xcode \
  -allowProvisioningUpdates build
```

The bundle remains `com.piyush.mode`, with normal onboarding, phone pairing,
widgets and VPN. The separate test-app identity and onboarding bypass remain
only in the earlier experiment worktree. Its credentials are not copied into
Mode: sign in again from the main app's Settings.

1. In Settings, tap **Sign in with Claude**.
2. Finish in the browser, copy the entire `code#state`, and paste it back in Mode.
3. Tap **Connect account**. Successful sign-in selects Claude.
4. Tap **Test Claude connection**. Only a generated JPEG showing `42` is sent;
   the check validates both the observed number and `connection_check` tool call.
5. Try a simple phone task, such as opening Settings, and inspect its recorded
   provider in the run log. Return to Settings to switch back to OpenRouter.
6. Disconnect Claude and ensure tasks request sign-in rather than use OpenRouter.

## Billing and limitations

This unofficial transport uses the compatibility identity of the reference
library. Authentication success and a passing image/tool check do not establish
which billing allowance Anthropic used. Requests can be refused or consume paid
extra usage. Compare Claude Settings > Usage before/after an isolated check,
with paid extra usage disabled, to investigate included allowance consumption.
Small checks may not move rounded usage percentages; no visible change is
inconclusive.

The endpoint reports token counts but not currency costs. `Usage.costReported`
and `AgentResult.costReported` distinguish unknown cost from zero. Run logs and
task history store unreported currency as null, including after reflection.
Dollar limits apply only where costs are reported; step/time limits still apply.

Mock tests cover protocol translation, refresh/state validation, concurrent
refresh and sign-out, provider selection persistence, absence of paid fallback,
image/tool checks, and provider isolation across a task and reflection. They do
not establish live acceptance or billing. Physical-device testing is required
before committing or pushing this change.

References:
- [Reference implementation](https://github.com/finnvoor/OAuthLanguageModels)
- [Reference license](https://github.com/finnvoor/OAuthLanguageModels/blob/main/LICENSE)
- [Anthropic authentication rules](https://code.claude.com/docs/en/legal-and-compliance)
