package com.example.eev.feature.bookshelf

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.eev.core.model.BookshelfCategory
import com.example.eev.core.model.FavoriteFolder
import com.example.eev.core.model.SortOrder
import com.example.eev.core.model.ThumbnailSize
import com.example.eev.core.model.ViewMode
import com.example.eev.feature.explorer.DisplayOptionDialog
import com.example.eev.feature.explorer.SortOptionDialog
import java.io.File

/**
 * 本棚画面のコンポーザブル。
 *
 * [責任]: お気に入りフォルダ一覧のリスト・グリッド表示、カテゴリ絞り込み・管理、フォルダ長押しカテゴリ選択、ドラッグ＆ドロップ並び替え、検索、各種設定遷移。
 * [影響する状態]: 選択フォルダのビューワ表示、カテゴリ管理・割り当て、お気に入り解除、カスタム並び順（`bookshelf.db`）、共有設定値。
 * [潜在的エラー]: なし。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookshelfScreen(
    uiState: BookshelfUiState,
    onBackClick: () -> Unit,
    onFolderClick: (File) -> Unit,
    onRemoveFavorite: (String) -> Unit,
    onMoveFolder: (String, String) -> Unit = { _, _ -> },
    onDragFinished: () -> Unit = {},
    onSelectCategoryFilter: (Long?) -> Unit = {},
    onAddCategory: (String, (BookshelfCategory?) -> Unit) -> Unit = { _, _ -> },
    onRenameCategory: (Long, String) -> Unit = { _, _ -> },
    onDeleteCategory: (Long) -> Unit = {},
    onUpdateFolderCategories: (String, Set<Long>) -> Unit = { _, _ -> },
    onFolderSortChanged: (SortOrder) -> Unit = {},
    onFileSortChanged: (SortOrder) -> Unit = {},
    onViewModeChanged: (ViewMode) -> Unit = {},
    onThumbnailSizeChanged: (ThumbnailSize) -> Unit = {},
    onShowItemInfoChanged: (Boolean) -> Unit = {},
    onSearchQueryChanged: (String) -> Unit = {},
    onToggleSearch: (Boolean) -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    var folderToRemove by remember { mutableStateOf<FavoriteFolder?>(null) }
    var folderForCategory by remember { mutableStateOf<FavoriteFolder?>(null) }
    var showCategoryManageDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showDisplayDialog by remember { mutableStateOf(false) }

    if (showCategoryManageDialog) {
        BookshelfCategoryManageDialog(
            categories = uiState.categories,
            onAddCategory = { name -> onAddCategory(name) {} },
            onRenameCategory = onRenameCategory,
            onDeleteCategory = onDeleteCategory,
            onDismiss = { showCategoryManageDialog = false }
        )
    }

    folderForCategory?.let { target ->
        val latestFolder = uiState.favoriteFolders.firstOrNull {
            it.file.absolutePath == target.file.absolutePath
        } ?: target
        BookshelfFolderCategoryDialog(
            folder = latestFolder,
            categories = uiState.categories,
            onAddCategoryAndSelect = onAddCategory,
            onSaveCategories = { selectedIds ->
                onUpdateFolderCategories(latestFolder.file.absolutePath, selectedIds)
            },
            onRequestRemoveFavorite = {
                folderForCategory = null
                folderToRemove = latestFolder
            },
            onDismiss = { folderForCategory = null }
        )
    }

    if (showSortDialog) {
        SortOptionDialog(
            currentFolderSortOrder = uiState.folderSortOrder,
            currentFileSortOrder = uiState.fileSortOrder,
            onFolderSortSelected = onFolderSortChanged,
            onFileSortSelected = onFileSortChanged,
            onDismiss = { showSortDialog = false }
        )
    }

    if (showDisplayDialog) {
        DisplayOptionDialog(
            currentThumbnailSize = uiState.thumbnailSize,
            currentShowItemInfo = uiState.showItemInfo,
            onThumbnailSizeSelected = onThumbnailSizeChanged,
            onShowItemInfoChanged = onShowItemInfoChanged,
            onDismiss = { showDisplayDialog = false }
        )
    }

    folderToRemove?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderToRemove = null },
            title = { Text("お気に入りの解除") },
            text = { Text("「${folder.name}」をお気に入り（本棚）から解除しますか？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveFavorite(folder.file.absolutePath)
                        folderToRemove = null
                    }
                ) {
                    Text("解除する")
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToRemove = null }) {
                    Text("キャンセル")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TopAppBar(
                    title = {
                        if (uiState.isSearchActive) {
                            OutlinedTextField(
                                value = uiState.searchQuery,
                                onValueChange = onSearchQueryChanged,
                                placeholder = { Text("フォルダ名で検索...") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(text = "本棚（お気に入り）", maxLines = 1)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "戻る"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { onToggleSearch(!uiState.isSearchActive) }) {
                            Icon(
                                imageVector = if (uiState.isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "検索"
                            )
                        }
                        IconButton(onClick = { showCategoryManageDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Category,
                                contentDescription = "カテゴリ管理"
                            )
                        }
                        IconButton(onClick = {
                            val nextMode = if (uiState.viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST
                            onViewModeChanged(nextMode)
                        }) {
                            Icon(
                                imageVector = if (uiState.viewMode == ViewMode.LIST) Icons.Default.GridView else Icons.AutoMirrored.Filled.ViewList,
                                contentDescription = "表示切替"
                            )
                        }
                        IconButton(onClick = { showSortDialog = true }) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.Sort, contentDescription = "ソート")
                        }
                        IconButton(onClick = { showDisplayDialog = true }) {
                            Icon(imageVector = Icons.Default.Tune, contentDescription = "表示オプション")
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(imageVector = Icons.Default.Settings, contentDescription = "設定")
                        }
                    }
                )
                BookshelfCategoryFilterBar(
                    categories = uiState.categories,
                    selectedCategoryIds = uiState.selectedCategoryIds,
                    onToggleCategory = onSelectCategoryFilter
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isReorderEnabled = uiState.searchQuery.isBlank() && uiState.selectedCategoryIds.isEmpty()
            if (uiState.isLoading && uiState.filteredFavoriteFolders.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.filteredFavoriteFolders.isEmpty()) {
                Text(
                    text = if (uiState.searchQuery.isNotBlank() || uiState.selectedCategoryIds.isNotEmpty()) {
                        "該当するフォルダがありません"
                    } else {
                        "お気に入りに登録されたフォルダがありません。\nフォルダを長押しして「お気に入りに追加」できます。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                )
            } else if (uiState.viewMode == ViewMode.LIST) {
                BookshelfReorderLazyList(
                    folders = uiState.filteredFavoriteFolders,
                    thumbnailSize = uiState.thumbnailSize,
                    showItemInfo = uiState.showItemInfo,
                    isReorderEnabled = isReorderEnabled,
                    onFolderClick = onFolderClick,
                    onFolderLongClick = { folderForCategory = it },
                    onMoveFolder = onMoveFolder,
                    onDragFinished = onDragFinished
                )
            } else {
                BookshelfReorderLazyGrid(
                    folders = uiState.filteredFavoriteFolders,
                    thumbnailSize = uiState.thumbnailSize,
                    showItemInfo = uiState.showItemInfo,
                    isReorderEnabled = isReorderEnabled,
                    onFolderClick = onFolderClick,
                    onFolderLongClick = { folderForCategory = it },
                    onRequestRemove = { folderToRemove = it },
                    onMoveFolder = onMoveFolder,
                    onDragFinished = onDragFinished
                )
            }
        }
    }
}
