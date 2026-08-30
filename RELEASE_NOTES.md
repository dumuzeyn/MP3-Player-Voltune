# MP3 Player Voltune 3.2.0

## Русский

Версия 3.2.0 добавляет полностью локальную группировку песен по звучанию.

- Новая вкладка «Звучание» находится сразу после «Плейлисты».
- Voltune анализирует реальный аудиосигнал: темп, энергию, громкость, динамический
  диапазон, форму спектра, баланс баса и верхних частот, ритмическую активность,
  спектральный контраст и компактные признаки тембра.
- Группы подбираются адаптивно под текущую библиотеку, а не по фиксированному числу.
- Каждая группа получает короткое относительное название из двух слов, например
  «Быстрый ритм», «Спокойный поток» или «Глубокий бас».
- Группа открывается как обычный список песен с воспроизведением и перемешиванием.
- Анализ идёт по одной песне в фоне, останавливается во время воспроизведения, при
  низком заряде или сильном нагреве и продолжается после следующего запуска.
- Результаты сохраняются в локальной базе и пересчитываются только после изменения
  файла или версии алгоритма. При удалении песни или папки её профиль тоже удаляется.
- В настройках появился переключатель «Анализировать песни по звучанию»; по умолчанию
  функция включена.
- Гистограмма и длительность в карточках песен снова аккуратно выровнены по вертикали.

Анализ не использует интернет, облачные модели, аккаунты или внешние сервисы. В сеть
не отправляются ни аудиофайлы, ни их признаки.

## English

Version 3.2.0 adds fully local grouping by sound.

- The new Sound tab appears immediately after Playlists.
- Voltune analyzes the actual audio signal: tempo, energy, loudness, dynamic range,
  spectral shape, bass/treble balance, rhythmic activity, spectral contrast, and
  compact timbral features.
- The number of groups adapts to the current library instead of using a fixed count.
- Every group receives a short relative two-word name such as Fast rhythm, Calm flow,
  or Deep bass.
- A group opens as a normal track list with play and shuffle actions.
- One song is analyzed at a time in the background. Work pauses during playback, low
  battery, or severe thermal pressure and resumes after a later launch.
- Results are cached in the local database and invalidated only when the file or
  analysis version changes. Removing a track or folder also removes its profile.
- Settings now includes Analyze songs by sound, enabled by default.
- Song-card waveforms and durations are vertically aligned again.

Analysis uses no internet connection, cloud model, account, or external service. Audio
files and derived features never leave the device.
