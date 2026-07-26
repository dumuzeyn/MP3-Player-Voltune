# MP3 Player Voltune 3.0.1

## Русский

Версия 3.0.1 ускоряет библиотеку и исправляет запуск релизной сборки.

- Раздел «Песни» переведён на `RecyclerView`: создаются только видимые карточки, а изменения воспроизведения обновляют отдельные строки.
- Загрузка библиотеки и поиск выполняются вне основного потока, поиск запускается с небольшой задержкой после ввода.
- Оптимизированы запросы SQLite, сохранение треков и загрузка обложек.
- Добавлен кэш обложек в памяти и на диске; невидимые карточки больше не загружают полноразмерные изображения.
- Исправлены два падения при открытии release-версии, вызванные слишком ранним обращением к контексту Android.
- Из строки песни убрана отдельная кнопка избранного: действие осталось в свойствах песни.
- Режим повтора в большом плеере теперь подписан «Повтор», «Песня» и «Список» без `1` и знака бесконечности.
- Сборка проверена unit-тестами, lint и тестами совместимости на Android 8, Android 16 и планшетном интерфейсе.

Все функции версии 3.0 сохранены, включая Media3, фоновое воспроизведение, очередь, повтор, таймер сна, плейлисты, темы, эквалайзер и адаптивный планшетный интерфейс.

## English

Version 3.0.1 improves library responsiveness and fixes release startup.

- Songs now uses `RecyclerView`, creating only visible rows and applying targeted playback-state updates.
- Library loading and debounced search filtering run outside the UI thread.
- SQLite reads, track persistence, and artwork loading have been optimized.
- Artwork uses memory and disk caching, and off-screen rows no longer decode full-size images.
- Fixed two release startup crashes caused by accessing Android context before the Activity was attached.
- Removed the redundant favorite button from song rows; the action remains available in track properties.
- The full-player repeat control now uses `Repeat`, `Song`, and `List` labels without numeric or infinity symbols.
- The build passed unit tests, lint, and compatibility checks for Android 8, Android 16, and tablet layouts.

All 3.0 features remain available, including Media3 playback, background audio, queues, repeat, sleep timer, playlists, themes, equalizer, and adaptive tablet layouts.
