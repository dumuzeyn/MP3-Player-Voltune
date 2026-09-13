# Аудит перед миграцией Voltune на Kotlin

Дата аудита: 2026-09-01. Базовая ветка: `main`, merge-коммит `88bdf44`.

Этот документ описывает фактическое состояние кода перед миграцией. README и старые
архитектурные документы использовались только как подсказки; выводы ниже проверены по
исходникам, манифесту, Gradle-конфигурации, тестам и измерениям приложения.

## 1. Инвентаризация

- Продуктовый код: 217 файлов Java, 0 файлов Kotlin.
- Unit-тесты: 50 файлов Java. Инструментальные тесты: 22 файла Java.
- Benchmark: 2 файла Java и отдельный модуль `benchmark`.
- UI: Android Views и RecyclerView, не Compose. XML-разметка используется только для
  элементов списка; основная иерархия строится программно.
- Одна реальная экранная Activity: `MainActivity` -> `MainActivityCore`.
- Fragment отсутствуют.
- `DarkMainActivity` и 50 вложенных Activity из `LauncherThemeActivities` нужны только
  как цели launcher alias для цветовых схем иконки. Они не являются отдельными экранами.
- Один foreground service: `Media3PlayerService` с типом `mediaPlayback`.
- Точка входа процесса: `Mp3PlayerApplication`; в debug включает StrictMode и всегда
  устанавливает локальный обработчик crash-report.
- `applicationId`: `com.dumuzeyn.mp3player`; minSdk 23, targetSdk 35, compileSdk 36.
- Текущая версия: 3.4.0 (`versionCode 43`). Эти идентификаторы нельзя менять миграцией.

Разрешения ограничены локальным плеером: foreground media playback, уведомления,
`READ_MEDIA_AUDIO`/старое `READ_EXTERNAL_STORAGE` и wake lock. INTERNET и
MANAGE_EXTERNAL_STORAGE отсутствуют.

## 2. Текущая архитектура

```text
Mp3PlayerApplication
  -> MainActivity -> MainActivityCore
       -> MainActivityCoordinator (lifecycle)
       -> MainActivityViewController / MainRenderer / feature renderers
       -> LibraryLoader -> LibraryDatabase
       -> LibraryRepository -> LibraryPersistenceController
       -> PlaybackController -> MediaController
       -> PlaybackUiState (проекция Media3 для Views)
       -> feature controllers (playlists, settings, import, similar, covers)

Media3PlayerService (отдельный lifecycle)
  -> один ExoPlayer
  -> одна MediaLibrarySession
  -> VoltuneMediaLibraryCallback / Media3SessionCommandHandler
  -> PlaybackStateManager / PlaybackSessionRestorer
  -> AudioEffectsManager / TrackLoudnessNormalizer / PlaybackSleepTimer
```

Плюс текущей структуры: обязанности уже частично вынесены из Activity, файлы ограничены
500 строками, тяжёлая библиотечная работа в основном выполняется в executor. Главный
недостаток: 209 продуктовых файлов лежат в одном плоском package, а большинство
контроллеров напрямую держат `MainActivityCore` и читают/меняют его поля. Это усложняет
владение состоянием, тестирование и lifecycle.

## 3. Playback и Media3

- Playback уже полностью использует Media3 1.10.1: ExoPlayer, MediaController,
  MediaLibraryService и MediaLibrarySession.
- ExoPlayer создаётся ровно один раз в `Media3PlayerService`.
- Media3 является фактическим источником queue/current item/playback state.
- UI получает immutable `PlaybackSnapshot`, но дополнительно держит проекцию очереди в
  `PlaybackUiState.queue`; это допустимый cache, однако его обновление пока императивное.
- Команды UI идут через `PlaybackActions`, `PlaybackQueueController` и
  `PlaybackController` в MediaController.
- Уведомление, lock screen, Bluetooth/headset controls и audio focus обслуживаются
  MediaSession/ExoPlayer. `setHandleAudioBecomingNoisy` зависит от настройки непрерывного
  воспроизведения; используется локальный wake mode.
- После process death `PlaybackSessionRestorer` читает компактный snapshot, восстанавливает
  очередь по стабильным track ID в фоне и применяет её к пустому player на main thread.
- Сохраняются current item, position, duration, queue, repeat, shuffle и active/inactive
  timestamps. Истёкший mini-player удаляется по пользовательскому retention window.
- Shuffle реализован как подготовленная очередь; при restore намеренно не создаётся второй
  независимый порядок.

Цель миграции: сохранить единственный Media3 player, заменить callbacks/ручную проекцию на
`StateFlow<PlaybackState>`, а сервис оставить небольшим координатором. Нового параллельного
player или второго playback repository создавать нельзя.

## 4. Библиотека и хранение

