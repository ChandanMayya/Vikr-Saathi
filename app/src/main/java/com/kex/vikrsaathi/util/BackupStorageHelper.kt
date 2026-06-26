package com.kex.vikrsaathi.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

data class BackupSaveResult(
    val fileName: String,
    val displayPath: String,
    private val contentUri: Uri?,
    private val legacyFile: File?
) {
    fun shareUri(context: Context): Uri? {
        contentUri?.let { return it }
        val file = legacyFile ?: return null
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
}

object BackupStorageHelper {

    const val FOLDER_NAME = "Vikr Saathi"

    fun saveBackupJson(context: Context, fileName: String, json: String): BackupSaveResult {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveWithMediaStore(context, fileName, json)
        } else {
            saveLegacyPublicFile(context, fileName, json)
        }
    }

    private fun saveWithMediaStore(context: Context, fileName: String, json: String): BackupSaveResult {
        val relativePath = "${Environment.DIRECTORY_DOCUMENTS}/$FOLDER_NAME"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, contentValues)
            ?: throw IllegalStateException("Could not create backup file in $FOLDER_NAME")

        try {
            resolver.openOutputStream(uri)?.use { stream ->
                stream.write(json.toByteArray(Charsets.UTF_8))
            } ?: throw IllegalStateException("Could not write backup file")
            val published = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            resolver.update(uri, published, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }

        return BackupSaveResult(
            fileName = fileName,
            displayPath = "Internal storage/$relativePath/$fileName",
            contentUri = uri,
            legacyFile = null
        )
    }

    @Suppress("DEPRECATION")
    private fun saveLegacyPublicFile(context: Context, fileName: String, json: String): BackupSaveResult {
        val folder = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            FOLDER_NAME
        )
        if (!folder.exists() && !folder.mkdirs()) {
            throw IllegalStateException("Could not create folder: ${folder.absolutePath}")
        }
        val file = File(folder, fileName)
        file.writeText(json, Charsets.UTF_8)
        return BackupSaveResult(
            fileName = fileName,
            displayPath = file.absolutePath,
            contentUri = null,
            legacyFile = file
        )
    }
}
