package com.moonbench.bifrost

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.IOException

/**
 * SAF-backed folder layout for user-managed Bifrost exports.
 *
 * The caller launches ACTION_OPEN_DOCUMENT_TREE and persists the granted URI
 * permission. This helper never requests broad storage permissions and never
 * deletes existing files.
 */
object BifrostLibraryStorage {
    private const val PREFS_NAME = "bifrost_library_storage"
    private const val KEY_TREE_URI = "tree_uri"
    private const val BIFROST_DIRECTORY = "Bifrost"
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

    /** Save the user-selected parent tree URI after permission has been granted. */
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
     * Create or reuse Bifrost/Saved/... under the user-selected parent.
     * Re-running this method is safe: existing directories are reused and no
     * files are deleted or overwritten.
     */
    @Throws(IOException::class, SecurityException::class)
    fun ensureDirectoryLayout(context: Context, treeUri: Uri): SetupResult {
        val treeDocumentId = runCatching {
            DocumentsContract.getTreeDocumentId(treeUri)
        }.getOrElse {
            throw IOException("The selected folder is not a valid Android document tree.", it)
        }
        val selectedParent = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocumentId)
        val created = mutableListOf<String>()
        val existing = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        val bifrostRoot = findChildDirectory(context, treeUri, selectedParent, BIFROST_DIRECTORY)
            ?: runCatching {
                DocumentsContract.createDocument(
                    context.contentResolver, selectedParent, DIRECTORY_MIME, BIFROST_DIRECTORY
                )
            }.getOrNull()?.also {
                created += BIFROST_DIRECTORY
            }

        val rootUri = bifrostRoot
            ?: throw IOException("Could not create or open the Bifrost folder. Check write access.")
        if (created.isEmpty()) existing += BIFROST_DIRECTORY

        for (path in directoryLayout) {
            var parentUri: Uri = rootUri
            val traversed = mutableListOf<String>()
            for (segment in path.split('/')) {
                traversed += segment
                val relativePath = "$BIFROST_DIRECTORY/" + traversed.joinToString("/")
                val child = findChildDirectory(context, treeUri, parentUri, segment)
                if (child != null) {
                    parentUri = child
                    if (traversed.joinToString("/") == path) existing += relativePath
                } else {
                    val newDirectory = runCatching {
                        DocumentsContract.createDocument(
                            context.contentResolver, parentUri, DIRECTORY_MIME, segment
                        )
                    }.getOrNull()
                    if (newDirectory == null) {
                        warnings += "Could not create folder '$relativePath'. Check write access to the selected location."
                        break
                    }
                    created += relativePath
                    parentUri = newDirectory
                }
            }
        }

        return SetupResult(
            rootUri = rootUri,
            createdFolders = created.distinct(),
            existingFolders = existing.distinct(),
            warnings = warnings.distinct()
        )
    }

    /** Resolve a managed Bifrost/Saved subfolder for document-picker navigation. */
    fun getLibraryFolderUri(context: Context, relativeFolder: String): Uri? {
        val treeUri = getRememberedRoot(context) ?: return null
        val treeDocumentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return null
        var current = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocumentId)
        current = findChildDirectory(context, treeUri, current, BIFROST_DIRECTORY) ?: return null
        for (segment in relativeFolder.split('/').filter { it.isNotBlank() }) {
            current = findChildDirectory(context, treeUri, current, segment) ?: return null
        }
        return current
    }

    /** Create a non-overwriting file inside a managed Bifrost/Saved subfolder. */
    fun createUniqueFileInLibrary(
        context: Context,
        relativeFolder: String,
        requestedName: String,
        mimeType: String
    ): Uri? {
        val treeUri = getRememberedRoot(context) ?: return null
        val treeDocumentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return null
        var current = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocumentId)
        current = findChildDirectory(context, treeUri, current, BIFROST_DIRECTORY) ?: return null
        for (segment in relativeFolder.split('/').filter { it.isNotBlank() }) {
            current = findChildDirectory(context, treeUri, current, segment) ?: return null
        }
        val dot = requestedName.lastIndexOf('.')
        val stem = if (dot > 0) requestedName.substring(0, dot) else requestedName
        val extension = if (dot > 0) requestedName.substring(dot) else ""
        var candidate = requestedName
        var suffix = 1
        while (findChildFile(context, treeUri, current, candidate) != null) {
            candidate = "$stem ($suffix)$extension"
            suffix++
            if (suffix > 1000) throw IOException("Could not choose a unique filename.")
        }
        return DocumentsContract.createDocument(context.contentResolver, current, mimeType, candidate)
    }

    private fun findChildFile(
        context: Context, treeUri: Uri, parentUri: Uri, displayName: String
    ): Uri? {
        val parentId = runCatching { DocumentsContract.getDocumentId(parentUri) }.getOrNull() ?: return null
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            if (idColumn < 0 || nameColumn < 0) return null
            while (cursor.moveToNext()) {
                if (cursor.getString(nameColumn) == displayName) {
                    return DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idColumn))
                }
            }
        }
        return null
    }
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
