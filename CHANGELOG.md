# Changelog

## 3.1.3 - Mini-player retention fix

- Enforced the configured mini-player memory window when the app returns to the
  foreground and when the playback service restores after process recreation.
- Cleared expired paused queues instead of allowing the Media3 service to project them
  back into the mini-player.

## 3.1.2 - Safe launcher alias lifecycle

- Deferred launcher alias changes until the Activity is no longer visible, preventing a
  one-time return to the home screen after an update or Custom background change.
- Avoided alias changes over a foreground Activity during Android system theme updates.
- Separated Custom launcher background matching from foreground accent matching, so the
  Voltune V follows the app's primary and secondary accent colors independently.

## 3.1.1 - Theme icon and Home library fixes

- Unified Light and Dark app, launcher, splash, startup window, status bar, and navigation
  bar backgrounds under canonical `#FFFFFF` and `#111015` theme tokens.
- Made Custom launcher backgrounds follow `customBg` through perceptual LAB distance,
  added System theme synchronization, and avoided redundant launcher alias changes.
- Added matching high-resolution foreground, legacy launcher, and Android 12+ splash assets
  for every built-in palette.
- Removed existing physical-file duplicates during the database upgrade while preserving
  favorites, playlist membership, source ownership, and listening statistics.
- Prevented automatic scans and folder imports from adding the same file through another
  content provider, and stopped songs from repeating across Home sections.
- Added visible themed surfaces for recent playlists, artists, and albums on Home.
- Added launcher safe-zone, palette mapping, duplicate migration, collection surface, and
  text-clipping checks on a physical Android device.

## 3.1.0 - Library Experience

- Added a Home start screen, persisted listening statistics, history, and six query-based smart playlists.
- Added debounced global search across songs, artists, albums, genres, and playlists plus safe folder browsing.
- Reworked the Media3-owned queue with drag/reorder, swipe removal, play-next, append, clear, and playlist saving.
- Added offline synchronized LRC, plain sidecar text, bounded embedded ID3 lyrics, and line-to-seek behavior.
- Added safe library metadata editing with album artist, year, track, and disc fields; source audio remains read-only.
- Added a Media3-backed home-screen widget and migrated the existing playback service to `MediaLibraryService` for Android Auto browsing and search.
- Replaced every launcher/theme/splash surface with the high-resolution Voltune 3.1 icon and added deterministic mask previews and safe-zone checks.
- Added the non-destructive v2-to-v3 database migration, privacy-safe debug logging, and debug StrictMode checks.
- Split large controllers by responsibility and added `checkSourceFileSize`, `checkArchitecture`, and `qualityCheck` CI gates.

## 3.0.1 - Faster library and stable release startup

- Replaced the manually rendered Songs list with `RecyclerView`, `ListAdapter`, stable track IDs, and targeted payload updates.
- Moved library loading and debounced search filtering off the UI thread.
- Added normalized search data so track metadata is not repeatedly converted for every entered character.
- Reduced SQLite work with grouped playlist reads and differential track persistence.
- Added memory and disk artwork caching while loading covers only for visible song rows.
- Fixed two release-only startup crashes caused by accessing the Activity context before it was attached.
- Removed the redundant favorite button from song rows; favorites remain available in track properties.
- Simplified the full-player repeat control to `Repeat`, `Song`, and `List` states without numeric or infinity symbols.
- Added unit coverage for normalized track search and retained Android 8, Android 16, and tablet compatibility checks.

## 3.0 - Media3 playback and responsive library

- Migrated playback to Media3 `ExoPlayer`, `MediaSessionService`, and `MediaController` while preserving background playback, queues, repeat, notifications, and sleep timer behavior.
- Restored the current track and mini-player synchronously before the Media3 controller reconnects.
- Matched current songs by stable track identifiers with compatibility for queues saved by older releases.
- Removed repeated database reads from periodic playback-state persistence.
- Moved playlist and favorite persistence off the UI thread and made collection updates atomic.
- Reused the prepared Songs/Favorites screen after a tab swipe instead of building the same rows twice.
- Prepared 15 visible cards for Songs, Favorites, Genres, Artists, and Albums to prevent visible incremental loading during transitions.
- Reduced particle and cover work during tab previews while retaining active waveforms and rotating artwork.
- Added a 1,000-track startup and navigation benchmark plus a 165-track database migration test.
- Made Russian the default language for clean installations without changing an existing user's language.

