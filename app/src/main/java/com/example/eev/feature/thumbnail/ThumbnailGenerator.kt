package com.example.eev.feature.thumbnail

import android.content.Context
import com.example.eev.core.storage.FileStorageManager
import com.example.eev.plugin.EEVPlugin
import java.io.File

/**
 * フォルダ内の最初の画像を検出・表示するためのサムネイル生成・制御プラグイン。
 *
 * [責任]: フォルダ内画像探索、初回画像縮小パスの導出およびキャッシュ呼び出し。
 * [影響する状態]: ファイルエクスプローラのフォルダアイテム描画用サムネイル画像。
 * [潜在的エラー]: 画像が存在しないフォルダアクセス時の null 返却。
 */
class ThumbnailGenerator(
    private val storageManager: FileStorageManager = FileStorageManager()
) : EEVPlugin {

    override val pluginId: String = "plugin_thumbnail_generator"
    override val displayName: String = "サムネイル生成プラグイン"

    override fun initialize(context: Context) {
        // プラグイン初期化用
    }

    override fun isEnabled(): Boolean = true

    /**
     * フォルダ内の最初に見つかった画像ファイルの絶対パスを取得する。
     */
    fun getFirstImagePath(folder: File): String? {
        if (!folder.isDirectory) return null
        val files = folder.listFiles() ?: return null
        return files.filter { storageManager.isImageFile(it) }
            .minByOrNull { it.name }
            ?.absolutePath
    }
}
