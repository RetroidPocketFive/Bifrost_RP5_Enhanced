package com.moonbench.bifrost

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import java.io.IOException

/**
 * SAF-backed folder layout for user-managed Bifrost exports.
 *
 * This class deliberately does not request broad storage permissions and never
 * deletes existing files. The calling Activity is responsible for launching
 * ACTION_OPEN_DOCUMENT_TREE and taking the persistable URI permission when
 * Android grants it.
 */
object BifrostLibraryStorage {
    private const val PREFS_NAME = "bifrost_library_storage"
    private const val KEY_TREE_URI = "tree_uri"
    private const val DIRECTORY_MIME = DocumentsContract.Document.MIME_TYPE_DIR

    val directoryLayout: List<String> = listOf(
        "Saved",
        "Saved/Profiles",
        "Saved/Themes",
        "Saved/Sample Area",
        "Saved/Media",
        "Saved/Media/Images",
        "Saved/Media/Sounds",
        "Saved/Media/Music",
        "Saved/Media/Videos",
        "Saved/Settings",
        "Saved/Calibration",
        "Saved/Devices",
        "Saved/Presets",
        "Saved/Logs",
        "Saved/Backups"
    )

    data class SetupResult(
        val rootUri: Uri,
        val createdFolders: List<String>,
        val existingFolders: List<String>,
        val warnings: List<String>
    )

    /** Save the user-selected SAF tree URI after permission has been granted. */
    fun rememberRoot(context: Context, treeUri: Uri) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TREE_URI, treeUri.toString())
            .apply()
    }

    fun getRememberedRoot(context: Context): Uri? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_TREE_URI, null)
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }

    fun clearRememberedRoot(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_TREE_URI)
            .apply()
    }

    /**
     * Create or reuse the standard directory layout beneath a SAF tree.
     * Existing entries are reused and unrelated files are left untouched.
     */
    @Throws(IOException::class, SecurityException::class)
    fun ensureDirectoryLayout(context: Context, treeUri: Uri): SetupResult {
        val rootDocumentId = runCatching {
            DocumentsContract.getTreeDocumentId(treeUri)
        }.getOrElse {
            throw IOException("The selected folder is not a valid Android document tree.", it)
        }
        var currentParent = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocumentId)
        val created = mutableListOf<String>()
        val existing = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        for (path in directoryLayout) {
            var parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocumentId)
            val segments = path.split('/')
            val traversed = mutableListOf<String>()
            for (segment in segments) {
                traversed += segment
                val relativePath = traversed.joinToString("/")
                val child = findChildDirectory(context, treeUri, parentUri, segment)
                parentUri = if (child != null) {
                    if (relativePath == path) existing += relativePath
                    child
                } else {
                    val newDirectory = runCatching {
                        DocumentsContract.createDocument(
                            context.contentResolver, parentUri, DIRECTORY_MIME, segment
                        )
                    }.getOrNull()
                    if (newDirectory == null) {
                        warnings += "Could not create folder '$relativePath'. Check write access to the selected location."
                        break
                    } else {
                        created += relativePath
                        newDirectory
                    }
                }
            }
        }

        currentParent = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocumentId)
        // The root folder itself is user-selected; we only create/reuse children.
        return SetupResult(treeUri, created.distinct(), existing.distinct(), warnings.distinct())
    }

    /** Find a direct child directory by display name without touching other entries. */
    private fun findChildDirectory(
        context: Context,
        treeUri: Uri,
        parentUri: Uri,
        displayName: String
    ): Uri? {
        val parentId = runCatching { DocumentsContract.getDocumentId(parentUri) }.getOrNull()
            ?: return null
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            if (idColumn < 0 || nameColumn < 0 || mimeColumn < 0) return null
            while (cursor.moveToNext()) {
                if (cursor.getString(nameColumn) == displayName &&
                    cursor.getString(mimeColumn) == DIRECTORY_MIME
                ) {
                    return DocumentsContract.buildDocumentUriUsingTree(
                        treeUri, cursor.getString(idColumn)
                    )
                }
            }
        }
        return null
    }
}