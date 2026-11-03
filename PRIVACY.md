# Privacy

Mode has no server and no account of its own. Its authors receive nothing from
you: no tasks, screenshots, voice, usage data or crash reports.

## What stays on your iPhone

- Your tasks, chats, task history and the screenshots of each run.
- What Mode remembers (about you, people, places, how you like things done).
  See and delete it in Memory; Settings → Reset Mode deletes everything.
- Your name and settings.
- Timing and error records (local OpenTelemetry files, used for diagnosing slow
  or failed tasks). They never leave the phone unless you copy them yourself.
- Keys and sign-ins (OpenRouter key, ChatGPT and Claude sign-ins, voice keys),
  in the iPhone’s Keychain, readable only by Mode on this device.

## What leaves your iPhone, and to whom

| When | What | Sent to |
|---|---|---|
| A task runs | Your task, screenshots of the screen and text read from it, earlier messages in that chat, and memory relevant to the task | The AI provider you chose: OpenAI (ChatGPT), Anthropic (Claude) or OpenRouter and the model maker you pick there |
| You talk to Mode with a voice provider other than Apple | Your recorded words | Deepgram or Soniox, if you chose one |
| Mode speaks with a voice provider other than Apple | Mode’s reply text | ElevenLabs or Fish Audio, if you chose one |
| You connect a provider | Your key or sign-in | That provider only |

Apple’s voice and listening run on the iPhone. Each provider handles your data
under its own privacy policy. Tasks marked private use and save no memory.

Mode looks at the screen only while a task runs. It is told never to type a
password, passcode or card number: when one is needed, it waits for you to
enter it.
Before memory is saved, Mode filters out things that look like keys, passwords
and card numbers.

## Mode Setup (the computer app)

- Your Apple account password goes only to Apple.
- A sign-in helper (an “anisette” server run by the iLoader or SideStore
  open-source projects) tells Apple which computer you are signing in from. It
  never sees your password. You can switch helpers in Settings.
- Your Apple password is kept in the computer’s keychain only if you choose to.
- Mode Setup checks GitHub for new releases of Mode.
- The pairing file it makes for your iPhone stays on the iPhone and on that
  computer.

## Questions

Open an issue at https://github.com/Piyusinha/mode/issues.
