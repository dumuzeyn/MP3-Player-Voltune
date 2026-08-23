# Voltune 3.1 architecture

Voltune remains a single-module Java/XML Android application. Responsibilities are separated
inside that module so playback reliability and existing SAF data are preserved without a rewrite.

```text
MainActivityCore
  -> MainActivityViewController / MainRenderer / feature renderers
  -> LibraryLoader -> LibraryDatabase / TrackStore
  -> GlobalSearchController / LyricsRepository / MetadataEditorController
  -> PlaybackController -> MediaController

Media3PlayerService (MediaLibraryService)
  -> one ExoPlayer instance and one MediaLibrarySession
  -> PlaybackHistoryRecorder / PlaybackStateManager / AudioEffectsManager
  -> VoltuneMediaLibraryCallback -> LibraryDatabase browse projections

PlayerWidgetProvider -> existing MediaLibrarySession
Android Auto -> existing MediaLibrarySession
```

## Ownership rules

- Media3 service state is the only source of truth for the active queue and playback state.
- UI queue lists are snapshots and all changes are sent back as Media3 commands.
- Database and metadata work run outside the UI thread; loaded snapshots are applied on main.
- Library metadata edits update Voltune's database only. Audio source bytes are never modified.
- Lyrics and artwork reads are bounded and use `ContentResolver`; arbitrary raw paths are not resolved.
- No network or broad-storage permission is present. User music stays local.
- Production source files must remain at or below 500 lines; `checkSourceFileSize` enforces this.

## Database

Schema v3 adds album artist, year, track/disc numbers, play/skip counts, date added,
last played, and last completed timestamps. Migrations v1 -> v2 -> v3 preserve stable track IDs,
favorites, playlists, and playlist order without destructive fallback.

## Verification

Run the complete local/CI gate with:

```bash
./gradlew qualityCheck
```

Physical-device scenarios, including widget, Android Auto, adaptive icon masks, splash,
background playback, Bluetooth, and tablet layouts, are listed in `DEVICE_TESTING.md`.
