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

## Implemented in the fifth stage

- Project and selected-range preview use the same Media3 composition renderer as
  export, including lane mixing, offsets and clip gain. An unchanged project uses
  its cached preview rather than encoding again. Added play/pause, seek and stop.
- The existing service player temporarily borrows the rendered audio; no second
  playback engine was introduced. Queue, index, position, repeat, shuffle and
  speed are restored when preview stops, ends, fails or its controller disconnects.
- A paused copy of the original queue is persisted before preview. Preview never
  replaces resume data, track durations or listening history. Playback fade/EQ and
  normalization do not alter the preview mix. Sleep timer/audio focus loss stop
  preview without resuming the original music.
- Preview commands are restricted to the application UID and app-owned cached M4A
  files. Ordinary player commands return to the original music queue first.
- Closing/backgrounding the activity, leaving Editor or dismissing clip preview
  stops preview; preparation is cancellable. Editing/export cannot race preview.
- `qualityCheck --no-problems-report` passed with 143 JVM tests, lint and existing
  architecture/source-size checks. API 35 headless tests: 21 unique scenarios across
  runs, including seven preview-service cases, four editor workflows, three text
  layout checks and seven background playback regressions. All passed.
- Screenshot: `app/build/reports/audio-editor-preview.png`.
- The session callback contract was checked against the pinned Media3 1.10.1 source:
  https://github.com/androidx/media/blob/1.10.1/libraries/session/src/main/java/androidx/media3/session/MediaSession.java

## Implemented in the sixth stage

- Editor automatically estimates BPM and musical key for the selected fragment.
  Selection changes debounce analysis; detaching the dialog cancels its request.
  Uncertain/silent material is shown as undetermined, not assigned a fake key.
- JTransforms 3.1 FFT feeds a chroma estimator with the Krumhansl-Kessler profiles
  and correlation/margin checks. BPM rejects silence and low-confidence envelopes.
- Waveforms and library/editor analysis share one streaming PCM decoder, with
  precise selection bounds, cancellation and preservation of AAC priming packets.
  PCM 8/16/24/32-bit and float output are handled explicitly. Analysis version is 3.
- `qualityCheck` passed: 148 JVM tests, lint, APK and architecture checks.
  API 35 headless: 19 unique Android scenarios passed across two runs, including
  exact PCM boundaries, real chord identification, silence, waveform/cache,
  feature extraction, AAC export, editor workflow and enlarged Russian text.
- Key reference: https://extra.humdrum.org/man/keycor/
- FFT dependency: https://github.com/wendykierp/JTransforms (BSD-2-Clause).

## Implemented in the seventh stage

- Real offline speech cleanup uses RNNoise v0.1, pinned as a Git submodule at
  cdf196b1e9de2f8ff1003328ebf9a4316477429d. Its embedded model needs no downloads
  at runtime. License text is included in the APK.
- Processing reads the selected fragment, resamples with Media3 Sonic to 48 kHz,
  keeps separate channel states and compensates the 10 ms RNNoise delay. A new
  16-bit WAV replaces the draft clip without changing the source, lane, offset or
  gain. Undo/redo and persisted drafts preserve access to both versions.
- One background processing job has progress, cancellation, disk-space checks and
  partial-file cleanup. Activity destruction cancels unfinished processing.
  Mono/stereo input is supported; unsupported multichannel processing fails clearly.
- NDK 27.2.12479018 and CMake 3.22.1 build ARM64, ARM32 and x86_64 libraries with
  flexible page-size support. Windows short-path C++ linker mode is explicit.
  Initialize submodules before building: `git submodule update --init --recursive`.
- `qualityCheck` passed with 148 JVM tests and lint. 21 unique Android cases passed
  across final runs: neural noise reduction, exact selected length, channel isolation,
  silence, cancellation/retry, source integrity, UI undo/restart, waveform/analysis,
  AAC export, preview and Russian text layout.
- The pinned MIT demucs.cpp/Eigen source is prepared for the next stage, but stem
  separation is not yet exposed or claimed complete.
- RNNoise: https://github.com/xiph/rnnoise/tree/v0.1

## Playback retention follow-up

- Expiring the remembered session also closes the full player immediately and
  releases its pager. An empty playback projection cannot leave stale song/queue
  pages behind. The previous library screen remains in place.
- A valid paused session retains its page, second queue item and exact position;
  active playback is not expired by the inactivity policy.
- `qualityCheck` passed. Twelve Android scenarios passed: full-player/song and
  queue expiry after a stop/resume lifecycle, valid pause, active playback, old
  service-session expiry, background repeat, reconnect and activity destruction.
  The 8-hour gap is simulated in persisted timestamps, not a real 8-hour wait.

## Implemented in the eighth stage

