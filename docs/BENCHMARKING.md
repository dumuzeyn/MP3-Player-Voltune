# Voltune performance benchmarks

The `benchmark` module uses AndroidX Macrobenchmark. The app generates up to 10,000 synthetic metadata records in the dedicated benchmark build; no music files are committed.

## Scenarios

- cold, warm, and hot startup;
- opening and scrolling a 10,000-item library;
- switching Songs and Favorites;
- critical startup, library, full-player, playback, and tab paths covered by the baseline profile.

Additional device scenarios to record before publishing performance claims: first-track start, next-track transition, full-player opening, search, theme application, large artwork, and animated GIF background.

## Commands

```powershell
.\gradlew.bat :benchmark:assembleBenchmark
.\gradlew.bat :benchmark:connectedBenchmarkAndroidTest
```

Run benchmarks on a physical release-class device with stable temperature and battery. Reports are written under `benchmark/build/outputs` and `benchmark/build/reports`. Keep raw reports as CI artifacts; do not present emulator numbers as physical-device performance.

No performance value is considered verified until the device, Android version, build commit, iteration count, thermal state, and report artifact are recorded together.

## Recorded physical-device run

Run date: 2026-07-22. Configuration commit: `b5cc90e`.

Device: realme GT Neo2 5G (`RMX3370`), Android 13 / API 33, 8 CPU cores, about 7.3 GiB RAM. The benchmark build was minified, non-debuggable, isolated from the installed production app, and executed for five iterations per measured scenario.

| Scenario | Median | Range |
| --- | ---: | ---: |
| Cold startup, time to initial display | 356.9 ms | 334.5-365.9 ms |
| Warm startup, time to initial display | 392.9 ms | 369.5-403.2 ms |
| Cold startup with 10,000 synthetic library records | 1129.1 ms | 1087.4-1163.0 ms |

The Android 13 firmware rejected the ART command used by `CompilationMode.Partial` and returned incomplete FrameTimeline data. The run therefore used `CompilationMode.Ignore`; hot-start tracing and Baseline Profile generation were skipped on this device and remain enabled for Android 14+. The 10,000-record value measures initial display while the scenario also performs a scroll and switches Songs/Favorites; it is not presented as an Android 13 frame-time result.

Raw JSON and Perfetto traces are generated under `benchmark/build/outputs/connected_android_test_additional_output`. They are build artifacts and are intentionally not committed to Git.
