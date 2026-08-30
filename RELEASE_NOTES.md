# MP3 Player Voltune 3.1.4

## Русский

Версия 3.1.4 убирает светлый квадратный фон, который был виден вокруг скруглённых обложек плейлистов, и включает все исправления 3.1.3.

- Внешний контейнер обложки теперь прозрачный, поэтому по скруглённым краям больше не виден белый фон.
- Цвет-заглушка сохранён внутри самой обложки и не выходит за её границы.
- В большом плеере страницы «Текст» и «Очередь» теперь находятся справа от основного экрана.
- Нижние плитки большого плеера теперь переключают страницы нажатием.
- В очереди свайп влево по песне по-прежнему удаляет её, а свайп вправо возвращает к тексту и основному плееру.

- Память мини-плеера теперь действительно соблюдает выбранный срок. Например, при настройке 2 часа сессия, поставленная на паузу 8 часов назад, больше не появляется снова.
- Проверка выполняется и при возвращении приложения на экран, и при восстановлении фонового проигрывателя после перезапуска процесса.
- Песни снова отображаются на вкладке «Избранное» после добавления сердечком.
- Плейлисты теперь появляются полностью готовыми уже в первом кадре перехода, без видимой дорисовки списка.
- Обложки в карточках плейлистов плавно сменяют друг друга только после загрузки следующего изображения, без пустого промежуточного кадра.

- Light Theme теперь использует единый фон `#FFFFFF`, а Dark Theme - `#111015` в интерфейсе, launcher icon, Splash Screen, стартовом окне и системных панелях.
- Custom Theme выбирает ближайший статический фон значка по сохранённому `customBg` через LAB/Delta E, а градиент V отдельно подбирается по основному и второму акцентам приложения.
- Добавлен режим «Системная»: при смене дневного и ночного режима Android обновляются интерфейс, ярлык и следующий splash.
- Переключение launcher alias выполняется только после ухода Activity в фон, поэтому обновление иконки больше не возвращает пользователя на рабочий стол во время запуска.
- Иконки подготовлены для legacy Android, adaptive icons, Android 12 splash и Android 13 themed icons; логотип и безопасные отступы сохранены.
- Удаляются существующие дубли одного файла из разных источников с сохранением избранного, плейлистов и статистики. Повторный импорт больше не создаёт такие дубли.
- Последние плейлисты, исполнители и альбомы визуально отделены от фона главного экрана, а одна песня не повторяется в нескольких домашних блоках.

Обычный adaptive launcher icon является статическим ресурсом Android. Поэтому произвольный `customBg` сопоставляется с ближайшим из десяти подготовленных Light/Dark-фонов, а акценты - с одной из пяти палитр V; конкретная оболочка может показать обновление значка с небольшой задержкой из-за системного кэша.

## English

Version 3.1.4 removes the light square background that appeared around rounded playlist covers and includes every fix from 3.1.3.

- The outer artwork container is now transparent, so no white background is exposed around rounded corners.
- The themed fallback remains inside the clipped artwork bounds.
- Lyrics and Queue now sit to the right of the main full-player page.
- The full-player indicator tiles now switch pages when tapped.
- Swiping a queue item left still removes it, while right swipes return to Lyrics and the main player.

- Mini-player memory now honors the selected duration. For example, a session paused eight hours ago no longer returns when the limit is two hours.
- Expiration is enforced both when the app returns to the foreground and when the background playback service restores after process recreation.
- Songs added with the heart action are visible on the Favorites tab again.
- Playlist tabs are fully prepared before their first transition frame, removing visible list construction.
- Playlist artwork now crossfades only after the next cover is ready, with no empty frame between covers.

- Light Theme now uses canonical `#FFFFFF`, while Dark Theme uses `#111015` across the app, launcher icon, splash, startup window, and system bars.
- Custom Theme selects the closest static launcher background from `customBg` using LAB/Delta E, while the V gradient is matched independently from the primary and secondary app accents.
- Added a System theme that follows Android day/night changes for the UI, launcher alias, and next splash.
- Launcher aliases now change only after the Activity leaves the foreground, preventing a one-time return to Home during startup.
- Added verified legacy, adaptive, Android 12 splash, and Android 13 themed icon assets without changing the Voltune logo or safe-zone sizing.
- Existing cross-provider duplicates are merged while preserving favorites, playlists, and listening statistics, and imports no longer recreate them.
- Recent playlists, artists, and albums are visually separated on Home, and tracks do not repeat across multiple Home sections.

Android adaptive launcher icons are static resources. An arbitrary `customBg` is therefore matched to the nearest of ten prepared Light/Dark backgrounds and the accents to one of five V palettes; some launchers may display the updated resource after a short system-cache delay.
