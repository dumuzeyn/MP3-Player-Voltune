# Voltune sound analysis architecture

## Data flow

1. `SoundAnalysisController` receives an immutable snapshot of the current library.
2. `SoundProfileStore` loads cached profiles and saved group assignments from SQLite.
3. Cache validity is checked against track ID, file size, modification time,
   fingerprint, and `TrackAudioProfile.ANALYSIS_VERSION`.
4. Invalid or missing entries move through `QUEUED` and `ANALYZING` to `ANALYZED` or
   `FAILED`. Interrupted entries return to `QUEUED` and resume later.
5. `AudioFeatureExtractor` decodes up to three representative ten-second ranges with
   `MediaExtractor` and `MediaCodec`.
6. `AudioFeatureAccumulator` consumes PCM as a stream. It retains one 256-sample frame,
   decimated spectral totals, and 20 ms envelope values, never a complete PCM track.
7. `SoundClusterEngine` normalizes usable profiles, derives a distance threshold from
   the library, builds deterministic centroid groups, merges undersized groups, and
   applies a data-sized upper bound of `ceil(sqrt(trackCount))`.
8. `SoundGroupNamer` selects the strongest relative traits from normalized centroids
   and produces unique two-word Russian and English names when possible.
9. New profiles are assigned to the nearest saved centroid. A full rebuild is reserved
   for an empty model, an analysis-version change, or a material library change.

## Audio profile

The persisted 18-dimensional vector contains:

- estimated BPM;
- mean absolute energy and RMS-derived dBFS loudness;
- block-level dynamic range;
- normalized spectral centroid, bandwidth, and 85% rolloff;
- zero-crossing rate;
- low-frequency and high-frequency power ratios;
- rhythmic envelope activity and spectral contrast;
- six DCT coefficients over logarithmic spectral bands as compact MFCC-like timbre.

Spectrum work is decimated to one of every eight 256-sample frames. Hann, sine, and
cosine tables are precomputed once, avoiding repeated trigonometric work per song.

## Scheduling and resources

- Exactly one low-priority executor analyzes one file at a time.
- Playback immediately causes cooperative cancellation and requeueing.
- Work waits while battery is at or below 15% and not charging, or Android reports
  severe thermal pressure.
- The analyzer uses no foreground service, wake lock, internet permission, cloud API,
  or model runtime.
- Process termination is safe because every completed profile and every queue state is
  committed independently. The next Activity process reconstructs the queue.

## Persistence and cleanup

Database version 7 adds `audio_profiles`, `sound_groups`, a group lookup index, and a
track-deletion trigger. Migration from version 6 is additive and does not rewrite the
track, favorite, playlist, or source tables. Folder cleanup and differential library
replacement delete track rows through the same trigger; empty groups are pruned on the
next reconciliation.

## Measured clustering cost

Deterministic synthetic profiles were measured by `SoundClusterEngineTest` on the local
JVM used for development:

| Profiles | Time | Resulting groups |
| ---: | ---: | ---: |
| 100 | 7 ms | 3 |
| 500 | 11 ms | 7 |
| 2,000 | 11 ms | 7 |
| 5,000 | 17 ms | 6 |

These figures cover normalization, adaptive grouping, merging, stable IDs, and naming;
they do not include media decoding, whose speed depends on the device codec and audio
format. The performance test fails if any dataset exceeds five seconds.

## Verification coverage

- normalization, distance symmetry, malformed values, and cache invalidation;
- adaptive deterministic clustering, relative naming, uniqueness, and incremental
  nearest-centroid assignment;
- empty and small libraries, failed profiles, and 100/500/2000/5000 synthetic sizes;
- SQLite profile persistence, v6-to-v7 migration, track deletion, and folder removal;
- real WAV decoding and cooperative cancellation on Android;
- immediate rendering and opening of saved groups;
- waveform/duration alignment and existing large-text clipping checks across every tab.

## Known limitations

- Codec support follows the decoders available on the Android device. Unsupported or
  damaged files remain visible in the library with a `FAILED` analysis state.
- Relative names can change after a material rebuild because they intentionally describe
  the current library distribution.
- Analysis resumes while the app process exists; Android may stop the process in the
  background, in which case work resumes on the next launch rather than holding the
  device awake.
