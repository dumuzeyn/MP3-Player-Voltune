-keep class com.dumuzeyn.mp3player.Track { *; }
# JTransforms uses bounded 8192-element Java arrays. JLargeArrays' off-heap
# multi-gigabyte allocation path references the desktop-only Cleaner API.
-dontwarn sun.misc.Cleaner
-keep class com.dumuzeyn.mp3player.SpeechDenoiser { native <methods>; }
-keep class com.dumuzeyn.mp3player.DemucsSeparator { native <methods>; }
-keep interface com.dumuzeyn.mp3player.DemucsSeparator$Progress { *; }
-keepclassmembers class * implements com.dumuzeyn.mp3player.DemucsSeparator$Progress { public boolean update(float); }
-keep class com.dumuzeyn.mp3player.PlayerService { *; }
-keep class com.dumuzeyn.mp3player.MainActivity { *; }
-keep class com.dumuzeyn.mp3player.DarkMainActivity { *; }
-keep class com.dumuzeyn.mp3player.WaveformView { *; }
-keep class com.dumuzeyn.mp3player.TriangleDecorView { *; }
-keep class com.dumuzeyn.mp3player.LibraryDatabase { *; }
-keep class com.dumuzeyn.mp3player.PlaylistManager { *; }
-keep class com.dumuzeyn.mp3player.TrackStore { *; }
