package com.example.eev.feature.explorer

import android.os.Environment
import com.example.eev.core.model.FileItem
import com.example.eev.core.model.SortOrder
import com.example.eev.core.model.ThumbnailSize
import com.example.eev.core.model.ViewMode
import java.io.File

/**
 * ファイルエクスプローラ画面のUI State。
 *
 * [責任]: エクスプローラ画面の描画に必要な全状態を保持。
 * [影響する状態]: 一覧表示、ソート順、お気に入り状態、表示モード、検索状態。
 * [潜在的エラー]: なし。
 */
data class ExplorerUiState(
    val currentDirectory: File = Environment.getExternalStorageDirectory(),
    val fileItems: List<FileItem> = emptyList(),
    val folderSortOrder: SortOrder = SortOrder.NAME_ASC,
    val fileSortOrder: SortOrder = SortOrder.NAME_ASC,
    val favoriteFolders: Set<String> = emptySet(),
    val viewMode: ViewMode = ViewMode.LIST,
    val showHiddenFiles: Boolean = false,
    /** サムネイル表示サイズ（大・中・小）。 */
    val thumbnailSize: ThumbnailSize = ThumbnailSize.MEDIUM,
    /** 日付・枚数などの付加情報を表示するか。 */
    val showItemInfo: Boolean = false,
    /** 戻るボタンで前のフォルダへ戻れるか（履歴が存在するか）。 */
    val canNavigateBack: Boolean = false,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val isLoading: Boolean = false
) {
    /** 旧互換用プロパティ（フォルダのソート順を返す）。 */
    val sortOrder: SortOrder
        get() = folderSortOrder

    val filteredFileItems: List<FileItem>
        get() = if (searchQuery.isBlank()) {
            fileItems
        } else {
            fileItems.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
}
