# Where the helper comes from

The helper follows the [scrcpy](https://github.com/Genymobile/scrcpy) server (Apache-2.0,
Copyright (C) 2018 Genymobile, Copyright (C) 2018-2026 Romain Vimont), which runs as the
shell user through `app_process` and makes a new virtual display for the apps it starts.

Taken from it, rewritten in Kotlin and cut to what Mode needs:

- `Workarounds`: an `ActivityThread` for the process and its system context.
- `FakeContext`: a context that is the shell package (`com.android.shell`), so framework
  services accept calls from this process.
- The virtual display's flags (`NewDisplayCapture`): public, presentation, own content
  only, touch, rotates with content, destroys its content when removed, and on Android 13+
  trusted, its own display group, always unlocked, no touch feedback, and on 14+ its own
  focus, so the hidden display never takes focus from the screen the user is on.

Not taken: video and audio capture, encoders, the control socket, clipboard, and input
injection. Mode reads and taps the hidden display through its accessibility service, which
accepts a display id; the helper only makes the display and starts apps on it.

The protocol is lines on standard input and output (Mode keeps one `exec:` stream open, so
there is no PTY echo and no socket to secure), listed in `Helper.kt`.
