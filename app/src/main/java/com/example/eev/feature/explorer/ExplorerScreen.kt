package com.example.eev.feature.explorer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.eev.core.model.FileItem
import com.example.eev.core.model.SortOrder
import com.example.eev.core.model.ThumbnailSize
import com.example.eev.core.model.ViewMode
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import java.io.File

private const val SCROLL_SAVE_DEBOUNCE_MS = 300L

/**
 * 高機能ファイルエクスプローラ画面のコンポーザブル。
 *
 * [責任]: アドレスバー、本棚画面遷移、検索、個別ソート、長押しメニュー（お気に入り・削除）、ファイル表示。
 * [影響する状態]: 表示ディレクトリ、並び順、お気に入り登録、削除実行。
 * [潜在的エラー]: なし。
 */
@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun ExplorerScreen(
    uiState: ExplorerUiState,
    onFolderClick: (File) -> Unit,
    onFileClick: (FileItem) -> Unit,
    onFolderSortChanged: (SortOrder) -> Unit,
    onFileSortChanged: (SortOrder) -> Unit,
    onViewModeChanged: (ViewMode) -> Unit,
    onThumbnailSizeChanged: (ThumbnailSize) -> Unit,
    onShowItemInfoChanged: (Boolean) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onToggleSearch: (Boolean) -> Unit,
    onUpClick: () -> Unit,
    onBackClick: () -> Unit,
    onQuickLaunchViewer: (SortOrder) -> Unit,
    onBookshelfClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onToggleFavorite: (FileItem) -> Unit,
    onDeleteItem: (FileItem) -> Unit,
    savedScrollPositions: Map<String, Int> = emptyMap(),
    onScrollPositionChanged: (String, Int) -> Unit = { _, _ -> }
) {
    var showSortDialog by remember { mutableStateOf(false) }
    var showDisplayDialog by remember { mutableStateOf(false) }
    var showQuickLaunchMenu by remember { mutableStateOf(false) }
    var selectedItemForMenu by remember { mutableStateOf<FileItem?>(null) }
    var itemToDelete by remember { mutableStateOf<FileItem?>(null) }

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

    selectedItemForMenu?.let { item ->
        ItemContextMenuDialog(
            item = item,
            isFavorite = uiState.favoriteFolders.contains(item.file.absolutePath),
            onDismissRequest = { selectedItemForMenu = null },
            onToggleFavorite = { onToggleFavorite(item) },
            onRequestDelete = { itemToDelete = item }
        )
    }

    itemToDelete?.let { item ->
        DeleteConfirmDialog(
            item = item,
            onDismissRequest = { itemToDelete = null },
            onConfirmDelete = { onDeleteItem(item) }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (uiState.isSearchActive) {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = onSearchQueryChanged,
                            placeholder = { Text("ファイル名で検索...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(text = uiState.currentDirectory.name.ifEmpty { "ストレージ" }, maxLines = 1)
                    }
                },
                navigationIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBackClick, enabled = uiState.canNavigateBack) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                        }
                        IconButton(onClick = onUpClick) {
                            Icon(imageVector = Icons.Default.ArrowUpward, contentDescription = "上階層へ")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onBookshelfClick) {
                        Icon(
                            imageVector = Icons.Filled.AutoStories,
                            contentDescription = "本棚（お気に入り）",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Box {
                        IconButton(onClick = { showQuickLaunchMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "条件指定ビューワ起動",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        DropdownMenu(
                            expanded = showQuickLaunchMenu,
                            onDismissRequest = { showQuickLaunchMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("📅 日付の最初（最古）") },
                                onClick = { showQuickLaunchMenu = false; onQuickLaunchViewer(SortOrder.DATE_ASC) }
                            )
                            DropdownMenuItem(
                                text = { Text("📅 日付の最後（最新）") },
                                onClick = { showQuickLaunchMenu = false; onQuickLaunchViewer(SortOrder.DATE_DESC) }
                            )
                            DropdownMenuItem(
                                text = { Text("📄 ファイル名の最初（昇順）") },
                                onClick = { showQuickLaunchMenu = false; onQuickLaunchViewer(SortOrder.NAME_ASC) }
                            )
                            DropdownMenuItem(
                                text = { Text("📄 ファイル名の最後（降順）") },
                                onClick = { showQuickLaunchMenu = false; onQuickLaunchViewer(SortOrder.NAME_DESC) }
                            )
                        }
                    }
                    IconButton(onClick = { onToggleSearch(!uiState.isSearchActive) }) {
                        Icon(
                            imageVector = if (uiState.isSearchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "検索"
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
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            BreadcrumbBar(currentDirectory = uiState.currentDirectory, onDirectoryClick = onFolderClick)

            Box(modifier = Modifier.fillMaxSize()) {
                if (uiState.isLoading && uiState.filteredFileItems.isEmpty()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (uiState.filteredFileItems.isEmpty()) {
                    Text(
                        text = if (uiState.searchQuery.isNotBlank()) "一致する項目がありません" else "フォルダ内は空です",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    val folderPath = uiState.currentDirectory.absolutePath
                    val savedIndex = savedScrollPositions[folderPath] ?: 0

                    if (uiState.viewMode == ViewMode.LIST) {
                        val listState = rememberLazyListState()
                        LaunchedEffect(folderPath, uiState.filteredFileItems.size) {
                            if (savedIndex in 0 until uiState.filteredFileItems.size) {
                                listState.scrollToItem(savedIndex)
                            }
                        }
                        LaunchedEffect(listState, folderPath) {
                            snapshotFlow { listState.firstVisibleItemIndex }
                                .distinctUntilChanged()
                                .debounce(SCROLL_SAVE_DEBOUNCE_MS)
                                .collect { index -> onScrollPositionChanged(folderPath, index) }
                        }
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                            items(uiState.filteredFileItems) { item ->
                                FileListItemRow(
                                    item = item,
                                    thumbnailSize = uiState.thumbnailSize,
                                    showItemInfo = uiState.showItemInfo,
                                    onClick = { if (item.isDirectory) onFolderClick(item.file) else onFileClick(item) },
                                    onLongClick = { selectedItemForMenu = item }
                                )
                            }
                        }
                    } else {
                        val gridState = rememberLazyGridState()
                        LaunchedEffect(folderPath, uiState.filteredFileItems.size) {
                            if (savedIndex in 0 until uiState.filteredFileItems.size) {
                                gridState.scrollToItem(savedIndex)
                            }
                        }
                        LaunchedEffect(gridState, folderPath) {
                            snapshotFlow { gridState.firstVisibleItemIndex }
                                .distinctUntilChanged()
                                .debounce(SCROLL_SAVE_DEBOUNCE_MS)
                                .collect { index -> onScrollPositionChanged(folderPath, index) }
                        }
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Fixed(uiState.thumbnailSize.gridColumns),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(8.dp)
                        ) {
                            items(uiState.filteredFileItems) { item ->
                                FileGridItemCell(
                                    item = item,
                                    thumbnailSize = uiState.thumbnailSize,
                                    showItemInfo = uiState.showItemInfo,
                                    onClick = { if (item.isDirectory) onFolderClick(item.file) else onFileClick(item) },
                                    onLongClick = { selectedItemForMenu = item }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
