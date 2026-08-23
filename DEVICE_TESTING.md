# Physical device testing / Проверка на физических устройствах

Эмуляторная матрица не заменяет проверку прошивок производителей. Результат отмечается как пройденный только после выполнения всех сценариев на реальном устройстве. The emulator matrix does not replace vendor-firmware testing. A device is marked as passed only after every scenario is completed on real hardware.

## Status / Статус

| Device group | Required firmware | Status | Evidence |
|---|---|---|---|
| Samsung | Current One UI | Not tested | Device unavailable |
| Xiaomi / Redmi | Current HyperOS or MIUI | Not tested | Device unavailable |
| Google Pixel | Current stock Android | Not tested | Device unavailable |
| Aggressive battery saver | Vendor power restrictions enabled | Not tested | Device unavailable |

## Test procedure / Сценарии

Record the model, Android version, firmware version, app version, and APK SHA-256 before testing. Перед проверкой запишите модель, версию Android, прошивку, версию приложения и SHA-256 APK.

1. Import one song, several songs, and a folder through SAF; restart the app and confirm every URI is still readable.
2. Start a playlist, turn the screen off for 15 minutes, and confirm that the current song and automatic transition continue.
3. Open another media app and test transient audio focus loss, pause, and recovery.
4. Connect and disconnect Bluetooth headphones; repeat with wired headphones when available.
5. Enable the vendor battery-saving restrictions, close the Activity, and verify notification controls and background playback.
6. Remove the app from recent tasks, reopen it, and verify queue, current track, position, repeat, and shuffle restoration.
7. Reboot the device and verify that imported songs remain available through persisted SAF permissions.
8. Use previous, play/pause, and next from the system media panel and confirm artwork, theme, and playback state synchronization.
9. Let one complete playlist finish and confirm that repeat and stop-at-end behavior match the selected mode.
10. Add the Voltune widget, then verify artwork, title, artist, previous, play/pause, and next while the Activity is closed.
11. Connect Android Auto, browse songs, artists, albums, playlists and smart playlists, search, and start playback.
12. Check the launcher icon in circle, rounded-square and vendor masks, the recent-apps screen, every theme alias, and Android 12+ splash. Confirm the V is neither cropped nor visually undersized.
13. Open Home, Folders, Lyrics, Queue and metadata editing; rotate portrait/landscape and repeat on a tablet.
14. Review the in-app crash-report count and attach sanitized logs if any scenario fails.

## Result record / Отчёт

Copy this block for each tested device:

```text
Model / Модель:
Android and firmware / Android и прошивка:
App version / Версия приложения:
APK SHA-256:
Date / Дата:
Scenarios passed / Пройденные сценарии: 0/14
Battery restrictions / Ограничения батареи:
Bluetooth device / Bluetooth-устройство:
Result / Результат: PASS | FAIL
Notes and sanitized logs / Примечания и обезличенные логи:
```
