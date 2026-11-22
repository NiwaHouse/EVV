package expo.modules.eevstorage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * ストレージ走査・メタデータ算出・永続サムネイル生成・再帰削除の実処理を担うヘルパーオブジェクト。
 *
 * [責任]: ファイルI/Oおよび縮小JPEGサムネイルの `filesDir` 永続化。
 * [影響する状態]: `filesDir/<thumbDirName>` 配下ファイル、削除時は端末ストレージ。
 * [潜在的エラー]: 権限不足・I/Oエラー・画像デコード失敗時は `try-catch` で捕捉し安全値へフォールバックする。
 */
internal object EevStorageOps {
  private const val TAG = "EevStorageOps"
  private const val THUMB_MAX_EDGE = 256
  private const val THUMB_JPEG_QUALITY = 85
  private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heif", "heic")

  fun resolveInitialRootPath(context: Context?): String {
    return try {
      val ext = Environment.getExternalStorageDirectory()
      if (ext != null && ext.exists()) {
        ext.absolutePath
      } else {
        context?.filesDir?.absolutePath ?: "/sdcard"
      }
    } catch (e: Exception) {
      Log.w(TAG, "初期ルートパスの取得に失敗しました", e)
      context?.filesDir?.absolutePath ?: "/sdcard"
    }
  }

