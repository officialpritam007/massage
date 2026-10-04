# Messaging smoothness and high refresh validation

The app follows the device's selected refresh mode. A 120 Hz panel has an 8.33 ms frame budget. Source changes and CI do not establish 120 fps: verify on a physical supported phone, with the OS set to its high refresh mode and power saving off. Record the model, Android version, selected/measured refresh rate and app revision.

## Functional acceptance

Use two accounts and a conversation with at least 2,000 messages, including image, voice and video attachments.

- Cold open and reopen a cached chat offline: cached rows appear without a full-screen loading flash.
- Open the same chat online: server refresh preserves visible rows and scroll position.
- Send multiple text messages: existing bubbles remain stationary and never disappear/re-enter.
- Scroll away from the bottom and receive a new message: reading position stays fixed.
- Load older history: the first visible message stays anchored.
- Open/close keyboard, reply to older messages, search/jump, return from media and switch tabs.
- Clear a chat and reopen after restart: cleared history stays absent.
- Queue text and attachments offline, restart, reconnect: FIFO and recovery work with stable message IDs.
- A permanent send error shows Failed and retry can return to Sending.
- Repeat with Reduce Motion and Reduce Transparency enabled, system large text and TalkBack.

## Frame capture

Use the APK from the build artifact for correctness. For performance conclusions use a release build on a physical phone, because debug Compose adds instrumentation overhead.

Clear the app's frame counters immediately before each scenario:

```sh
adb shell dumpsys gfxinfo com.aistudio.liquidchat.vwnxkp reset
```

Perform 20–30 seconds of continuous chat scrolling, then save the measured frames:

```sh
adb shell dumpsys gfxinfo com.aistudio.liquidchat.vwnxkp framestats > chat-scroll-120hz.txt
python tools/analyze_frame_stats.py chat-scroll-120hz.txt --refresh-rate 120 --check
```

Use the actual active refresh rate, not the panel's advertised maximum. The analyzer ignores flagged/incomplete frames, rejects captures below 120 valid frames and reports median/p95/p99 duration plus missed deadlines. The target is p95 within the selected frame budget and no more than 5% missed deadlines. Capture cold launch, steady scrolling, send/receive, keyboard transitions and navigation separately; run each scenario three times. These captures include OS/device effects, so use Perfetto FrameTimeline and main-thread traces to identify app work behind misses.

The app does not force a display mode or keep animations running to manufacture a frame-rate reading. Reliable killed-process push, private media delivery and E2EE remain separate architecture work.
