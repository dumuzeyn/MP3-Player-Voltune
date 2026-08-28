# MP3 Player Voltune 3.1.1

## Русский

Версия 3.1.1 синхронизирует оформление приложения и исправляет повторное добавление музыки.

- Light Theme теперь использует единый фон `#FFFFFF`, а Dark Theme - `#111015` в интерфейсе, launcher icon, Splash Screen, стартовом окне и системных панелях.
- Custom Theme выбирает ближайший статический фон значка по сохранённому `customBg` через LAB/Delta E, а не по цвету акцента.
- Добавлен режим «Системная»: при смене дневного и ночного режима Android обновляются интерфейс, ярлык и следующий splash.
- Иконки подготовлены для legacy Android, adaptive icons, Android 12 splash и Android 13 themed icons; логотип и безопасные отступы сохранены.
- Удаляются существующие дубли одного файла из разных источников с сохранением избранного, плейлистов и статистики. Повторный импорт больше не создаёт такие дубли.
- Последние плейлисты, исполнители и альбомы визуально отделены от фона главного экрана, а одна песня не повторяется в нескольких домашних блоках.

Обычный adaptive launcher icon является статическим ресурсом Android. Поэтому произвольный `customBg` сопоставляется с ближайшим из десяти подготовленных Light/Dark-фонов; конкретная оболочка может показать обновление значка с небольшой задержкой из-за системного кэша.

## English

Version 3.1.1 synchronizes visual surfaces and prevents duplicate library entries.

- Light Theme now uses canonical `#FFFFFF`, while Dark Theme uses `#111015` across the app, launcher icon, splash, startup window, and system bars.
- Custom Theme selects the closest static launcher background from the saved `customBg` using LAB/Delta E instead of accent colors.
- Added a System theme that follows Android day/night changes for the UI, launcher alias, and next splash.
- Added verified legacy, adaptive, Android 12 splash, and Android 13 themed icon assets without changing the Voltune logo or safe-zone sizing.
- Existing cross-provider duplicates are merged while preserving favorites, playlists, and listening statistics, and imports no longer recreate them.
- Recent playlists, artists, and albums are visually separated on Home, and tracks do not repeat across multiple Home sections.

Android adaptive launcher icons are static resources. An arbitrary `customBg` is therefore matched to the nearest of ten prepared Light/Dark backgrounds; some launchers may display the updated resource after a short system-cache delay.
