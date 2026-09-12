package com.example.eev.feature.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * フォルダサムネイル用の軽量画像をアプリ内部キャッシュに保存・管理するクラス。
 *
 * [責任]: 元画像の縮小デコード、`cacheDir/folder_thumbs` への保存、LRU による容量上限管理。
 * [影響する状態]: ファイルエクスプローラのフォルダサムネイル表示パス。
 * [潜在的エラー]: 画像デコード失敗時は null を返し、キャッシュ書き込み失敗時は元パスへフォールバックする。
 *
 * サムネイルは元画像のパスと最終更新日時から生成したハッシュをファイル名に用いるため、
 * 元画像が更新されると別ファイルとして再生成される。
 */
class FolderThumbnailCache(private val context: Context) {

    companion object {
        /** サムネイルの最大辺サイズ（ピクセル）。 */
        private const val THUMBNAIL_MAX_SIZE = 256

        /** キャッシュディレクトリの容量上限（バイト）。超過時は古い順に削除する。 */
        private const val MAX_CACHE_BYTES = 50L * 1024L * 1024L

        /** キャッシュディレクトリ名。 */
        private const val CACHE_DIR_NAME = "folder_thumbs"
    }

    private val cacheDir: File
        get() = File(context.cacheDir, CACHE_DIR_NAME).apply { if (!exists()) mkdirs() }

    /**
     * 指定された元画像パスに対応する軽量サムネイルを取得する。
     *
     * [責任]: キャッシュ済みならそのパスを返し、未生成なら縮小生成して保存する。
     * [影響する状態]: キャッシュディレクトリのファイル群。
     * [潜在的エラー]: 元画像が存在しない・デコード不能な場合は null を返す。
     *
     * @param sourceImagePath 元画像の絶対パス。
     * @return サムネイル画像の絶対パス。生成できない場合は null。
     */
    suspend fun getOrCreateThumbnail(sourceImagePath: String): String? = withContext(Dispatchers.IO) {
        val sourceFile = File(sourceImagePath)
        if (!sourceFile.isFile) return@withContext null

        val cacheFile = File(cacheDir, buildCacheFileName(sourceFile))
        if (cacheFile.isFile) {
            // 既存キャッシュの最終アクセス時刻を更新して LRU 精度を保つ。
            cacheFile.setLastModified(System.currentTimeMillis())
            return@withContext cacheFile.absolutePath
        }

        val bitmap = decodeScaledBitmap(sourceFile) ?: return@withContext null
        val saved = try {
            FileOutputStream(cacheFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
            true
        } catch (e: Exception) {
            false
        } finally {
            bitmap.recycle()
        }

        if (!saved) return@withContext null
        enforceCacheLimit()
        cacheFile.absolutePath
    }

    /**
     * 元画像を最大辺 [THUMBNAIL_MAX_SIZE] に収まるよう縮小デコードする。
     *
     * [責任]: メモリ効率の良い縮小ビットマップ生成。
     * [影響する状態]: なし（戻り値のみ）。
     * [潜在的エラー]: デコード失敗時は null を返す。
     */
    private fun decodeScaledBitmap(sourceFile: File): Bitmap? {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(sourceFile.absolutePath, boundsOptions)
        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return null

        val sampleSize = calculateInSampleSize(boundsOptions.outWidth, boundsOptions.outHeight)
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeFile(sourceFile.absolutePath, decodeOptions)
    }

    /**
     * 目標サイズに収まる 2 の冪乗の inSampleSize を算出する。
     */
    private fun calculateInSampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        var currentWidth = width
        var currentHeight = height
        while (currentWidth / 2 >= THUMBNAIL_MAX_SIZE && currentHeight / 2 >= THUMBNAIL_MAX_SIZE) {
            currentWidth /= 2
            currentHeight /= 2
            sampleSize *= 2
        }
        return sampleSize
    }

    /**
     * 元画像パスと最終更新日時からキャッシュファイル名を生成する。
     */
    private fun buildCacheFileName(sourceFile: File): String {
        val raw = "${sourceFile.absolutePath}:${sourceFile.lastModified()}"
        val digest = MessageDigest.getInstance("MD5").digest(raw.toByteArray())
        return digest.joinToString("") { "%02x".format(it) } + ".jpg"
    }

    /**
     * キャッシュディレクトリの合計サイズが上限を超えている場合、最終アクセスが古い順に削除する。
     *
     * [責任]: LRU 方式によるキャッシュ容量制御。
     * [影響する状態]: キャッシュディレクトリのファイル群。
     * [潜在的エラー]: 削除失敗時は無視して処理を継続する。
     */
    private fun enforceCacheLimit() {
        val files = cacheDir.listFiles() ?: return
        var totalBytes = files.sumOf { it.length() }
        if (totalBytes <= MAX_CACHE_BYTES) return

        val sortedByOldest = files.sortedBy { it.lastModified() }
        for (file in sortedByOldest) {
            if (totalBytes <= MAX_CACHE_BYTES) break
            val length = file.length()
            if (file.delete()) totalBytes -= length
        }
    }
}
