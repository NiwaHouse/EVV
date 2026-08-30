package com.example.eev.feature.explorer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Card
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.eev.core.model.FileItem
import com.example.eev.core.model.SortOrder
import com.example.eev.core.model.ViewMode
import java.io.File

/**
 * 高機能ファイルエクスプローラ画面のコンポーザブル。
 *
 * [責任]: アドレスバー、検索、表示切替、ファイル・サムネイル表示、指定条件一括ビューワ起動。
 * [影響する状態]: 表示ディレクトリ、並び順、検索フィルタ、レイアウト形式。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerScreen(
    uiState: ExplorerUiState,
    onFolderClick: (File) -> Unit,
    onFileClick: (FileItem) -> Unit,
    onSortChanged: (SortOrder) -> Unit,
    onViewModeChanged: (ViewMode) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onToggleSearch: (Boolean) -> Unit,
    onUpClick: () -> Unit,
    onQuickLaunchViewer: (SortOrder) -> Unit,
    onSettingsClick: () -> Unit
) {
    var showSortDialog by remember { mutableStateOf(false) }
    var showQuickLaunchMenu by remember { mutableStateOf(false) }

    if (showSortDialog) {
        SortOptionDialog(
            currentSortOrder = uiState.sortOrder,
            onSortSelected = { order ->
                onSortChanged(order)
                showSortDialog = false
            },
            onDismiss = { showSortDialog = false }
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
                    IconButton(onClick = onUpClick) {
                        Icon(imageVector = Icons.Default.ArrowUpward, contentDescription = "上階層へ戻る")
                    }
                },
                actions = {
                    // クイックビューワ起動メニュー (日付の最初/最後、ファイル名最初/最後)
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
                                text = { Text("📅 日付の最初（最古の画像）") },
                                onClick = {
                                    showQuickLaunchMenu = false
                                    onQuickLaunchViewer(SortOrder.DATE_ASC)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("📅 日付の最後（最新の画像）") },
                                onClick = {
                                    showQuickLaunchMenu = false
                                    onQuickLaunchViewer(SortOrder.DATE_DESC)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("📄 ファイル名の最初（名前昇順）") },
                                onClick = {
                                    showQuickLaunchMenu = false
                                    onQuickLaunchViewer(SortOrder.NAME_ASC)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("📄 ファイル名の最後（名前降順）") },
                                onClick = {
                                    showQuickLaunchMenu = false
                                    onQuickLaunchViewer(SortOrder.NAME_DESC)
                                }
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
                            imageVector = if (uiState.viewMode == ViewMode.LIST) Icons.Default.GridView else Icons.Default.ViewList,
                            contentDescription = "表示切替"
                        )
                    }
                    IconButton(onClick = { showSortDialog = true }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.Sort, contentDescription = "ソート")
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
            BreadcrumbBar(
                currentDirectory = uiState.currentDirectory,
                onDirectoryClick = onFolderClick
            )

            Box(modifier = Modifier.fillMaxSize()) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (uiState.filteredFileItems.isEmpty()) {
                    Text(
                        text = if (uiState.searchQuery.isNotBlank()) "一致する項目がありません" else "フォルダ内は空です",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    if (uiState.viewMode == ViewMode.LIST) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(uiState.filteredFileItems) { item ->
                                FileListItemRow(
                                    item = item,
                                    onClick = { if (item.isDirectory) onFolderClick(item.file) else onFileClick(item) }
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(8.dp)
                        ) {
                            items(uiState.filteredFileItems) { item ->
                                FileGridItemCell(
                                    item = item,
                                    onClick = { if (item.isDirectory) onFolderClick(item.file) else onFileClick(item) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BreadcrumbBar(
    currentDirectory: File,
    onDirectoryClick: (File) -> Unit
) {
    val pathParts = remember(currentDirectory) {
        val parts = mutableListOf<File>()
        var curr: File? = currentDirectory
        while (curr != null) {
            parts.add(0, curr)
            curr = curr.parentFile
        }
        parts
    }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            IconButton(
                onClick = { pathParts.firstOrNull()?.let { onDirectoryClick(it) } },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(imageVector = Icons.Default.Home, contentDescription = "ホーム", modifier = Modifier.size(20.dp))
            }
        }
        items(pathParts) { file ->
            Text(
                text = " > ${file.name.ifEmpty { "root" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { onDirectoryClick(file) }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
fun FileListItemRow(
    item: FileItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FileThumbnailIcon(item = item, size = 48)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            if (item.isDirectory && item.imageCount > 0) {
                Text(text = "画像: ${item.imageCount}枚", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun FileGridItemCell(
    item: FileItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .padding(6.dp)
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(8.dp)
        ) {
            FileThumbnailIcon(item = item, size = 72)
            Spacer(modifier = Modifier.size(4.dp))
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
        }
    }
}

@Composable
fun FileThumbnailIcon(item: FileItem, size: Int) {
    if (item.isDirectory) {
        if (item.firstImagePath != null) {
            AsyncImage(
                model = item.firstImagePath,
                contentDescription = "サムネイル",
                modifier = Modifier.size(size.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(imageVector = Icons.Default.Folder, contentDescription = "フォルダ", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(size.dp))
        }
    } else {
        val ext = item.file.extension.lowercase()
        if (listOf("jpg", "jpeg", "png", "webp", "bmp", "gif").contains(ext)) {
            AsyncImage(
                model = item.file.absolutePath,
                contentDescription = "画像",
                modifier = Modifier.size(size.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(imageVector = Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = "ファイル", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(size.dp))
        }
    }
}
