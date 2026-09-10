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
- 137 JVM unit tests: no failures or errors, including speed bounds/round trips,
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

## Implemented in the third stage

- Added Editor immediately after Folders. Original audio files are not modified.
- Editable clip ranges with millisecond precision, splitting, removing a selected
  range, concatenation in displayed lane/clip order, and up to eight mixed lanes.
- Clips have independent timeline offsets and volume. Overlapping clips on the
  same lane are rejected; separate lanes are mixed by Media3 Transformer 1.10.1.
- Drafts persist between activity launches. Undo/redo retains up to 32 edits in
  the current session; clearing the project can also be undone.
- Real AAC/M4A export with progress/cancel, Android document saving and automatic
  reimport through the existing library importer. A finished export remains
  available to save again if the file picker is cancelled or saving fails.
- Export is activity-owned: destroying the activity cancels an unfinished export,
  while the draft and completed cached export can be restored. No cloud service
  or network permission was added.
- System bar/cutout insets protect tabs, dialogs and the mini-player on Android 15.
  Particle lifetime labels now expand vertically on narrow screens.
- `qualityCheck --no-problems-report`: passed, including 137 JVM tests and lint.
- API 35 headless verification: four real export tests verify source integrity,
  encoded duration, non-silent decoded PCM and the 25% volume ratio. Two editor UI
  tests verify draft restoration, undo/redo, sliders, saving and library reimport.
  Three text-layout tests and two existing library UI tests also passed.
- Editor flow verified at 393dp and 320dp widths and in a 640x320dp landscape
  viewport. Russian dialog clipping also passed at 320dp with 1.3x test text.
- Screenshots are generated under `app/build/reports/audio-editor*.png`.

## Implemented in the fourth stage

- Real decoded waveforms now appear on the clip timeline and in the trim dialog.
  The envelope uses 2048 timestamp-indexed peak buckets, retaining silence and
  transients without storing the full PCM track or inventing waveform samples.
- Start/end handles update the millisecond fields; tapping the waveform sets the
  split cursor, synchronized with the existing split slider. Numeric fields remain
  available for precise and accessible editing.
- Horizontal drags edit selection; vertical drags scroll the dialog. The waveform
  fits the visible scroll area even in a short landscape viewport.
- One background decoder is shared by active views, with a 16-source session cache.
  Unsubscribing the last view cancels its request; closing the editor controller
  cancels pending work. Failed decoding displays an unavailable state, not a fake
  waveform. Decoding is capped at 120 seconds, with a 10-second no-output timeout.
- AAC packets with negative priming timestamps are preserved instead of mistaken
  for EOF. Export tests decode the resulting M4A back into a non-empty waveform.
- `qualityCheck --no-problems-report`: passed, including 143 JVM tests and lint.
  All production source files remain within the existing 500-line limit.
- API 35 headless emulator: 14 Android tests passed, covering real WAV amplitude
  and silence, decoder cancellation/retry, shared cache/lifecycle, AAC exports,
  source integrity, selection/scroll gestures, undo/redo, draft restore, document
  saving/reimport, and Russian text clipping. Waveform interaction was also
  checked at 320dp width and in a 640x320dp landscape viewport.
- Screenshots: `app/build/reports/audio-editor-waveform*.png`.
- No production-phone installation or release was performed in this stage.

## Remaining work from the full request

- In-editor audition using the existing playback service, with ordinary queue,
  position and session persistence preserved. No second playback engine was added.
- Real stem separation, vocal removal, speech cleanup, BPM and key detection.
  Do not substitute frequency filtering for source separation, or upload users'
  audio to cloud services without explicit consent.
- Audit negative AAC priming timestamps in the separate AudioFeatureExtractor
  before extending its BPM/key analysis; its old input loop still checks time < 0.
- Verify supported playback/import/export formats before adding new decoders.
- Full device matrix, version/release artifacts and production-phone installation.

## Editor research references

- Adobe Audition multitrack editor:
  https://helpx.adobe.com/audition/desktop/mixing-multitrack-sessions/multitrack-editor-overview.html
- BandLab Splitter:
  https://help.bandlab.com/hc/en-us/articles/16560236938777-Using-BandLab-Splitter
- Moises features: https://moises.ai/features/
- Media3 composition/export:
  https://developer.android.com/media/media3/transformer/composition
- Media3 clipping/effects:
  https://developer.android.com/media/media3/transformer/transformations

Use a non-destructive clip timeline and an established media export engine.
Choose and validate a real local separation model, including its redistribution
license and memory requirements, before exposing AI processing as available.
