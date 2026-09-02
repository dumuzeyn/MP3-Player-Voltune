package com.dumuzeyn.mp3player

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Compatibility boundary around SQLite-backed SAF music sources. */
object PersistedFolderStore {
    private const val PREFS = "voltune_music_folders"
    private const val TREES = "trees"
    private const val MIGRATED = "sources_migrated_to_sqlite"

    @JvmStatic
    fun remember(context: Context, treeUri: Uri): LibrarySource? =
        remember(context, treeUri, fallbackName(treeUri), true)

    @JvmStatic
    fun remember(
        context: Context,
        treeUri: Uri,
        displayName: String?,
        explicitImport: Boolean,
    ): LibrarySource? {
        if (!hasReadPermission(context, treeUri)) return null
        migrateLegacy(context)
        return LibrarySourceStore(context).use {
            it.remember(treeUri, displayName, explicitImport)
        }
    }

    @JvmStatic
    fun list(context: Context): List<LibrarySource> {
        migrateLegacy(context)
        return LibrarySourceStore(context).use { it.list() }
    }

    @JvmStatic
    fun readableTrees(context: Context): List<Uri> = list(context)
        .map(LibrarySource::asUri)
        .filter { hasReadPermission(context, it) }

    @JvmStatic
    fun contains(context: Context, treeUri: Uri): Boolean {
        migrateLegacy(context)
        return LibrarySourceStore(context).use { it.find(treeUri) != null }
    }

    @JvmStatic
    fun forget(context: Context, source: LibrarySource): RemovedLibraryItems =
        LibraryMutationStore(context).use { it.removeSource(source.sourceId) }

    @JvmStatic
    fun clear(context: Context): RemovedLibraryItems =
        LibraryMutationStore(context).use { it.clearLibrary() }

    @JvmStatic
    fun releaseReadPermission(context: Context, uri: Uri?) {
        if (uri == null) return
        try {
            context.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // Permission may already have been revoked by Android or the provider.
        }
    }

    @JvmStatic
    fun hasReadPermission(context: Context, uri: Uri?): Boolean =
        uri != null && context.contentResolver.persistedUriPermissions.any {
            it.isReadPermission && it.uri == uri
        }

    @JvmStatic
    fun persistReadFlag(resultFlags: Int): Int {
        val flags = resultFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION
        return flags.takeIf { it != 0 } ?: Intent.FLAG_GRANT_READ_URI_PERMISSION
    }

    private fun migrateLegacy(context: Context) {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (preferences.getBoolean(MIGRATED, false)) return
        val legacy = HashSet(preferences.getStringSet(TREES, emptySet()).orEmpty())
        LibrarySourceStore(context).use { store ->
            legacy.forEach { value ->
                val uri = Uri.parse(value)
                if (hasReadPermission(context, uri)) {
                    store.remember(uri, fallbackName(uri), false)
                }
            }
            preferences.edit().remove(TREES).putBoolean(MIGRATED, true).commit()
        }
    }

    private fun fallbackName(uri: Uri?): String =
        uri?.lastPathSegment?.trim()?.takeIf(String::isNotEmpty) ?: "Music folder"
}
