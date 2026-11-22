package com.example.eev.feature.explorer

import com.example.eev.core.model.FileItem
import com.example.eev.core.storage.FileStorageManager
import com.example.eev.core.storage.FolderMetadata
import com.example.eev.feature.thumbnail.FolderThumbnailCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * フォルダ一覧のメタデータ（画像数、サムネイル、翻訳DB有無）を非同期バッチ取得するローダー。
 *
 * [責任]: フォルダ直下のメタデータ探索およびバッチ反映の実行。
 * [影響する状態]: 一覧内の各フォルダアイテムの表示情報。
 * [潜在的エラー]: 読み取り失敗時は該当項目をスキップ。
 */
class ExplorerMetadataLoader(
    private val storageManager: FileStorageManager,
    private val thumbnailCache: FolderThumbnailCache?,
    private val batchSize: Int = 8
) {
    /**
     * メタデータを非同期で順次取得し、バッチ単位でコールバックを呼び出す。
     *
     * [責任]: バッチ単位でのメタデータ更新通知。
     * [影響する状態]: 各フォルダのメタ情報。
     * [潜在的エラー]: なし。
     */
    fun loadAsync(
        scope: CoroutineScope,
        items: List<FileItem>,
        showHiddenFiles: Boolean,
        onBatchUpdates: (Map<String, FolderMetadata>) -> Unit
    ) {
        scope.launch(Dispatchers.IO) {
            val updates = mutableMapOf<String, FolderMetadata>()
            var processedSinceFlush = 0

            items.forEach { item ->
                if (item.isDirectory) {
                    val metadata = storageManager.getFolderMetadata(
                        folder = item.file,
                        showHiddenFiles = showHiddenFiles
                    )
                    val thumbnailPath = metadata.firstImagePath?.let { path ->
                        thumbnailCache?.getOrCreateThumbnail(path) ?: path
                    }
                    updates[item.file.absolutePath] = metadata.copy(firstImagePath = thumbnailPath)
                    processedSinceFlush++

                    if (processedSinceFlush >= batchSize) {
                        onBatchUpdates(updates.toMap())
                        updates.clear()
                        processedSinceFlush = 0
                    }
                }
            }

            if (updates.isNotEmpty()) {
                onBatchUpdates(updates.toMap())
            }
        }
    }
}