`LibraryDatabase` использует SQLiteOpenHelper, база `mp3_player_library.db`, schema v7.
Таблицы:

- `tracks`, включая metadata, fingerprint, availability и историю воспроизведения;
- `favorites`;
- `playlists` и `playlist_tracks` с сохранением порядка;
- `library_sources`, `track_sources`, `excluded_tracks` для SAF и исключений;
- `audio_profiles` и `sound_groups` для сохранённого анализа похожих треков.

Миграции v1 -> v7 недеструктивные. Есть одноразовый импорт старых JSON/SharedPreferences
в SQLite. Переход на Kotlin не требует изменения схемы и не должен менять track ID, URI,
playlist order или существующий migration flag. Room можно вводить только поверх этой же
базы с проверенной migration и identity hash; для первого этапа безопаснее перенести DAO
логику на Kotlin без смены формата.

`LibraryLoader` загружает сохранённый snapshot в одном background executor и публикует его
на main. `LibraryRepository` держит in-memory списки и индексы по URI/ID. Сохранение
favorites/playlists coalescing-очередью выполняет `LibraryPersistenceController`.

Автоимпорт использует MediaStore и пропускает только `IS_MUSIC`, минимум 20 секунд, после
чего `DeviceAudioClassifier` исключает recording, podcast, audiobook, ringtone, alarm,
notification и известные голосовые пути/названия. SAF-импорт файлов и папок сохраняет
persistable URI permission. Обслуживание metadata и отсутствующих URI выполняется вне UI.

## 5. Настройки и пользовательские данные

SharedPreferences используются для небольших настроек и состояния:

- `mp3_player_ui` — тема, анимации, фон, UI и настройки анализа;
- `player_resume` — playback session;
- `audio_effects` и `track_loudness_cache` — эквалайзер/нормализация;
- `playback_behavior`, `player_sleep_timer` — поведение и таймер;
- `voltune_music_folders` — совместимость списка SAF-источников;
- `library_content_version`, `voltune_migrations`, diagnostics.

Крупные коллекции уже не являются основным содержимым SharedPreferences. DataStore можно
вводить через совместимый однократный importer, но нельзя удалять старые keys до проверки
обновления поверх 3.4.0. Backup/restore настроек сейчас опирается на `mp3_player_ui` и тоже
должен быть сохранён.

## 6. Обложки и память

`CoverLoader` использует LruCache размером 6–16 MiB, приватный disk thumbnail cache,
декодирование двумя background workers, coalescing одинаковых запросов, WeakReference к
ImageView, tag-проверку повторного использования и downsampling до 160/целевого размера.
При trim memory кэш сокращается или очищается. Невидимые Activity-объекты освобождаются
через `CloseableRegistry`.

Риск: загрузки управляются собственными executor/Handler, а не structured concurrency;
отмена привязана к закрытию общего loader и tag, не к lifecycle отдельного запроса. При
миграции нужно сохранить disk key и визуальное поведение, заменив управление задачами на
ограниченный CoroutineScope и гарантированную отмену.

## 7. «Похожие» и кластеризация

`SoundAnalysisController` выполняет анализ последовательно в отдельном executor, сохраняет
профили и группы в SQLite и не запускает полный анализ при каждом открытии экрана.
`SoundClusterEngine` работает по сохранённым feature vectors; текущая версия использует
адаптивное объединение групп, а не удалённый KMeans. BPM вычисляется как один из признаков
аккумулятора, но не должен быть возвращён как доминирующий фактор без измерений.

Текущий код способен создавать адаптивное число групп, тогда как продуктовая цель требует
ровно три основных кластера. Это существующее расхождение нужно закрыть отдельным
поведенческим тестом до изменения алгоритма. CPU-анализ следует переносить на
`Dispatchers.Default`, чтение/запись профилей — на `Dispatchers.IO`.

## 8. UI, навигация и обновления

- Home, Songs, Albums, Artists, Genres, Folders, Playlists, Favorites, Similar и Settings
  являются renderer/controller-состояниями внутри одной Activity, а не Fragment.
- Songs использует RecyclerView `ListAdapter`/DiffUtil и payload-обновления текущей песни и
  позиции, а не полный `notifyDataSetChanged` каждый тик.
- Часы большого player обновляют только seek/time views каждые 250 мс и останавливаются,
  когда страница не видна. Songs обновляет position payload раз в 500 мс.
- Home static content и playback section разделены; tab preview/cache позволяют возвращать
  готовую иерархию без повторного сканирования библиотеки.
- Навигация не должна становиться владельцем загрузки данных. Kotlin UI state будет
  собираться lifecycle-aware и обновлять только затронутые Views/ListAdapter items.

Дизайн и программно построенная View-иерархия сохраняются. Одновременный переход на Compose
не входит в миграцию: он увеличил бы риск визуальных и lifecycle-регрессий.

