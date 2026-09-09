# UI and audio request, September 2026

This checklist tracks the new request separately from the completed Kotlin migration.
It is not a release-completion declaration.

## Implemented in the first stage

- Thematic albums tab label, two-line sizing, accurate analysis completion status.
- Removed the V/Voltune header and its layout space.
- Full player: three tools in each of two rows, including playback speed.
- Timer tap starts/cancels the remembered duration; hold opens duration selection.
- Save tap toggles membership; hold chooses Favorites, a playlist, or creates one.
  The selected playlist is cleared when the remembered playback session expires.
- Repeat tap cycles modes; hold opens the three-mode selector.
- Equalizer tap toggles the effect; hold opens its settings.
- Speed tap toggles 1x / remembered coefficient. Hold opens a 0.25x-4x slider
  with 0.05 steps; Apply saves and applies the coefficient through Media3.
- Level tap toggles normalization without changing its mode; hold selects
  reduce-only, boost-only, or balanced normalization.
- Level targets use the quietest, loudest, or arithmetic mean LUFS of available
  analyzed tracks from the current library. Until measurements are available,
  targets are -20, -14, and -16 LUFS respectively. Unanalyzed tracks receive no
  fixed normalization gain. Targets stay within -24 to -10 LUFS; gain remains
  bounded to -12/+8 dB with peak headroom and the existing equalizer protection.
  Existing reduce-only preferences remain valid until a new mode is selected.
- Shuffle precedes sequential playback in library and collection actions.
- Support is the primary action in the author-support dialog.
- Song properties use long press; removed ellipsis buttons from song rows and
  Continue listening. Play/pause glyphs have no contrasting button background.
- Fresh defaults enable a gradient and disable particles; existing explicit
  user preferences are preserved.
- Snapshot-ready signal now follows rendering, preventing tests/consumers from
  observing a ready library with the old empty Home hierarchy.

## Verification

- `:app:qualityCheck :app:assembleDebugAndroidTest`: passed.
- 129 JVM unit tests: no failures or errors, including speed bounds/round trips,
  directional normalization, clipping limits, invalid values, and mode migration.
- API 35 headless emulator: 19 instrumented tests passed across both completed
  stages, covering real Media3
  speed changes, slider endpoint selection, level tap/hold, Russian text at
  enlarged sizing, Home reuse, navigation, queue, playlists, settings and
  alphabet fast scrolling, background playback, repeat, reconnect and service
  lifecycle behavior.
- Physical-device verification and signed release installation are still pending.

## Implemented in the second stage

- Visible covers are prefetched before the first library render and additional
  covers continue warming in the background. Missing embedded artwork uses a
  neutral themed surface without a white field or a Voltune logo fallback.
- Added an optional 1-12 second end-of-track fade. It combines with loudness
  normalization, follows playback-speed changes, and resets on track transitions.
- Fresh palette defaults use blue, purple, gold and white while explicit saved
  custom themes remain unchanged.
- Songs has a compact alphabet rail with present English letters first, followed
  by present Russian letters and `#`. The rail does not duplicate English for an
  English-only library. App scrollbars now use a themed blue-to-gold thumb.
- Added unit coverage for fade boundaries, short tracks and speed changes, plus
  alphabet ordering and normalization. Headless UI coverage verifies rail jumps
  and checks the fade dialog for clipped Russian text.

## Remaining work from the full request

- Audio editor after Folders: non-destructive trim, split, remove range,
  concatenate and multitrack arrangement/export.
- Real stem separation, vocal removal, speech cleanup, BPM and key detection.
  Do not substitute frequency filtering for source separation, or upload users'
  audio to cloud services without explicit consent.
- Verify supported playback/import/export formats before adding new decoders.
- Full device matrix, version/release artifacts and production-phone installation.

## Editor research references

- Adobe Audition multitrack editor:
  https://helpx.adobe.com/audition/desktop/mixing-multitrack-sessions/multitrack-editor-overview.html
- BandLab Splitter:
  https://help.bandlab.com/hc/en-us/articles/16560236938777-Using-BandLab-Splitter
- Moises features: https://moises.ai/features/

Use a non-destructive clip timeline and an established media export engine.
Choose and validate a real local separation model, including its redistribution
license and memory requirements, before exposing AI processing as available.
