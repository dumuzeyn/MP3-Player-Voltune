# Voltune 4.0.0

## Русский

Новое название приложения: **Voltune — аудио плеер и редактор**.

- После раздела «Папки» появился многодорожечный редактор: обрезка, разделение,
  удаление фрагмента, соединение и смешивание до восьми дорожек с настройкой громкости.
- Звуковая волна, точные границы выделения, предварительное прослушивание,
  отмена и повтор изменений, сохранение черновика и экспорт в AAC/M4A.
  Исходные песни не изменяются; готовый файл можно сохранить и добавить в медиатеку.
- Локальное разделение на вокал, ударные, бас и остальные инструменты,
  создание инструментальной версии, очистка речи, определение BPM и тональности.
- Большой плеер получил скорость 0,25–4× и короткое/долгое нажатие для таймера,
  добавления в плейлист, повтора, эквалайзера, скорости и выравнивания громкости.
- Три режима громкости и настраиваемое плавное затихание в конце трека.
- После истечения памяти воспроизведения закрывается и большой плеер:
  устаревшие песня и очередь больше не остаются на экране. Активная музыка не прерывается.
- «Похожие» переименованы в «Тематические альбомы», готовность анализа обозначается
  фразой «Все треки проанализированы». Убрана верхняя панель с повторным названием.
- Улучшена подготовка обложек, убраны белые поля и запасной логотип. Свойства песни
  открываются удержанием; кнопки воспроизведения больше не имеют отдельной подложки.
- Алфавитная навигация в песнях, стилизованные полосы прокрутки, градиентный фон
  без частиц по умолчанию. Сохранённые пользовательские темы не сбрасываются.
- Исправлены окна на узких и горизонтальных экранах, запуск базы на Android 8/9,
  импорт Opus/OGA и экспорт выделенных фрагментов AAC/FLAC.

Обработка выполняется на устройстве, без загрузки аудио в облако. Модель разделения
включена в APK, поэтому файл установки стал больше. Разделение не работает в реальном
времени: скорость и качество зависят от телефона и записи, возможны остатки инструментов.
Поддержка конкретных кодеков и профилей зависит от версии Android и устройства.

## English

Voltune now includes a local multitrack audio editor.

- Trim, split, remove selections, concatenate and mix up to eight lanes with individual
  gain, waveform selection, preview, undo/redo, saved drafts and AAC/M4A export.
  Original songs remain unchanged; exported files can be saved and imported into the library.
- Offline vocal/drum/bass/other separation, instrumental versions, speech cleanup,
  BPM and musical key detection.
- Playback speed from 0.25x to 4x; tap/hold actions for the timer, playlist target,
  repeat, equalizer, speed and three loudness-leveling modes; optional end-of-track fade.
- Expired playback memory now closes the full player instead of leaving stale track
  and queue state visible. Active playback does not expire.
- Thematic albums naming, clearer analysis completion, no redundant top title,
  prefetched covers, long-press song properties and simpler play/pause buttons.
- Alphabet navigation, themed scrollbars, gradient defaults without particles,
  and dialogs that fit narrow and short screens. Existing custom themes are preserved.
- Android 8/9 database compatibility, Opus/OGA import and accurate AAC/FLAC selections.

Audio processing stays on the device. The bundled separation model increases APK size.
Separation is not real-time; performance and quality vary by device and recording,
and residual instruments may remain. Codec/profile support depends on Android and hardware.