## 9. Подтверждённые причины лагов до миграции

Причины были воспроизведены и измерены до этого аудита, затем исправлены в `80b8358` и
`e499722`/`88bdf44`:

1. `Media3PlayerService` синхронно перечитывал всю библиотеку и записывал duration/session
   на main thread при смене media item.
2. Обновление каждой строки повторно выполняло линейный поиск текущего трека, что давало
   квадратичную работу на большой библиотеке.
3. Нажатие на песню инициировало лишний UI refresh до callback Media3, затем второй refresh.
4. Playback ID входил в ключ статического Home cache и сбрасывал готовый Home после смены
   песни.

Измеренный плохой сценарий: SQLite lock около 5 секунд, кадр 5867 мс и 343 skipped frames;
row refresh на 1000 треков занимал 1879,29 мс. После исправлений: row refresh 0,54 мс,
media transition 1,37 мс, cached Home adoption около 1,17 мс, без SQLite lock и skipped
frames. Kotlin не считается причиной этого улучшения: миграция обязана сохранить эти
инварианты и trace-проверки.

Оставшиеся архитектурные риски производительности:

- ручные Handler/executor lifecycle и большое число Activity-bound controller;
- императивная синхронизация PlaybackSnapshot, UI queue и отдельных view callbacks;
- создание Home derived content отдельным executor без versioned immutable state;
- несколько независимых SharedPreferences readers для одной группы настроек;
- плоский package затрудняет контроль направления зависимостей.

## 10. Lifecycle, утечки и фон

Activity закрывает loader, controllers, repositories, cover cache, handlers и MediaController
через `CloseableRegistry`. Долгоживущие библиотечные workers переводят Context на application
context. Cover targets хранятся weak. Статических mutable Activity/Context полей не найдено.

Playback переживает уничтожение Activity в MediaLibraryService. При повторном создании UI
подключается к существующей MediaSession; второй ExoPlayer не создаётся. Риски миграции:
CoroutineScope должен отменяться по владельцу, service scope — только в `onDestroy`, а UI
collectors — по lifecycle STARTED/STOPPED. Нельзя связывать service scope с Activity.

Фоновое сканирование и кластеризация не должны работать постоянно. Wake lock принадлежит
ExoPlayer и используется только для playback. WorkManager сейчас не используется.

## 11. Исходная тестовая точка

До изменения продуктового кода выполнен `:app:qualityCheck --no-problems-report`:

- debug APK и androidTest APK собраны;
- unit tests прошли;
- lint прошёл;
- проверка одного ExoPlayer и отсутствия тяжёлых API в Activity прошла;
- проверка launcher/splash assets прошла;
- лимит 500 строк прошёл.

Обычный запуск Gradle без `--no-problems-report` иногда падает после успешных задач из-за
конфликта записи собственного `build/reports/problems/problems-report.html`. Это дефект
локального Gradle report writer, не ошибка приложения, но его нужно учесть в CI-командах.

Существующее покрытие включает queue, repeat/shuffle, session restore, background playback,
favorites/playlists, SQLite migrations, duplicate/relink policies, similar clustering,
metadata, permissions, Home hierarchy, текстовые границы и tablet layout. Не хватает
систематических Kotlin Flow/concurrency-тестов, явной проверки ровно трёх кластеров и полного
update-from-3.4.0 теста без очистки данных.

## 12. План замены без двух архитектур

1. Подключить Kotlin, coroutines и lifecycle-runtime; добавить immutable state/contracts и
   тесты, не меняя пользовательское поведение.
2. Перенести playback models/state/store/controller/service по одному вертикальному контуру.
   В каждом коммите удалять заменённый Java-файл; не создавать второй player.
3. Перенести SQLite schema/DAO, library repository/loader/import/maintenance. Сначала
   сохранить schema v7 byte-for-byte, затем отдельно оценить Room.
4. Перенести Track/Playlist и collection use cases, favorites и playlist UI contracts.
5. Перенести View UI state, navigation и renderers, сохранив Views/XML и размеры.
6. Перенести settings/themes/icons с чтением существующих preference keys.
7. Перенести audio analysis/clustering на structured concurrency и сохранить результаты.
8. Перенести оставшиеся utilities/tests, удалить Java source set собственного кода и
   временные adapters.
9. Выполнить compileDebugKotlin, assemble, unit/lint/instrumented tests, update test,
   macrobenchmark и профиль CPU/memory/frame/startup/navigation/playback.

Каждый этап должен собираться и отправляться отдельным русским коммитом. Миграция считается
завершённой только при нуле собственных Java-файлов в main/test/androidTest/benchmark, при
сохранении applicationId/schema/preferences и прохождении функциональной матрицы.