## 2.5.3 - Launcher, splash, and theme polish

- Route each custom launcher alias through an Activity with the matching Android 12+ splash theme.
- Match the active red and light-blue custom palette in the launcher and loading screen.
- Keep custom text colors scoped to the Custom theme so Light and Dark labels stay readable.
- Use the launcher component itself for the recent-apps icon so both surfaces share one scale.
- Add a crisp white outline to black text in the Light theme.
- Use one thin Light-theme outline for labels and buttons, including full-player tools.
- Suppress outlines inside cards; use white around Light-theme text and black around Dark-theme text only on open backgrounds.

## 2.5.2 - Stable settings and menu position

- Keep theme, language, mini-player memory, and background dialogs open while their values change; close them only through an explicit user action.
- Show custom color and text controls only after selecting the Custom theme, without closing or recreating the dialog visibly.
- Pin the Done action inside the custom-theme window while the longer settings area scrolls independently.
- Replace blurred text shadows with a crisp configurable outline and preserve its selected color when toggled.
- Add a default action for all particle colors and sliders while keeping the outline as a simple neutral on/off control.
- Restore both animated tab previews and final lists at the remembered scroll position before they become visible.
- Preserve pending batched song rendering while adjacent tabs are previewed or a swipe is cancelled.
- Let the Custom theme define both accent colors instead of keeping the second accent fixed to yellow.
- Apply the active two-color palette to the header, media-session artwork, recent-apps icon, launcher icon, and Android splash screen.

## 2.5.1 - Playback, sound, and visual customization

- Keep playlist and repeat playback state synchronized with the foreground service after leaving and returning to the app.
- Correct active playlist indicators, rotating playlist artwork, pause behavior, and turntable-style forward/backward seeking.
- Allow playlist ticker speed to be set to zero for a completely static preview.
- Add solid, gradient, image, and GIF backgrounds with independent main/full-player settings and adjustable blur.
- Validate selected visual media before saving it and decode raster pixels without executing metadata, links, or scripts.
- Add equalizer presets while preserving a remembered custom profile.
- Replace fixed volume correction with per-track loudness analysis and smooth gain changes.
- Preserve menu scroll positions, mini-player restoration, original track titles, and already loaded playlist artwork.
- Replace the oversized song actions sheet with a compact content-sized panel and consistently aligned actions.
- Add adjustable full-player disc rotation speed from 25% to 200% and keep seek rotation proportional to that speed.
- Add two independently selectable particle colors while retaining theme colors as the default palette.
- Add an independent text color plus an optional configurable outline for readable text on any background.
- Stabilize Android 15/16 background playback tests by observing confirmed playback state instead of transient queue preparation snapshots.
- Keep the tablet CI job focused on application configuration and responsive-layout checks; background audio remains covered on Android 8 and Android 16.

## 2.5 - Adaptive tablet interface

- Detect tablets automatically at 600 dp smallest width without adding a separate setting.
- Constrain and center the main library, mini-player, dialogs, bottom panels, and full-player content on large screens.
- Scale full-player artwork for tablet portrait and landscape dimensions while preserving phone sizing.
- Keep selected song cards and their action buttons readable when adding tracks to favorites or playlists.
- Split playback responsibilities into focused engine, command, state, timer, and error-recovery components.
- Add dependency review, Dependabot, contribution guidelines, security reporting, and dynamic release metadata.

## 2.4.3 - Voltune identity and settings cleanup

- Rename the application and distributed APK to MP3 Player Voltune.
- Reorder the settings screen and show explicit animation and particle states.
- Add a voluntary CloudTips support link with a clear external-page confirmation.
- Allow every modal panel to close with a horizontal swipe without intercepting sliders.
- Reset application settings once while preserving songs, favorites, and playlists.
- Replace the README with a shorter Russian and English project overview.

