# Voltune physical-device playback matrix

This checklist records real device results. An unchecked item is not verified. Emulator results must not be entered here.

## Devices

| Device family | Android / firmware | Result | Notes |
| --- | --- | --- | --- |
| Samsung / One UI | Not tested | [ ] | |
| Xiaomi or Redmi / HyperOS or MIUI | Not tested | [ ] | |
| Realme | Not tested | [ ] | Connected-device checks must include firmware and date |
| Pixel or clean Android | Not tested | [ ] | |

## Playback scenarios

- [ ] Several hours of uninterrupted playback.
- [ ] Several complete `repeat all` cycles.
- [ ] Continuous `repeat one` playback.
- [ ] Playback with the screen off.
- [ ] Playback after removing the Activity from recent apps.
- [ ] Recovery after a forced process stop.
- [ ] Recovery after device reboot.
- [ ] Bluetooth headset disconnect and reconnect.
- [ ] Wired headset disconnect.
- [ ] Incoming call, interruption, and audio-focus return.
- [ ] Start YouTube, Telegram, or another audio application while Voltune plays.
- [ ] Corrupted audio file inside a valid queue.
- [ ] SAF URI whose persisted permission was revoked.
- [ ] Queue, position, repeat, shuffle, timer, and pause reason restore correctly.

For every completed item record device, Android version, firmware, Voltune version, date, duration, expected result, actual result, and logs if it failed.
