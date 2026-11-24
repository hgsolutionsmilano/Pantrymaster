package com.example.data.local

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageStorageManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    private val storageDir: File
        get() = File(context.filesDir, "images").apply { if (!exists()) mkdirs() }

    suspend fun copyFromUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(storageDir, "${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null
            file.absolutePath
        }.getOrNull()
    }

    suspend fun downloadFromUrl(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body ?: return@withContext null
                val file = File(storageDir, "${UUID.randomUUID()}.jpg")
                body.byteStream().use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
                file.absolutePath
            }
        }.getOrNull()
    }

    fun deleteImageFile(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching {
            val file = File(path)
            if (file.exists() && file.parentFile?.absolutePath == storageDir.absolutePath) {
                file.delete()
            }
        }
    }

    fun listAllImageFiles(): List<File> = storageDir.listFiles()?.toList().orEmpty()

    fun getImagesDir(): File = storageDir
}
