package expo.modules.eevstorage

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import expo.modules.kotlin.functions.Coroutine
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 外部ストレージ走査・権限要求・永続サムネイル生成・再帰削除を提供する極薄ネイティブモジュール。
 *
 * ファイル操作の実処理は [EevStorageOps] へ委譲し、本クラスは Expo Module DSL 公開と
 * OS ストレージ権限処理のみを担う。
 *
 * [責任]: `EevStorage` ネイティブインターフェース公開および全ファイルアクセス権限の確認・要求。
 * [影響する状態]: 端末ストレージ、設定画面インテント起動。
 * [潜在的エラー]: 権限・I/O 例外はすべて `try-catch` で捕捉し安全値へフォールバックする。
 */
class EevStorageModule : Module() {

  companion object {
    private const val TAG = "EevStorageModule"
  }

  override fun definition() = ModuleDefinition {
    Name("EevStorage")

    AsyncFunction("hasAllFilesAccess") {
      checkAllFilesAccess()
    }

    AsyncFunction("requestAllFilesAccess") {
      launchAllFilesAccessSettings()
    }

    AsyncFunction("getInitialRootPath") {
      EevStorageOps.resolveInitialRootPath(getSafeContext())
    }

    AsyncFunction("listDirectory") Coroutine { path: String, showHidden: Boolean ->
      withContext(Dispatchers.IO) {
        EevStorageOps.readDirectoryEntries(path, showHidden)
      }
    }

    AsyncFunction("getFolderMetadata") Coroutine { path: String, showHidden: Boolean, thumbDirName: String ->
      withContext(Dispatchers.IO) {
        EevStorageOps.computeFolderMetadata(getSafeContext(), path, showHidden, thumbDirName)
      }
    }

    AsyncFunction("listImagesInDirectory") Coroutine { path: String, showHidden: Boolean ->
      withContext(Dispatchers.IO) {
        EevStorageOps.readImagesInDirectory(path, showHidden)
      }
    }

    AsyncFunction("listSubDirectories") Coroutine { path: String, showHidden: Boolean ->
      withContext(Dispatchers.IO) {
        EevStorageOps.readSubDirectories(path, showHidden)
      }
    }

    AsyncFunction("checkExistingFiles") Coroutine { paths: List<String> ->
      withContext(Dispatchers.IO) {
        EevStorageOps.checkFilesExist(paths)
      }
    }

    AsyncFunction("deletePath") Coroutine { path: String ->
      withContext(Dispatchers.IO) {
        EevStorageOps.deleteTargetPath(path)
      }
    }

    AsyncFunction("deleteThumbnail") Coroutine { folderPath: String, thumbDirName: String ->
      withContext(Dispatchers.IO) {
        EevStorageOps.removeSingleThumbnail(getSafeContext(), folderPath, thumbDirName)
      }
    }

    AsyncFunction("clearThumbnails") Coroutine { thumbDirName: String ->
      withContext(Dispatchers.IO) {
        EevStorageOps.clearThumbnailDirectory(getSafeContext(), thumbDirName)
      }
    }
  }

  private fun getSafeContext(): Context? {
    return try {
      appContext.reactContext
    } catch (e: Exception) {
      Log.w(TAG, "Context取得に失敗しました", e)
      null
    }
  }

  private fun checkAllFilesAccess(): Boolean {
    return try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
      } else {
        val ctx = getSafeContext() ?: return false
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_EXTERNAL_STORAGE) ==
          PackageManager.PERMISSION_GRANTED
      }
    } catch (e: Exception) {
      Log.w(TAG, "ストレージ権限確認に失敗しました", e)
      false
    }
  }

  private fun launchAllFilesAccessSettings(): Boolean {
    return try {
      val ctx = getSafeContext() ?: return false
      val activity = appContext.currentActivity
      val targetIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Intent(
          Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
          Uri.parse("package:${ctx.packageName}")
        )
      } else {
        Intent(
          Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
          Uri.parse("package:${ctx.packageName}")
        )
      }
      try {
        if (activity != null) {
          activity.startActivity(targetIntent)
        } else {
          targetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          ctx.startActivity(targetIntent)
        }
        true
      } catch (inner: Exception) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
          val fallback = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
            if (activity == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
          (activity ?: ctx).startActivity(fallback)
          true
        } else {
          Log.w(TAG, "権限設定画面を開けませんでした", inner)
          false
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "権限要求処理でエラーが発生しました", e)
      false
    }
  }
}
