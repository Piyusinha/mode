# Structured screen perception on iOS 26/27 without XCUITest (CoreDevice / RemoteXPC)

Scope note: "Mode" runs on the iPhone and drives the same iPhone over a loopback CoreDevice tunnel using jkcoxson/idevice at commit `89702099f4d7552f704f93b4526756abda319ceb`. That commit is also upstream HEAD as of 2026-10-27, checked with `git log` on a fresh clone. Research date: 2026-11-01.

**First-hand device test.** For this research I ran pymobiledevice3 11.49.1 against a USB-connected iPhone 17 Pro (iPhone18,1) on **iOS 27.2, build 24B119**, using its no-root userspace tunnel. The probe scripts are in this research session's scratchpad: `scripts/axprobe.py` and `scripts/axprobe2.py`. They are referred to below as "local measurement". The phone was **locked** during the test, so only lock-screen and SpringBoard elements were walked. Then the phone disconnected and no third-party app could be tested. Findings from these runs are marked [MEASURED]. They have no URL because they are my own runs.

---

## Q1. Can the accessibility audit service (axAuditDaemon) list on-screen elements with label, value, traits and frame for third-party apps over CoreDevice/RSD on iOS 17+? Does idevice implement it? How fast is it?

### Takeaway
Yes, partly, and it is the best structured source that needs no XCUITest. Over an iOS 17+ RSD tunnel, `com.apple.accessibility.axAuditDaemon.remoteserver.shim.remote` is a DTX service. It lets a host walk the foreground screen element by element, in VoiceOver order. For each element it returns:
- a VoiceOver-style caption that combines label, value and traits, for example "Flashlight, Off, Button, Not Enabled" or "Priority Notifications, Header"
- an opaque element handle
- reads of single attributes such as `Label` on the focused element
- an "Activate" (press) action on that handle

It does **not** return a tree, and it does **not** give per-element frames on real devices. Hit-testing by coordinate is simulator-only. The walk is fast, about **30–70 ms per element** [MEASURED]. idevice does **not** implement this service at the pinned commit. Its DTX `RemoteServerClient` and `rsd_checkin` are the building blocks for adding it.

### Cited Findings

