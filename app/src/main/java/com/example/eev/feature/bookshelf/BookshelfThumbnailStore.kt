package com.example.eev.feature.bookshelf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.math.max

/**
 * 本棚（お気に入りフォルダ）専用の永続サムネイル画像ストア。
 *
 * エクスプローラ用のLRUキャッシュ（`cacheDir/folder_thumbs`）とは分離し、
 * `filesDir/bookshelf_thumbs` 配下にお気に入りフォルダのサムネイルを永続保存することで、
 * 本棚画面を開いた瞬間の高速表示を保証する。
 *
 * [責任]: 本棚専用サムネイルの縮小生成・永続保存・不要サムネイルのクリーンアップ。
 * [影響する状態]: `filesDir/bookshelf_thumbs` 内の画像ファイル群。
 * [潜在的エラー]: デコードや書き込み失敗時は `try-catch` で捕捉し、元画像パスへフォールバックする。
 */
class BookshelfThumbnailStore(context: Context) {

    companion object {
        private const val TAG = "BookshelfThumbStore"
        private const val THUMB_DIR_NAME = "bookshelf_thumbs"
        private const val MAX_THUMB_SIZE = 256
    }

    private val thumbDir: File = File(context.applicationContext.filesDir, THUMB_DIR_NAME).apply {
        try {
            if (!exists()) mkdirs()
        } catch (_: Exception) {}
    }

    /**
     * 指定フォルダの先頭画像から本棚専用サムネイルを取得または生成する。
     *
     * [責任]: 専用サムネイルが存在し最新であれば即座にそのパスを返し、未生成なら生成して返す。
     * [影響する状態]: `filesDir/bookshelf_thumbs` 内のファイル。
     * [潜在的エラー]: 生成失敗時は [sourceImagePath] をそのまま返す。
     */
    suspend fun getOrCreateBookshelfThumbnail(
        folderPath: String,
        sourceImagePath: String
    ): String = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(sourceImagePath)
            if (!sourceFile.isFile) return@withContext sourceImagePath

            val thumbFile = File(thumbDir, buildFileName(folderPath, sourceFile))
            if (thumbFile.isFile && thumbFile.length() > 0L) {
                return@withContext thumbFile.absolutePath
            }

            // 同一フォルダの古いサムネイルがあれば削除
            deleteThumbnailForFolder(folderPath)

            val bitmap = decodeScaledBitmap(sourceFile) ?: return@withContext sourceImagePath
            val saved = try {
                FileOutputStream(thumbFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
                }
                true
            } catch (e: Exception) {
                Log.w(TAG, "本棚サムネイル保存失敗: $folderPath", e)
                false
            } finally {
                bitmap.recycle()
            }

            if (saved) thumbFile.absolutePath else sourceImagePath
        } catch (e: Exception) {
            Log.w(TAG, "本棚サムネイル生成失敗: $folderPath", e)
            sourceImagePath
        }
    }

    /**
     * 指定フォルダに対応する本棚専用サムネイルを削除する。
     *
     * [責任]: お気に入り解除時または更新時の古いサムネイル削除。
     * [影響する状態]: `filesDir/bookshelf_thumbs` 内の対象ファイル。
     * [潜在的エラー]: 削除失敗時は無視して継続する。
     */
    fun deleteThumbnailForFolder(folderPath: String) {
        try {
            val prefix = hashString(folderPath).take(16)
            thumbDir.listFiles()?.forEach { file ->
                if (file.name.startsWith(prefix)) {
                    file.delete()
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * 有効なお気に入りフォルダ一覧に含まれない孤立サムネイルを一括削除する。
     *
     * [責任]: お気に入り解除済みフォルダのサムネイル領域クリーンアップ。
     * [影響する状態]: `filesDir/bookshelf_thumbs` 内のファイル群。
     * [潜在的エラー]: 失敗時は無視して継続する。
     */
    fun retainOnly(validFolderPaths: Set<String>) {
        try {
            val validPrefixes = validFolderPaths.map { hashString(it).take(16) }.toSet()
            thumbDir.listFiles()?.forEach { file ->
                val prefix = file.name.substringBefore("_")
                if (!validPrefixes.contains(prefix)) {
                    file.delete()
                }
            }
        } catch (_: Exception) {}
    }

    private fun decodeScaledBitmap(sourceFile: File): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(sourceFile.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val maxDim = max(bounds.outWidth, bounds.outHeight)
            var sampleSize = 1
            while (maxDim / (sampleSize * 2) >= MAX_THUMB_SIZE) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeFile(sourceFile.absolutePath, decodeOpts)
        } catch (_: Exception) {
            null
        }
    }

    private fun buildFileName(folderPath: String, sourceFile: File): String {
        val folderPrefix = hashString(folderPath).take(16)
        val srcHash = hashString("${sourceFile.absolutePath}:${sourceFile.lastModified()}").take(16)
        return "${folderPrefix}_${srcHash}.jpg"
    }

    private fun hashString(input: String): String {
        return try {
            val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            bytes.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            input.hashCode().toUInt().toString(16)
        }
    }
}