- Offline HTDemucs separates a selected clip into drums, bass, other and vocals;
  vocal removal mixes the three instrumental stems. Originals remain unchanged,
  and results participate in the existing draft and undo/redo flow.
- Pinned FP16 model (83,994,361 bytes) is verified with SHA-256 during the build
  and extraction. Model, Demucs and Eigen license notices are bundled in the APK.
  No runtime network access or upload is involved.
- One inference job uses disk-backed PCM, bounded windows, context and overlap
  blending. Cancellation, insufficient memory/storage and occupied lanes leave
  the draft intact and remove incomplete outputs.
- Generated upstream copies use tiled full-key attention and tiled convolution
  instead of materializing large attention/im2col matrices. Dense-reference
  comparisons pass at 1e-5 tolerance. Two independent tile/head workers avoid
  repeatedly parallelizing small matrix products.
- API 35 headless x86_64 with 2.5 GB guest RAM: real-model inference for a 2-second
  test input (internally padded by Demucs) improved from 203,356 to 31,790 ms.
  Sampled native allocations during inference were 480-515 MB, not a measured
  whole-process peak. This is offline processing, not real-time separation;
  physical ARM-device performance and musical separation quality vary.
- `qualityCheck` passed with 148 JVM tests and lint. Fifteen unique Android cases
  passed: two native/model/window tests and thirteen editor, text and RNNoise
  regressions. Tests include moved-lane allocation, occupied-lane rejection,
  cancellation/retry, source integrity, exact overlap duration and undo/redo.
- Screenshot inspected: `app/build/reports/audio-editor-stems.png`.

## Implemented in the ninth stage

- Import recognizes `.opus` and `.oga` when a document provider omits audio MIME
  metadata. Existing MP3, M4A, AAC, WAV, Ogg and FLAC handling is preserved.
- Real synthetic fixtures for all eight extensions are checked through service
  playback, PCM waveform decoding and non-zero-start trim/export to AAC/M4A.
  Fixtures can be regenerated with `tools/generate-audio-format-fixtures.ps1`.
- Raw ADTS AAC exposed an unseekable-clipping failure. Export now retries by
  indexing AAC into a temporary MP4 container on a cancellable worker. Compressed
  frames remain byte-identical; only the requested final export is encoded.
  Original files and draft URIs remain unchanged. Temporary indexed sources are
  cleaned after success, failure or cancellation.
- `qualityCheck` passed with 150 JVM tests and lint. Thirteen Android cases passed:
  eight-format end-to-end coverage, lossless AAC indexing/cancel and eleven editor
  export/UI regressions. Exported trim durations and original bytes are checked.
- Formats still depend on platform decoders. These are API 35 runtime results,
  not a promise that every codec/profile works on every Android device.
- Primary format references:
  https://developer.android.com/media/media3/exoplayer/supported-formats
  https://developer.android.com/media/platform/supported-formats

## Compatibility hardening

- The first API 26-36 matrix found a real Android 8/9 crash: older platform
  SQLiteOpenHelper does not implement AutoCloseable. LibraryDatabase now declares
  the interface explicitly, with a direct-interface and resource-close regression.
- AAC indexing checks cancellation before platform extractor initialization.
- Playback tests cancel inherited sleep timers and start with a foreground
  activity; Android 15 correctly rejects audio focus from an inactive test app.
  Queue swipe tests wait for page settling, retention compares actual paused
  positions, and report pruning uses timestamps distinguishable on older filesystems.
- Offline model preparation is shared once per CI workflow, still SHA-256 checked
  by every consumer. Bounded retries handle transient HTTP/network failures.
- Local API 35: all 97 instrumented tests passed together (243.714 seconds).
  `qualityCheck` passed with 150 JVM tests, lint and architecture/icon checks.
- The remote matrix still needs a clean rerun; Android 10 exposed a separate FLAC
  trim-duration issue. Optimized-build runtime verification and release are pending.

## Exact FLAC selections

- Trimmed FLAC clips are decoded to disk-backed WAV selections before composition.
  PCM bytes, bit depth, channels and sample rate are preserved; no intermediate
  lossy encoding is added. Offsets, gain and clip IDs stay intact.
- Handles both compressed FLAC extractors and Android extractors that expose
  already-decoded PCM, identified by the FLAC signature. Preparation is cancellable,
  bounded in memory and removes temporary outputs on failure/cancellation.
- API 35: eight format/export/selection tests passed. The new selection test checks
  exact 998 ms sample count and byte-identical selected PCM, timeline properties,
  unchanged source bytes and pre-cancellation without temporary files.
- `qualityCheck` passed. Android 10 confirmation remains part of the next CI matrix.

## Remaining work from the full request

- Final requested order: complete the remaining stages, then rename the app to
  `Voltune — аудио плеер и редактор`, then publish a tested signed release.
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
