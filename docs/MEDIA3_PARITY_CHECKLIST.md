# Media3 parity checklist

Stable baseline: `70e1ddcd24cd93071f3500bad7a81df04c02d5f9` (Voltune 2.5.3).

This checklist is the release gate for replacing the legacy `android.media.MediaPlayer`
playback core. An item is checked only after the stated test has actually passed.

## Library and persisted data

- [ ] Existing songs survive an application update.
- [ ] Existing playlists and their order survive an application update.
- [ ] Favorites survive an application update.
- [ ] Persisted SAF permissions remain usable after restart.
- [ ] Playback queue, current item, position, repeat and shuffle restore after process death.
- [ ] Themes, backgrounds, particles, equalizer and loudness-analysis settings remain intact.

## Playback commands

- [ ] Play a selected track.
- [ ] Play a complete queue.
- [ ] Play and pause from the application.
- [ ] Play and pause from the media notification/system media panel.
- [ ] Seek within the current item.
- [ ] Move to the next and previous item.
- [ ] Add, remove and move queue items.
- [ ] Clear the queue and dismiss the mini-player.
- [ ] Shuffle keeps Voltune's single pre-shuffled queue behavior.

## Repeat and completion

- [ ] Repeat off ends after the last item with `QUEUE_ENDED`.
- [ ] Repeat one repeats the current item until explicitly stopped or the sleep timer fires.
- [ ] Repeat all wraps from the last item to the first for multiple complete cycles.
- [ ] Shuffle and repeat all do not create a second independent shuffle order.
- [ ] A user pause is never resumed automatically.

## Background and lifecycle

- [ ] Playback continues with the screen off.
- [ ] Playback continues when the Activity is closed.
- [ ] Playback continues when the task is removed from recents.
- [ ] A controller reconnects after Activity recreation.
- [ ] Service recreation restores the queue and position.
- [ ] The mini-player and full player immediately reflect the MediaController state.
- [ ] The foreground notification remains synchronized with the active item.

## Interruptions and audio

- [ ] Temporary audio-focus loss pauses and resumes only when allowed by policy.
- [ ] Permanent audio-focus loss does not resume automatically.
- [ ] Audio becoming noisy pauses playback and does not auto-resume.
- [ ] Wired-headset removal is handled.
- [ ] Bluetooth disconnection is handled.
- [ ] Incoming-call interruption is handled.
- [ ] Equalizer is reapplied after an ExoPlayer audio-session change.
- [ ] Loudness gain is applied without losing existing analysis results.
- [ ] Sleep timer stops playback with `SLEEP_TIMER`.

## Errors

- [ ] An inaccessible URI is reported without exposing its full path.
- [ ] A corrupt item is skipped when another playable item exists.
- [ ] Consecutive failures cannot create an infinite skip loop.
- [ ] A fully unavailable queue stops with `ALL_ITEMS_UNAVAILABLE`.
- [ ] A fatal playback error stops with `FATAL_ERROR`.

## Automated release checks

- [x] Unit tests pass.
- [x] Android lint passes.
- [x] Debug APK builds.
- [x] Signed release APK builds with R8 and resource shrinking.
- [x] Instrumented tests compile.
- [x] Instrumented tests pass on a physical Realme device with Android 13.
- [ ] Instrumented MediaController/background tests pass on Android 8.
- [ ] Instrumented MediaController/background tests pass on Android 16.
- [ ] Tablet layout tests pass.
- [x] Macrobenchmarks compile and produce reports.
- [x] Baseline Profile is packaged in the release artifact.

## Manual physical-device checks

- [ ] Realme: multi-hour playback and repeated queues.
- [ ] Samsung/One UI background playback.
- [ ] Xiaomi or Redmi background playback.
- [ ] Pixel or clean Android background playback.
- [ ] Aggressive battery saver behavior documented.
- [ ] Bluetooth interruption and reconnection.
- [ ] Incoming phone call and audio-focus return.
- [ ] Device reboot and persisted SAF access.
- [ ] TalkBack and Switch Access.

Unchecked manual items are known release-verification gaps, not implicit passes.
