# Voltune 3.4.0

## Русский

Версия 3.4.0 возвращает точную адаптивную группировку похожих треков и делает переход
на главный экран плавным даже во время воспроизведения.

- В «Похожие» восстановлен проверенный адаптивный алгоритм: естественные большие группы
  разрешены, а искусственного выравнивания и фиксированного количества кластеров нет.
- BPM и уверенность темпа полностью исключены из расстояния, нормализации, выбора групп,
  ближайшей группы и названий подборок.
- Короткие названия снова описывают главное отличие звучания без технических конструкций.
- Готовые профили автоматически перегруппируются без повторного чтения MP3. В «Похожие»
  появилась команда «Пересобрать группы».
- В настройках доступен отдельный полный повторный анализ библиотеки с подтверждением,
  прогрессом N/N и автоматической группировкой после завершения.
- Статическая часть Home больше не зависит от текущего трека. Playback-секция, видимые
  обложки и waveform обновляются отдельно и не перегружают кадр перехода.
- Из карточек плейлистов удалены бегущая строка, таймер и автоматическая смена обложек;
  старая сохранённая настройка скорости безопасно игнорируется.

Анализ полностью выполняется на устройстве и не использует интернет, облачные модели,
аккаунты или внешние сервисы. Аудиофайлы и рассчитанные признаки не покидают устройство.

## English

Version 3.4.0 restores accurate adaptive Similar grouping and keeps Home transitions
smooth while music is playing.

- Similar uses the proven adaptive algorithm again. Natural large groups are allowed;
  cluster counts and sizes are not artificially fixed or balanced.
- BPM and tempo confidence are excluded from distance, normalization, group selection,
  nearest-group matching, and collection names.
- Short collection names describe the group's strongest audible distinction without
  technical compound labels.
- Saved profiles are regrouped without decoding MP3 files. Similar now includes a
  Rebuild groups command.
- Settings provides a separate full library re-analysis with confirmation, N/N progress,
  and automatic clustering after completion.
- Static Home content no longer depends on the current track. Playback content, visible
  artwork, and waveforms update independently outside the transition's heavy path.
- Playlist tickers, timed callbacks, and automatic cover cycling are removed. The old
  saved speed preference is safely ignored.

Analysis stays entirely on the device and uses no internet connection, cloud model,
account, or external service. Audio files and derived features never leave the device.
