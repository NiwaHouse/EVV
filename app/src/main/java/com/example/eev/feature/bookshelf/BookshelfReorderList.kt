package com.example.eev.feature.bookshelf

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.eev.core.model.FavoriteFolder
import com.example.eev.core.model.ThumbnailSize
import java.io.File

/**
 * ドラッグ＆ドロップによる並び替えに対応した本棚のリスト表示コンポーネント。
 *
 * 各行右端のドラッグハンドルを上下にドラッグすることでフォルダ順序を入れ替え、
 * ドロップ完了時に [onDragFinished] を呼び出して DB へ永続化する。
 *
 * [責任]: リスト表示でのドラッグ＆ドロップ並び替えジェスチャー処理。
 * [影響する状態]: 本棚フォルダの並び順（[onMoveFolder], [onDragFinished]）。
 * [潜在的エラー]: インデックス計算時の例外は `try-catch` で捕捉し安全に無視する。
 */
@Composable
fun BookshelfReorderLazyList(
    folders: List<FavoriteFolder>,
    thumbnailSize: ThumbnailSize,
    showItemInfo: Boolean,
    isReorderEnabled: Boolean,
    onFolderClick: (File) -> Unit,
    onFolderLongClick: (FavoriteFolder) -> Unit,
    onMoveFolder: (String, String) -> Unit,
    onDragFinished: () -> Unit
) {
    val listState = rememberLazyListState()
    var draggingPath by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val currentFolders by rememberUpdatedState(folders)

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
    ) {
        items(folders, key = { it.file.absolutePath }) { folder ->
            val path = folder.file.absolutePath
            val isDragging = draggingPath == path

            val handleModifier = if (isReorderEnabled) {
                Modifier.pointerInput(path) {
                    detectDragGestures(
                        onDragStart = {
                            draggingPath = path
                            dragOffsetY = 0f
                        },
                        onDragEnd = {
                            draggingPath = null
                            dragOffsetY = 0f
                            onDragFinished()
                        },
                        onDragCancel = {
                            draggingPath = null
                            dragOffsetY = 0f
                            onDragFinished()
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            try {
                                dragOffsetY += dragAmount.y
                                val visibleItems = listState.layoutInfo.visibleItemsInfo
                                val currentItem = visibleItems.firstOrNull { it.key == path }
                                    ?: return@detectDragGestures
                                val centerY = currentItem.offset + currentItem.size / 2f + dragOffsetY
                                val targetItem = visibleItems.firstOrNull { item ->
                                    item.key != path &&
                                        centerY >= item.offset &&
                                        centerY <= (item.offset + item.size)
                                }
                                val targetPath = targetItem?.key as? String
                                if (targetPath != null && currentFolders.any { it.file.absolutePath == targetPath }) {
                                    val delta = targetItem.offset - currentItem.offset
                                    onMoveFolder(path, targetPath)
                                    dragOffsetY -= delta
                                }
                            } catch (_: Exception) {}
                        }
                    )
                }
            } else {
                Modifier
            }

            BookshelfListRow(
                folder = folder,
                thumbnailSize = thumbnailSize,
                showItemInfo = showItemInfo,
                isDragging = isDragging,
                modifier = Modifier
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (isDragging) dragOffsetY else 0f
                    },
                dragHandleModifier = handleModifier,
                onClick = { onFolderClick(folder.file) },
                onLongClick = { onFolderLongClick(folder) }
            )
        }
    }
}

/**
 * ドラッグ＆ドロップによる並び替えに対応した本棚のグリッド表示コンポーネント。
 *
 * カードの長押し＆ドラッグで順序を入れ替え、ドラッグせず長押しを離した場合（または右上×ボタン）は
 * お気に入り解除ダイアログを表示する。
 *
 * [責任]: グリッド表示での長押しドラッグ並び替えジェスチャー処理。
 * [影響する状態]: 本棚フォルダの並び順（[onMoveFolder], [onDragFinished]）。
 * [潜在的エラー]: 座標計算時の例外は `try-catch` で捕捉し安全に無視する。
 */
@Composable
fun BookshelfReorderLazyGrid(
    folders: List<FavoriteFolder>,
    thumbnailSize: ThumbnailSize,
    showItemInfo: Boolean,
    isReorderEnabled: Boolean,
    onFolderClick: (File) -> Unit,
    onFolderLongClick: (FavoriteFolder) -> Unit,
    onRequestRemove: (FavoriteFolder) -> Unit = onFolderLongClick,
    onMoveFolder: (String, String) -> Unit,
    onDragFinished: () -> Unit
) {
    val gridState = rememberLazyGridState()
    var draggingPath by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var hasMovedDuringDrag by remember { mutableStateOf(false) }
    val currentFolders by rememberUpdatedState(folders)

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(thumbnailSize.gridColumns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp)
    ) {
        items(folders, key = { it.file.absolutePath }) { folder ->
            val path = folder.file.absolutePath
            val isDragging = draggingPath == path

            BookshelfItemCard(
                folder = folder,
                thumbnailSize = thumbnailSize,
                showItemInfo = showItemInfo,
                isDragging = isDragging,
                modifier = Modifier
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        translationX = if (isDragging) dragOffset.x else 0f
                        translationY = if (isDragging) dragOffset.y else 0f
                    }
                    .then(
                        if (isReorderEnabled) {
                            Modifier.pointerInput(path) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggingPath = path
                                        dragOffset = Offset.Zero
                                        hasMovedDuringDrag = false
                                    },
                                    onDragEnd = {
                                        val moved = hasMovedDuringDrag
                                        draggingPath = null
                                        dragOffset = Offset.Zero
                                        hasMovedDuringDrag = false
                                        if (moved) {
                                            onDragFinished()
                                        } else {
                                            onFolderLongClick(folder)
                                        }
                                    },
                                    onDragCancel = {
                                        draggingPath = null
                                        dragOffset = Offset.Zero
                                        hasMovedDuringDrag = false
                                        onDragFinished()
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        try {
                                            dragOffset += dragAmount
                                            if (dragOffset.getDistance() > 16f) {
                                                hasMovedDuringDrag = true
                                            }
                                            val visibleItems = gridState.layoutInfo.visibleItemsInfo
                                            val currentItem = visibleItems.firstOrNull { it.key == path }
                                                ?: return@detectDragGesturesAfterLongPress
                                            val centerX = currentItem.offset.x + currentItem.size.width / 2f + dragOffset.x
                                            val centerY = currentItem.offset.y + currentItem.size.height / 2f + dragOffset.y
                                            val targetItem = visibleItems.firstOrNull { item ->
                                                item.key != path &&
                                                    centerX >= item.offset.x &&
                                                    centerX <= (item.offset.x + item.size.width) &&
                                                    centerY >= item.offset.y &&
                                                    centerY <= (item.offset.y + item.size.height)
                                            }
                                            val targetPath = targetItem?.key as? String
                                            if (targetPath != null && currentFolders.any { it.file.absolutePath == targetPath }) {
                                                val deltaX = targetItem.offset.x - currentItem.offset.x
                                                val deltaY = targetItem.offset.y - currentItem.offset.y
                                                onMoveFolder(path, targetPath)
                                                dragOffset -= Offset(deltaX.toFloat(), deltaY.toFloat())
                                                hasMovedDuringDrag = true
                                            }
                                        } catch (_: Exception) {}
                                    }
                                )
                            }
                        } else {
                            Modifier
                        }
                    ),
                onClick = { onFolderClick(folder.file) },
                onLongClick = { onFolderLongClick(folder) },
                onRemoveClick = { onRequestRemove(folder) }
            )
        }
    }
}