**Service names and transport**
- pymobiledevice3 defines `SERVICE_NAME = "com.apple.accessibility.axAuditDaemon.remoteserver"` for lockdown (iOS 16 and older) and `RSD_SERVICE_NAME = "com.apple.accessibility.axAuditDaemon.remoteserver.shim.remote"` for RSD (iOS 17+). It speaks DTX and invokes selectors on the control (root) channel. — [pymobiledevice3 accessibilityaudit.py](https://github.com/doronz88/pymobiledevice3/blob/master/pymobiledevice3/services/accessibilityaudit.py), commit 5234545 (2026-10-31)
- The RSD service list shows `com.apple.accessibility.axAuditDaemon.remoteserver.shim.remote` with `"Entitlement": "com.apple.mobile.lockdown.remote.trusted"` and `"UsesRemoteXPC": false`. That is the same trust class as the other `.shim.remote` services, such as AFC. — [pymobiledevice3 misc/RemoteXPC.md](https://github.com/doronz88/pymobiledevice3/blob/master/misc/RemoteXPC.md)
- What changed in iOS 17: services moved from usbmuxd/lockdown over TLS to a tunnel with RemoteXPC/RSD. Each Apple device runs `remoted`, which exports services over the network, and USB shows up as an Ethernet-over-USB IPv6 interface. — [RemoteXPC.md](https://github.com/doronz88/pymobiledevice3/blob/master/misc/RemoteXPC.md)

**The API is a focus walk, not a tree dump**
- `iter_elements()` enables app monitoring and calls `deviceInspectorMoveWithOptions:` with direction Next (4). Other directions are Previous=3, First=5 and Last=6, with `includeContainers=1` and `allowNonAX=0`. Each step waits for a `hostInspectorCurrentElementChanged:` event carrying an `AXAuditInspectorFocus_v1` with three fields: `CaptionTextValue_v1`, `SpokenDescriptionValue_v1` and `ElementValue_v1`, an opaque `PlatformElementValue_v1` byte handle. The walk stops when an element repeats. — [accessibilityaudit.py](https://github.com/doronz88/pymobiledevice3/blob/master/pymobiledevice3/services/accessibilityaudit.py)
- In pymobiledevice3's words, `element_attribute()` (`deviceElement:valueForAttribute:`) "resolves only the element that currently holds the inspector focus … other elements read as None". The CLI reads the attributes `Label`, `Identifier` and `Value` by default (`pymobiledevice3 developer accessibility element-attributes`). — [accessibilityaudit.py](https://github.com/doronz88/pymobiledevice3/blob/master/pymobiledevice3/services/accessibilityaudit.py); [tests/cli/developer/test_accessibility_element_attributes.py](https://github.com/doronz88/pymobiledevice3/blob/master/tests/cli/developer/test_accessibility_element_attributes.py)
- Press action: `deviceElement:performAction:withValue:` with attribute `AXAction-2010` ("Activate"). pymobiledevice3's docstring says it "Can only be used for processes that carry the `task_for_pid-allow` entitlement". go-ios exposes the same call as `PerformAction(ActionTap, …)`. — [accessibilityaudit.py](https://github.com/doronz88/pymobiledevice3/blob/master/pymobiledevice3/services/accessibilityaudit.py); [go-ios ios/accessibility/accessibility_control.go](https://github.com/danielpaulus/go-ios/blob/main/ios/accessibility/accessibility_control.go) (`PerformAction`, around line 393)

**No frames and no hit-testing on real devices**
- go-ios maintainers tested on an iPhone 13 with iOS 18.4.1. The geometry attributes (`AXFrame`, `AXBounds`, `AXPosition`, `AXSize`) "return no value even for a live, focused element". `deviceFetchElementAtNormalizedDeviceCoordinate:` "returns an empty reply on devices", and the service advertises `deviceFetchResolvesElementsOnSimulator`, which means coordinate resolution is simulator-only. On real hardware an element's rect only arrives inside audit results (`ElementRectValue_v1`). — [go-ios ios/accessibility/ax_audit.go](https://github.com/danielpaulus/go-ios/blob/main/ios/accessibility/ax_audit.go) (comment block at top), commit 51887da (2026-10-30)
- Audit issues (`AXAuditIssue_v1`) carry `ElementRectValue_v1`, `IssueClassificationValue_v1` (contrast, hit region, element detection, sufficient description, dynamic text, text clipped), `FontSizeValue_v1` and `MLGeneratedDescriptionValue_v1`. These cover **flagged** elements only, not every element. — [accessibilityaudit.py](https://github.com/doronz88/pymobiledevice3/blob/master/pymobiledevice3/services/accessibilityaudit.py); [go-ios ax_rect.go](https://github.com/danielpaulus/go-ios/blob/main/ios/accessibility/ax_rect.go)

**[MEASURED] on iOS 27.2 (24B119), iPhone 17 Pro**
- **Availability:** the service works on iOS 27.2. `deviceApiVersion` = 26.
- **Advertised capabilities** include:
  - `deviceInspectorMoveWithOptions:`, `deviceElement:valueForAttribute:`, `deviceElement:valueForParameterizedAttribute:withObject:`, `deviceElement:setValue:attribute:`, `deviceElement:performAction:withValue:`
  - `deviceFetchElementAtNormalizedDeviceCoordinate:`, `deviceFetchSpecialElement:`
  - `deviceCaptureScreenshot`, `deviceRunningApplications`, `deviceSetAuditTargetPid:`
  - `deviceInspectorShowVisuals:`, `deviceInspectorShowIgnoredElements:`, `deviceInspectorAutodrillIntoElements:`, `deviceInspectorLockOnCurrentElement`
  - `deviceBeginAuditTypes:` and others
- **Timing:**
  - Userspace tunnel plus RSD connect took 146 ms. Opening the axAudit DTX connection took 312 ms.
  - A full walk of the 7 lock-screen elements took **332 ms**: 67, 58, 40, 32, 32, 32 and 40 ms per element.
  - The whole CLI command `pymobiledevice3 developer accessibility list-items`, including Python startup and the tunnel, took 0.94 s wall-clock.
- **Captions returned:** "19:51, Updates Frequently"; "Priority Notifications, Header"; "DEVICE HUB, 5 minutes ago, Notification, Button"; "ZOMATO, now, Zomato, Notification, Button"; "Flashlight, Off, Button, Not Enabled"; "Camera, Button"; "Show Notifications, Button". So label, value, role/trait and enabled state all come through as caption text.
- **Attribute reads:**
  - After `move_focus_and_wait(First)`, `Label` returned "19:52".
  - These names all returned None: `Value`, `Identifier`, `Traits`, `Frame`, `AXFrame`, `Bounds`, `Hint`, `Role`, `Focused`, `ElementRect`, `Rect`, and `AXAttribute-2001` through `AXAttribute-2024`.
  - Querying 30 attribute names took about 160 ms, roughly **5 ms per attribute call**.
  - When the walk had already wrapped around, every attribute read returned None, including `Label`.
- **Point lookup:** `deviceFetchElementAtNormalizedDeviceCoordinate:` rejected dicts, arrays and strings with `-[… CGPointValue]: unrecognized selector`, so it needs an NSValue-encoded CGPoint. I could not test whether it resolves anything on a device; go-ios reports that it returns empty.
- **`deviceFetchSpecialElement:`** returned element handles for args 0 and 1, and also for the strings "Focused" and "keyboard". These came back as the first-walked and last-walked elements, and their meaning is unknown.
- `deviceRunningApplications` returned `[]` and `deviceCurrentState` returned 0 while the phone was locked.

**idevice status**
- idevice at the pinned commit has **no accessibility-audit module**. `grep -ri axaudit` over the checkout finds nothing.
- The services it does have:
  - `dvt/` (`remote_server`, `screenshot`, `process_control`, `xctest`, …)
  - `core_device/` (`app_service`, `hid`, `screencaptureservices`, `display_stream`, `configuration_service`, `orientation_service`, `icon_service`, …)
  - `wda.rs` and `wda_bridge.rs`

  — [idevice/src/services @ 8970209](https://github.com/jkcoxson/idevice/tree/89702099f4d7552f704f93b4526756abda319ceb/idevice/src/services)
- Building blocks already in idevice:
  - `RemoteServerClient::new(stream)` (DTX over any `ReadWrite`)
  - `root_channel()` (channel 0, where axAudit selectors go)
  - `call_method(…, args: Option<Vec<AuxValue>>)` with `AuxValue::archived_value(plist::Value)` (NSKeyedArchiver args)
  - `read_message(0)` to receive device-to-host `host*` callbacks
  - `Idevice::rsd_checkin()`, which `.shim.remote` services need

  — [idevice/src/services/dvt/remote_server.rs](https://github.com/jkcoxson/idevice/blob/89702099f4d7552f704f93b4526756abda319ceb/idevice/src/services/dvt/remote_server.rs) (`root_channel` ~L291, `call_method` ~L784, `read_message` ~L828); [message.rs `archived_value` ~L447](https://github.com/jkcoxson/idevice/blob/89702099f4d7552f704f93b4526756abda319ceb/idevice/src/services/dvt/message.rs); [lib.rs `rsd_checkin` ~L313](https://github.com/jkcoxson/idevice/blob/89702099f4d7552f704f93b4526756abda319ceb/idevice/src/lib.rs)
- idevice's `RsdService` impl for `RemoteServerClient` hard-codes `com.apple.instruments.dtservicehub`. A wrapper type needs its own `rsd_service_name()` that returns the axAudit shim name and runs `rsd_checkin()` in `from_stream`, the same way `amfi.rs`, `misagent.rs` and `syslog_relay.rs` do for their shims. — [dvt/mod.rs](https://github.com/jkcoxson/idevice/blob/89702099f4d7552f704f93b4526756abda319ceb/idevice/src/services/dvt/mod.rs); [services/amfi.rs ~L147–151](https://github.com/jkcoxson/idevice/blob/89702099f4d7552f704f93b4526756abda319ceb/idevice/src/services/amfi.rs)
- **Reference ports to copy:**
  - pymobiledevice3 `accessibilityaudit.py`: the full selector set, the object envelopes `{"ObjectType": "AXAuditElement_v1", "Value": {"ObjectType": "passthrough", …}}`, and the 2 initial messages to flush on iOS 15+.
  - go-ios `ios/accessibility/accessibility_control.go`: it registers `hostInspectorCurrentElementChanged:`, `hostInspectorMonitoredEventTypeChanged:`, `hostAppStateChanged:` and `hostInspectorNotificationReceived:`, then calls `deviceInspectorSupportedEventTypes`, `deviceApiVersion` and `deviceSetAppMonitoringEnabled:`.

  — [pymobiledevice3](https://github.com/doronz88/pymobiledevice3/blob/master/pymobiledevice3/services/accessibilityaudit.py); [go-ios accessibility_control.go](https://github.com/danielpaulus/go-ios/blob/main/ios/accessibility/accessibility_control.go)

### Inferences
- **What an agent gets per screen.** A list of (caption, handle) pairs in reading order. This answers "is there a Back button / Add button / Search field here?" for icon-only controls, as long as the app labels them (the iOS default for system bar buttons and SF Symbols). With about 25–40 elements on a typical app screen, a full walk should take roughly **1–2.5 s** at 30–60 ms per element. This is extrapolated from the 7-element lock-screen walk, not measured on a third-party app.
- **Missing frames can partly be worked around:**
  1. **Press by handle.** Activate the element through `AXAction-2010` without coordinates. Whether this works from Mode's tunnel identity on third-party apps was not tested, and pymobiledevice3 warns about a `task_for_pid-allow` caveat. Mode's HID tap stays the fallback.
  2. **Match text to OCR boxes.** Match a caption's label text to Vision OCR boxes to get a position for text elements.
  3. **Place icons.** For icon elements, combine reading order with an icon detector's boxes (see Q4).
  4. **Highlight trick (speculative).** Turn on `deviceInspectorShowVisuals:` briefly and screenshot the focus highlight rectangle. It would show in screenshots and on screen, so it is a last resort.
- **Run it in parallel.** Mode could start the walk alongside the screenshot and OCR, then merge the results. Latency is dominated by the element count.
- **Risk:** VoiceOver-style focus movement can auto-scroll scroll views to reveal off-screen elements. It is unknown whether the inspector does this. Verify on a long list in a third-party app.

### Gaps
- Not tested on an unlocked third-party app such as Swiggy or MyFitnessPal. The phone disconnected. Unknown:
  - element counts, latency at scale, whether WebView/React Native/Flutter content appears
  - whether captions include "Text Field" and "Is editing"
  - whether `AXAction-2010` succeeds from a non-Xcode host
- The real attribute-name vocabulary for `deviceElement:valueForAttribute:` beyond `Label` was not found. The Mac `AccessibilityAudit.framework` binary is in the dyld shared cache and was not extracted.
- Whether opening the inspector session has visible on-device side effects (overlay, VoiceOver cursor, slower UI) when `ShowVisuals` is off is unknown. No overlay was reported in pymobiledevice3 or go-ios docs, but I did not observe the screen.
- The meaning and argument format of `deviceFetchSpecialElement:` are unknown.

---

## Q2. Other device services that expose the view hierarchy, focused element or keyboard state

### Takeaway
Nothing else gives a third-party app's element tree without an XCTest runner:
- **testmanagerd** snapshots (`_XCT_requestSnapshotForElement:…`) are what WDA, Appium and Maestro use. They come with an XCTest UI-test session, and that brings the "Automation Running" overlay.
- **ViewHierarchyAgent** (Xcode's view debugger) requires a debuggable, developer-signed process.
- **dtservicehub** instruments has no UI-tree channel.
- **CoreDevice / Device Hub** services cover screenshots, HID, apps, display streaming and appearance settings, but not UI structure.

### Cited Findings
- RSD services on the device include:
  - `com.apple.dt.testmanagerd.remote`
  - `com.apple.dt.testmanagerd.remote.automation` (`"Entitlement": "AppleInternal"`)
  - `com.apple.dt.ViewHierarchyAgent.remote` (`"Entitlement": "com.apple.private.dt.ViewHierarchyAgent.client"`, `UsesRemoteXPC: true`)
  - `com.apple.instruments.dtservicehub`
  - `com.apple.springboardservices.shim.remote`
  - `com.apple.webinspector.shim.remote`

  — [pymobiledevice3 misc/RemoteXPC.md](https://github.com/doronz88/pymobiledevice3/blob/master/misc/RemoteXPC.md); [go-ios ios/rsd_test.go](https://github.com/danielpaulus/go-ios/blob/main/ios/rsd_test.go)
- The XCTest manager protocol includes `_XCT_requestElementAtPoint:` and `_XCT_requestSnapshotForElement:attributes:parameters:reply:`. These are runner-to-daemon calls. — [appium/WebDriverAgent PrivateHeaders/XCTest/XCTestManager_ManagerInterface-Protocol.h](https://github.com/appium/WebDriverAgent/blob/master/PrivateHeaders/XCTest/XCTestManager_ManagerInterface-Protocol.h)
- I found no source showing testmanagerd answering those snapshot calls from a host that has not started a UI-test runner. A secondary write-up says testmanagerd gates cross-process AX reads, but its page now returns 404. — search result for kaeawc.github.io/auto-mobile testmanagerd design doc (unverifiable; treat as unconfirmed)
- On real devices with iOS/iPadOS 15+, an "Automation Running" overlay ("hold both volume buttons to stop") is shown while WDA/XCTest runs. Appium calls it an XCTest limitation, and it does not appear in screenshots. — [Appium XCUITest driver troubleshooting](https://appium.github.io/appium-xcuitest-driver/latest/guides/troubleshooting/)
- idevice CoreDevice services at the pinned commit:
  - HID features: `com.apple.coredevice.feature.remote.hid.button`, `…hid.keyboard`, `…hid.digitizer`, `…hid.scroll`, `…universalhidservice`, `com.apple.coredevice.hid.indigo`
  - `com.apple.coredevice.screencaptureservice`
  - `com.apple.coredevice.configuration` actions (`getuserinterfacestyle`, `setliquidglassconfiguration`, `getshowborders`/`setshowborders`, `setdevicetextsize`, `getreducemotion`, …)
  - `com.apple.coredevice.iconservice`, `com.apple.coredevice.devicecontrol`

  None of them return UI structure, focus or keyboard state. — [idevice core_device/hid.rs](https://github.com/jkcoxson/idevice/blob/89702099f4d7552f704f93b4526756abda319ceb/idevice/src/services/core_device/hid.rs); [configuration_service.rs](https://github.com/jkcoxson/idevice/blob/89702099f4d7552f704f93b4526756abda319ceb/idevice/src/services/core_device/configuration_service.rs); [screencaptureservices.rs](https://github.com/jkcoxson/idevice/blob/89702099f4d7552f704f93b4526756abda319ceb/idevice/src/services/core_device/screencaptureservices.rs)
- The axAudit daemon is the one non-XCTest service that publishes focus changes (`hostInspectorCurrentElementChanged:`), app state (`hostAppStateChanged:`) and inspector notifications (`hostInspectorNotificationReceived:`). go-ios logs the last two. — [go-ios accessibility_control.go](https://github.com/danielpaulus/go-ios/blob/main/ios/accessibility/accessibility_control.go) (`readhostAppStateChanged`, `readhostInspectorNotificationReceived`)
- The webinspector shim exposes the DOM of Safari and WKWebViews. pymobiledevice3 and go-ios ship webinspector commands, for example go-ios `cmd_device_webinspector.go`. — [go-ios repo](https://github.com/danielpaulus/go-ios) (file listing). Third-party app WKWebViews are generally only inspectable when the app sets `isInspectable`. That last part is my inference, not verified here.

### Inferences
- `ViewHierarchyAgent` is Xcode's "Debug View Hierarchy" agent. It almost certainly needs the target to be debuggable (`get-task-allow`), so App Store apps are excluded. I found no source for this.
- `hostInspectorNotificationReceived:` may carry UIAccessibility notifications such as screen-changed and layout-changed. That would make a cheap "screen changed, re-perceive now" signal. It is unverified because the payload was not captured.

### Gaps
- No public evidence for or against calling testmanagerd snapshot APIs directly over DTX without an XCTest runner, or for whether doing so shows the overlay.
- I found no write-up of the protocol or entitlement needs of `ViewHierarchyAgent.remote`.
- I found no iOS 27 Device Hub service that exposes UI elements. Other researchers or Apple's release notes should confirm.

---

## Q3. Keyboard detection: reliable ways to know the software keyboard is up and a field is focused

### Takeaway
No CoreDevice service reports keyboard state directly. The cheapest reliable signal comes from Mode's existing OCR: a keyboard shows as a dense grid of single-character tokens in the bottom ~40% of the screen, plus `space` and `return`/`search`/`go`/`done`. The axAudit walk should also reveal keyboard keys and, probably, a field's editing state. Treat both as pending verification. The simplest fix for the "tapped the search field 6 times" failure: after one tap on a field, type through CoreDevice HID keyboard events and check the result with OCR, rather than tapping again.

### Cited Findings
- I found no source for detecting the iOS keyboard from a screenshot. Existing methods are in-app UIKit notifications (`keyboardWillChangeFrameNotification`), VisualViewport for Safari, and Appium's `is_keyboard_shown`. A forum report says a class-name lookup for the keyboard works "only on Simulator". — [Appium forum: iOS - Determine Keyboard is Shown](https://discuss.appium.io/t/ios-determine-keyboard-is-shown/5651); [codemia keyboard detection](https://codemia.io/knowledge-hub/path/how_to_detect_when_keyboard_is_shown_and_hidden)
- idevice exposes a CoreDevice HID keyboard feature (`com.apple.coredevice.feature.remote.hid.keyboard`) next to digitizer, button and scroll. — [idevice core_device/hid.rs ~L588](https://github.com/jkcoxson/idevice/blob/89702099f4d7552f704f93b4526756abda319ceb/idevice/src/services/core_device/hid.rs)
- Apple's Screen Recognition taxonomy includes a "text field" class, so text fields can be detected visually. — [Apple ML Research: Screen Recognition](https://machinelearning.apple.com/research/mobile-applications-accessible)

### Inferences
- **OCR heuristic (pixel-only, near-zero cost):** if Vision OCR finds at least 8 one-character tokens, in at least 2 horizontal rows with regular spacing, inside the bottom 45% of the frame, plus one of {space, return, search, go, done, send, next}, then the keyboard is up. This covers QWERTY and should be robust with any keyboard language that shows Latin letters. Emoji and number pads need the second rule: a bottom-row "123"/"ABC"/globe pattern.
- **Focused-field inference:** keyboard up plus a caret or a placeholder change in a field near the top. Simpler: when the keyboard is up, assume the last tapped field is focused and **type**. Never re-tap a field while the keyboard is up.
- **axAudit signals:**
  - With the keyboard up, the VoiceOver order should include the key elements ("q", "w", …, "shift", "return"). The element count also jumps by about 30, which is easy to detect.
  - VoiceOver normally speaks "is editing" for the active text field, so the caption may contain it.
  - Both are unverified on device.
- **HID typing caveat:** sending hardware-keyboard events may make iOS treat a hardware keyboard as attached and hide the software keyboard. Expect the OCR keyboard signal to flip after HID typing. Not verified.

### Gaps
- No measured precision or recall for OCR-based keyboard detection.
- Captions for a focused text field or for keyboard keys were not captured, because the phone was locked.

---

## Q4. If structured access is incomplete: best screenshot-based UI parsing (icons, fields), with latency

### Takeaway
Use axAudit captions for "what exists", OCR for text positions, and a small on-device icon/element detector for "where". The best licence-clean detectors are:
- OmniParser v2's `icon_detect_v3` (YOLOv9-E, MIT), with the optional Florence-2 captioner (MIT)
- uitag's YOLO11s fine-tuned on GroundCUA (MIT, about 18 MB)

Apple's own Screen Recognition detector shows that this class of model runs in about 10 ms on an iPhone 11, but it is not exposed to developers. A Core ML conversion of a YOLO detector should run in tens of milliseconds on an A19-class iPhone. Set-of-mark numbering of detector boxes for the LLM is the standard way to turn detections into taps.

### Cited Findings
- **OmniParser v2:**
  - `icon_detect` is YOLOv8 (AGPL-3.0). `icon_detect_v3` is YOLOv9-E (MIT). `icon_caption` is Florence-2 (MIT). "icon_detect_v3 + icon_caption = fully MIT pipeline."
  - Average latency is **0.6 s/frame on A100 and 0.8 s on a single 4090**, 60% faster than v1.
  - ScreenSpot-Pro average accuracy 39.6.

  — [microsoft/OmniParser-v2.0 model card](https://huggingface.co/microsoft/OmniParser-v2.0)
- **Apple Screen Recognition (iOS 14):**
  - SSD with a MobileNetV1 and FPN backbone, trained on 77,637 screens from 4,068 iPhone apps
  - 12 classes: checkbox, container, dialog, icon, picture, page control, segmented control, slider, text, text field, tab-bar item, toggle
  - About **20 MB Core ML and about 10 ms per screen on an iPhone 11**
  - mAP@0.5 IoU of 71.3%
  - Icon Recognition for 38 icon types
  - A clickability model at 90% precision and 73.6% recall
  - Faster R-CNN was rejected: more than 1 s per screen and more than 120 MB

  — [Apple ML Research: Making Mobile Applications Accessible with ML](https://machinelearning.apple.com/research/mobile-applications-accessible)
- **uitag (macOS, MIT):**
  - Apple Vision text recognition and rectangle detection, plus YOLO11s fine-tuned on GroundCUA (9 classes, about 18 MB), run on 640×640 tiles
  - Element coverage on ScreenSpot-Pro: Vision-only **57.3% overall, 42.5% icon**; Vision+YOLO **90.8% overall, 87.6% icon**
  - Example timing at 1920×1080: Vision 942 ms, YOLO 2150 ms (hardware not stated)
  - It excludes OmniParser and Screen2AX for AGPL reasons

  — [uitag on PyPI](https://pypi.org/project/uitag/)
- Vision OCR alone misses icons and controls with no text labels, which is Mode's current failure. — [uitag](https://pypi.org/project/uitag/)
- I found no public iOS 26 Vision API for UI element detection. Visual Intelligence in iOS 26 targets on-screen content such as businesses, not element boxes. — [Apple ML Screen Recognition](https://machinelearning.apple.com/research/mobile-applications-accessible); [codingmall iOS 26 Visual Intelligence write-up](https://codingmall.com/knowledge-base/25-global/369199-deep-dive-into-ios-26s-visual-intelligence-image-parsing) (secondary source)

### Inferences
- **On-iPhone pipeline:**
  1. Screenshot from CoreDevice
  2. Vision OCR, about 100–300 ms (not measured here)
  3. Core ML YOLO icon/element detector on one full-screen pass, roughly 20–60 ms on the Neural Engine, extrapolated from Apple's 10 ms on an A13 for a smaller SSD
  4. Merge, then draw numbered marks (set-of-mark) or send a JSON list of boxes plus OCR text plus axAudit captions to the LLM

  The tiling uitag uses is unnecessary at phone resolution.
- **Skip the captioner.** Florence-2 would add hundreds of ms and is unnecessary if axAudit captions supply icon names. Match captions to unlabeled icon boxes by reading order: top-left to bottom-right, the same XY-cut ordering Apple uses.
- **Licensing:** use `icon_detect_v3` (MIT) or the uitag/GroundCUA YOLO (MIT). Avoid the AGPL `icon_detect` weights in an open-source app unless the licence is acceptable.

### Gaps
- No measured Core ML latency for OmniParser `icon_detect_v3` or YOLO11s on an iPhone 17-class device.
- No published iPhone-app-specific accuracy for these detectors. GroundCUA and ScreenSpot-Pro are mostly desktop.
- I found no reliable source for "ScreenParser" latency. Not researched further.

---

## Q5. What iOS automation tools use for element trees, and what works without an XCTest runner

### Takeaway
On real devices, every mainstream element-tree tool (Appium/WDA, Maestro, DeviceKit, idevice's `WdaClient`) runs an XCUITest runner and reads XCTest accessibility snapshots, which brings the overlay. idb's accessibility describe commands, xctree and AXe are simulator-only. The only real-device, non-XCTest element source in open tooling is the axAudit inspector, implemented in pymobiledevice3 and go-ios but not in idevice.

### Cited Findings
- **Appium / WDA:** XPath lookup needs "a snapshot of the whole accessibility hierarchy with all element attributes resolved, which is a time-expensive operation". Large hierarchies with hundreds of elements are a known XCTest limitation. The mitigations are `snapshotMaxDepth`/`snapshotMaxChildren` and `mobile: source` with `format=description`. — [Appium XCUITest driver: WDA slowness](https://appium.github.io/appium-xcuitest-driver/11.2/troubleshooting/wda-slowness/)
- **Maestro:** an XCUITest runner serves the view hierarchy over HTTP. XCUITest output was "superior to IDB in terms of the richness of the view hierarchy", and it became the official iOS driver from Maestro 1.18.0. — [Maestro blog: re-building the iOS driver](https://maestro.dev/blog/maestro-re-building-the-ios-driver)
- **idb:** "accessibility operations are currently supported on iOS Simulators only". That covers `describe-all`, `describe-point` and `describe`. HID works on simulators and partly on devices. — [idb UI docs](https://fbidb.io/docs/idb/ui); [idb accessibility docs](https://fbidb.io/docs/accessibility)
- **DeviceKit iOS:** a "modern WebDriverAgent alternative" (JSON-RPC) built on an XCUITest runner with private-API wrappers. **xctree** is simulator-only. **DSH iOS** uses AXe for simulators and WDA for real devices, with OCR fallback when the tree is empty. — [edusperoni/devicekit-ios](https://github.com/edusperoni/devicekit-ios); [ldomaradzki/xctree](https://github.com/ldomaradzki/xctree); [ZSeven-W/dsh-ios](https://github.com/ZSeven-W/dsh-ios)
- **idevice** ships `WdaClient` with `source()`, `find_elements`, `element_rect`, `send_keys` and others, plus `dvt/xctest` for launching runners. Both need WDA/XCTest and therefore the overlay. — [idevice services/wda.rs](https://github.com/jkcoxson/idevice/blob/89702099f4d7552f704f93b4526756abda319ceb/idevice/src/services/wda.rs); [dvt/xctest](https://github.com/jkcoxson/idevice/tree/89702099f4d7552f704f93b4526756abda319ceb/idevice/src/services/dvt/xctest)
- **pymobiledevice3:** `developer accessibility` has `list-items`, `element-attributes`, `run-audit`, `notifications`, `settings` and `shell`. **go-ios:** `ios ax`, `ios ax audit` and `ios resetax`. Neither needs XCTest. — [pymobiledevice3 cli/developer/accessibility](https://github.com/doronz88/pymobiledevice3/tree/master/pymobiledevice3/cli/developer/accessibility); [go-ios README](https://github.com/danielpaulus/go-ios/blob/main/README.md); [go-ios main.go `startAx`/`runAxAudit` ~L1012–1060](https://github.com/danielpaulus/go-ios/blob/main/main.go)
- **Out-of-process accessibility sees less:** frameworks that read accessibility from outside the app "cannot see WebViews, many third-party SDK elements, or elements rendered outside the native view hierarchy". — search-result summary attributed to the [DSH iOS](https://github.com/ZSeven-W/dsh-ios) and [xctree](https://github.com/ldomaradzki/xctree) READMEs (secondary; not verified line-by-line)

### Inferences
- **Recommended Mode perception stack with no overlay:**
  - (a) A CoreDevice screenshot.
  - (b) Vision OCR with boxes.
  - (c) A new idevice-based axAudit client, run in parallel with (a) and (b). It walks the elements and returns ordered captions such as "Back, Button", "Add, Button", "Search, Search Field" and handles. The client is about 300–500 lines of Rust ported from pymobiledevice3's `accessibilityaudit.py` onto idevice's `RemoteServerClient` and `rsd_checkin`.
  - (d) Optionally a Core ML icon detector for icon boxes.
  - (e) Keyboard detection from OCR, cross-checked against keys or an "is editing" caption in the axAudit walk.
  - Actions: HID taps at box centres, the axAudit Activate action by handle as an alternative, and HID keyboard typing.
- **tidevice** was not researched directly. It is generally known to rely on WDA/XCTest for UI, but I found no citation here.

### Gaps
- tidevice's element approach was not verified with a source.
- I found no benchmark comparing an axAudit walk with a WDA snapshot on the same screen.