  fun readDirectoryEntries(path: String, showHidden: Boolean): List<Map<String, Any?>> {
    return try {
      val dir = File(path)
      if (!dir.exists() || !dir.isDirectory) return emptyList()
      val files = dir.listFiles() ?: return emptyList()
      files.mapNotNull { file ->
        try {
          if (!showHidden && file.name.startsWith(".")) return@mapNotNull null
          val isDir = file.isDirectory
          val isImg = !isDir && isImageFile(file)
          mapOf<String, Any?>(
            "path" to file.absolutePath,
            "name" to file.name,
            "isDirectory" to isDir,
            "lastModified" to file.lastModified().toDouble(),
            "size" to (if (isDir) 0L else file.length()).toDouble(),
            "isImage" to isImg
          )
        } catch (_: Exception) {
          null
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "ディレクトリ一覧取得に失敗しました: $path", e)
      emptyList()
    }
  }

  fun computeFolderMetadata(
    context: Context?,
    path: String,
    showHidden: Boolean,
    thumbDirName: String
  ): Map<String, Any?> {
    val defaultMap = mapOf<String, Any?>(
      "imageCount" to 0,
      "firstImagePath" to null,
      "thumbPath" to null,
      "hasTransDb" to false,
      "hasSubDirs" to false,
      "lastModified" to 0.0
    )
    return try {
      val dir = File(path)
      if (!dir.exists() || !dir.isDirectory) return defaultMap
      val lastModified = dir.lastModified()
      // 翻訳DBの有無は隠しファイル表示設定に関係なく直下を確認する
      val hasTransDb = try {
        File(dir, ".trans_image.db").isFile
      } catch (_: Exception) {
        false
      }
      val children = dir.listFiles() ?: return defaultMap.plus(
        mapOf("hasTransDb" to hasTransDb, "lastModified" to lastModified.toDouble())
      )
      var hasSubDirs = false
      val imageFiles = mutableListOf<File>()
      for (child in children) {
        try {
          if (!showHidden && child.name.startsWith(".")) continue
          if (child.isDirectory) {
            hasSubDirs = true
          } else if (child.isFile && isImageFile(child)) {
            imageFiles.add(child)
          }
        } catch (_: Exception) {
        }
      }
      imageFiles.sortBy { it.name.lowercase() }
      val firstImage = imageFiles.firstOrNull()
      val thumbPath = if (firstImage != null) {
        createOrGetThumbnail(context, path, firstImage, thumbDirName) ?: firstImage.absolutePath
      } else {
        null
      }
      mapOf<String, Any?>(
        "imageCount" to imageFiles.size,
        "firstImagePath" to firstImage?.absolutePath,
        "thumbPath" to thumbPath,
        "hasTransDb" to hasTransDb,
        "hasSubDirs" to hasSubDirs,
        "lastModified" to lastModified.toDouble()
      )
    } catch (e: Exception) {
      Log.w(TAG, "フォルダメタデータ計算に失敗しました: $path", e)
      defaultMap
    }
  }

  fun readImagesInDirectory(path: String, showHidden: Boolean): List<Map<String, Any?>> {
    return try {
      val dir = File(path)
      if (!dir.exists() || !dir.isDirectory) return emptyList()
      val files = dir.listFiles() ?: return emptyList()
      files.mapNotNull { file ->
        try {
          if (!showHidden && file.name.startsWith(".")) return@mapNotNull null
          if (!file.isFile || !isImageFile(file)) return@mapNotNull null
          mapOf<String, Any?>(
            "path" to file.absolutePath,
            "name" to file.name,
            "isDirectory" to false,
            "lastModified" to file.lastModified().toDouble(),
            "size" to file.length().toDouble(),
            "isImage" to true
          )
        } catch (_: Exception) {
          null
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "画像一覧取得に失敗しました: $path", e)
      emptyList()
    }
  }

  fun readSubDirectories(path: String, showHidden: Boolean): List<Map<String, Any?>> {
    return try {
      val dir = File(path)
      if (!dir.exists() || !dir.isDirectory) return emptyList()
      val files = dir.listFiles() ?: return emptyList()
      files.mapNotNull { file ->
        try {
          if (!showHidden && file.name.startsWith(".")) return@mapNotNull null
          if (!file.isDirectory) return@mapNotNull null
          mapOf<String, Any?>(
            "path" to file.absolutePath,
            "name" to file.name,
            "isDirectory" to true,
            "lastModified" to file.lastModified().toDouble(),
            "size" to 0.0,
            "isImage" to false
          )
        } catch (_: Exception) {
          null
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "サブディレクトリ取得に失敗しました: $path", e)
      emptyList()
    }
  }

  fun checkFilesExist(paths: List<String>): Map<String, Boolean> {
    val result = mutableMapOf<String, Boolean>()
    for (p in paths) {
      result[p] = try {
        val f = File(p)
        f.exists() && (f.isDirectory || f.length() > 0L)
      } catch (_: Exception) {
        false
      }
    }
    return result
  }

  fun deleteTargetPath(path: String): Boolean {
    return try {
      val target = File(path)
      if (!target.exists()) return true
      if (target.isDirectory) target.deleteRecursively() else target.delete()
    } catch (e: Exception) {
      Log.e(TAG, "削除に失敗しました: $path", e)
      false
    }
  }

  fun removeSingleThumbnail(context: Context?, folderPath: String, thumbDirName: String): Boolean {
    return try {
      val file = resolveThumbFile(context, folderPath, thumbDirName) ?: return false
      if (!file.exists()) true else file.delete()
    } catch (e: Exception) {
      Log.w(TAG, "サムネイル削除失敗: $folderPath", e)
      false
    }
  }

  fun clearThumbnailDirectory(context: Context?, thumbDirName: String): Boolean {
    return try {
      val ctx = context ?: return false
      val safeDir = sanitizeDirName(thumbDirName)
      val dir = File(ctx.filesDir, safeDir)
      if (!dir.exists()) return true
      dir.deleteRecursively()
    } catch (e: Exception) {
      Log.w(TAG, "サムネイル一括削除失敗: $thumbDirName", e)
      false
    }
  }

  private fun createOrGetThumbnail(
    context: Context?,
    folderPath: String,
    sourceImage: File,
    thumbDirName: String
  ): String? {
    var decoded: Bitmap? = null
    var scaled: Bitmap? = null
    return try {
      val thumbFile = resolveThumbFile(context, folderPath, thumbDirName) ?: return null
      thumbFile.parentFile?.mkdirs()

      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeFile(sourceImage.absolutePath, bounds)
      if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

      var sampleSize = 1
      while (bounds.outWidth / (sampleSize * 2) >= THUMB_MAX_EDGE &&
        bounds.outHeight / (sampleSize * 2) >= THUMB_MAX_EDGE
      ) {
        sampleSize *= 2
      }
      val decodeOpts = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.RGB_565
      }
      decoded = BitmapFactory.decodeFile(sourceImage.absolutePath, decodeOpts) ?: return null

      val maxDim = maxOf(decoded.width, decoded.height)
      val finalBitmap = if (maxDim > THUMB_MAX_EDGE) {
        val ratio = THUMB_MAX_EDGE.toFloat() / maxDim.toFloat()
        val w = (decoded.width * ratio).toInt().coerceAtLeast(1)
        val h = (decoded.height * ratio).toInt().coerceAtLeast(1)
        scaled = Bitmap.createScaledBitmap(decoded, w, h, true)
        scaled
      } else {
        decoded
      }

      val tmpFile = File(thumbFile.parentFile, "${thumbFile.name}.tmp")
      FileOutputStream(tmpFile).use { fos ->
        finalBitmap.compress(Bitmap.CompressFormat.JPEG, THUMB_JPEG_QUALITY, fos)
      }
      if (thumbFile.exists()) thumbFile.delete()
      if (!tmpFile.renameTo(thumbFile)) {
        tmpFile.delete()
        return null
      }
      thumbFile.absolutePath
    } catch (e: Exception) {
      Log.w(TAG, "永続サムネイル生成失敗: ${sourceImage.absolutePath}", e)
      null
    } finally {
      try {
        if (scaled != null && scaled !== decoded) scaled?.recycle()
        decoded?.recycle()
      } catch (_: Exception) {
      }
    }
  }

  private fun resolveThumbFile(
    context: Context?,
    folderPath: String,
    thumbDirName: String
  ): File? {
    return try {
      val ctx = context ?: return null
      val safeDir = sanitizeDirName(thumbDirName)
      val hash = sha256Hex(folderPath)
      File(File(ctx.filesDir, safeDir), "$hash.jpg")
    } catch (_: Exception) {
      null
    }
  }

  private fun sanitizeDirName(raw: String): String {
    val cleaned = raw.replace(Regex("[^a-zA-Z0-9_-]"), "")
    return cleaned.ifEmpty { "folder_thumbs" }
  }

  private fun sha256Hex(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
  }

  private fun isImageFile(file: File): Boolean {
    return IMAGE_EXTENSIONS.contains(file.extension.lowercase())
  }
}