## 2.4.2 - Reliable repeat and library stability

- Keep repeat-one and repeat-all playback alive in the foreground service until the user, sleep timer, or a real interruption stops it.
- Recover playback after a temporary audio-focus denial without losing the queue.
- Keep queue and library state synchronized when tracks are removed.
- Move audio import, metadata refresh, and notification artwork decoding off the UI/service main thread.
- Add independent opacity controls for songs, favorites, collections, mini-player, header, and dialogs.
- Reduce tab, playlist preview, and long song-list memory use.
- Fix the GitHub project button, Android 8 volume-leveling fallback, and full-player alignment.
- Verify repeat and sleep-timer continuation on a physical Android 13 device.

## 2.4.1 - Playback continuity and cover rotation

- Keep a playlist advancing after the app task is removed while the sleep timer remains active.
- Persist active playback correctly while the next queue item is preparing.
- Restore the current service state when the Activity returns to the foreground.
- Reset a rotating cover when the track changes and resume rotation after reopening the player.
- Remove the empty bottom inset above the Android navigation bar.
- Add physical-device instrumentation coverage for queue continuation and rotating covers.

## 2.4 - Continuous compatibility checks

- Run Android 8 and Android 16 instrumentation tests for every pull request.
- Keep the complete Android 8–16 emulator matrix on the weekly schedule and manual dispatch.
- Updated GitHub Actions to Node.js 24-compatible major versions.
- Added a reproducible physical-device checklist for Samsung, Xiaomi/Redmi, Pixel, and aggressive battery-saving firmware.
- Release tags, source code, release notes, and the signed APK now advance together as version 2.4.

## 2.3 - Controller redistribution

- Added Android instrumentation tests for application configuration, local crash reports, and background playback with a generated WAV.
- Verified the same test suite on Android 8 through Android 16 (API 26, 28–31, and 33–36) using a GitHub Actions emulator matrix.
- Added per-version JUnit, logcat, crash buffer, DropBox, MediaSession, and PlayerService diagnostic artifacts.
- Added a privacy-preserving local crash store that retains five reports and redacts music URI and storage paths.
- Moved playback queue commands, service start logic, and playback watcher behavior into `PlaybackController`.
- Moved mini-player state rendering into `PlayerUiController`.
- Moved settings screen rendering into `SettingsRenderer`.
- Moved song list rendering, chunked row rendering, and song row click handling into `SongsRenderer`.
- Removed unused generated-style wrapper methods and switched `SongsRenderer` to direct controller calls.

## 2.2 - MainActivity split and README refresh

- Split first UI responsibilities out of `MainActivity` into `SongsRenderer`, `PlayerUiController`, `SettingsRenderer`, `TabsController`, `PlaybackController`, and `ThemeController`.
- Replaced generated-looking `AnonymousClass...`, `RunnableC000...`, and `m...$$Nest...` names with readable names.
- Rebuilt the README in Russian and English with download/navigation buttons, phone screenshots, and contributor-oriented architecture notes.

## 2.1 - Repository quality update

- Moved the main music library, favorites, and playlists from JSON-in-SharedPreferences to a local SQLite database with legacy migration.
- Kept lightweight UI settings and playback resume snapshots in SharedPreferences.
- Added a `SongRowStateRegistry` helper so playback row state is no longer stored directly in `MainActivity`.
- Renamed the playback watcher from a generated-looking anonymous class to `PlaybackWatcher`.
- Added JVM tests for track sorting, legacy track JSON migration, playlist JSON round-trip, and playlist name cleanup.
- Release builds now run with R8 minify and resource shrinking enabled.
- GitHub Actions now publishes only the signed release APK artifact.
- Removed the generated APK from git tracking; release APKs should be downloaded from GitHub Actions or GitHub Releases.

## 2.0

- Added folder import through Android document tree picker.
- Added custom themes with user-selected background and text colors.
- Improved launcher, splash, and in-app vector icons.
- Added swipe-down close from the full player.
- Improved album art caching and playback duration handling.
- Switched playback preparation to `MediaPlayer.prepareAsync()`.
