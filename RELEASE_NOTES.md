# Voltune 3.3.0

## Русский

Версия 3.3.0 делает локальный раздел «Похожие» точнее и заметно ускоряет возврат на
главный экран во время воспроизведения.

- Раздел «Звучание» переименован в «Похожие», а пользовательское название приложения
  во всех основных местах сокращено до Voltune.
- Группы теперь строятся устойчивым алгоритмом с нормализацией выбросов, несколькими
  запусками k-means++, автоматическим выбором количества групп и проверкой крупных
  неоднородных кластеров.
- BPM оценивается по нескольким участкам песни с коррекцией ошибок ×2/÷2 и показателем
  уверенности. Неуверенный темп меньше влияет на группу и не определяет её название.
- Названия групп детерминированно описывают реальные характеристики: темп, энергию,
  бас, динамику и спектр. Под названием показываются BPM и число треков.
- Главный экран сохраняет готовые строки, прокрутку и загруженные обложки при переходах,
  а состояние воспроизведения обновляется точечно.
- Выбор разделов надёжно центрируется после первого и повторного запуска, включая
  изменение доступной ширины окна и планшетный макет.
- Индикатор текущего трека стал единым скруглённым вертикальным маркером во всех списках.
- Карточки плейлистов приведены к компактной высоте 68 dp без потери анимированного
  предпросмотра и действий.

Анализ полностью выполняется на устройстве и не использует интернет, облачные модели,
аккаунты или внешние сервисы. Аудиофайлы и рассчитанные признаки не покидают устройство.

## English

Version 3.3.0 makes the local Similar section more accurate and substantially reduces
the work required when returning Home during playback.

- The Sound section is now Similar, and the user-facing product name is simply Voltune.
- Groups use robust outlier-resistant normalization, deterministic multi-start
  k-means++, adaptive cluster selection, and quality checks for oversized heterogeneous
  clusters.
- BPM is estimated from multiple song segments with half/double-time correction and a
  confidence score. Uncertain tempo has less clustering weight and does not drive names.
- Deterministic names describe real tempo, energy, bass, dynamics, and spectral traits;
  cards also show a concise BPM and track-count summary.
- Home reuses its existing rows, scroll position, and loaded artwork across navigation,
  while playback changes update only related controls.
- The looping section selector centers after the final viewport size is available on a
  clean launch, relaunch, window changes, and tablet layouts.
- Track lists, queues, and playlists share one rounded vertical Now Playing indicator.
- Playlist cards now use a compact 68 dp layout without losing animated previews or
  actions.

Analysis stays entirely on the device and uses no internet connection, cloud model,
account, or external service. Audio files and derived features never leave the device.
