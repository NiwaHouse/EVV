package com.example.eev.feature.bookshelf

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.eev.core.model.FavoriteFolder
import com.example.eev.core.model.ThumbnailSize
import com.example.eev.feature.explorer.FileThumbnailIcon
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 本棚画面に表示するお気に入りフォルダのグリッドカードコンポーネント。
 *
 * サムネイルサイズ・付加情報表示・翻訳DBバッジに対応し、右上にお気に入り解除ボタンを備える。
 *
 * [責任]: グリッド形式でのお気に入りフォルダ描画、タップおよび解除操作の受付。
 * [影響する状態]: タップ時のビューワ起動、解除ボタン押下時のお気に入り解除ダイアログ表示。
 * [潜在的エラー]: なし。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookshelfItemCard(
    folder: FavoriteFolder,
    thumbnailSize: ThumbnailSize = ThumbnailSize.MEDIUM,
    showItemInfo: Boolean = false,
    isDragging: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onRemoveClick: () -> Unit = onLongClick
) {
    Card(
        modifier = modifier
            .padding(6.dp)
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isDragging) 8.dp else 2.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                FileThumbnailIcon(
                    item = folder.toFileItem(),
                    size = thumbnailSize.gridSizeDp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                if (folder.categoryNames.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = folder.categoryNames.joinToString(" / "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
                if (showItemInfo) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formatFolderInfo(folder),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
            IconButton(
                onClick = onRemoveClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(24.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "お気に入り解除",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 本棚画面に表示するお気に入りフォルダのリスト行コンポーネント。
 *
 * 右端にドラッグ＆ドロップ並び替え用のハンドル（`≡`）を備える。
 *
 * [責任]: リスト形式でのお気に入りフォルダ描画、タップ・長押し・ドラッグハンドル操作の提供。
 * [影響する状態]: タップ時のビューワ起動、長押し時の解除ダイアログ、ドラッグ時の順序入れ替え。
 * [潜在的エラー]: なし。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookshelfListRow(
    folder: FavoriteFolder,
    thumbnailSize: ThumbnailSize = ThumbnailSize.MEDIUM,
    showItemInfo: Boolean = false,
    isDragging: Boolean = false,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (isDragging) {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)
                } else {
                    MaterialTheme.colorScheme.surface
                }
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FileThumbnailIcon(
            item = folder.toFileItem(),
            size = thumbnailSize.listSizeDp
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (folder.categoryNames.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = folder.categoryNames.joinToString(" / "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (showItemInfo) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatFolderInfo(folder),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Box(
            modifier = dragHandleModifier
                .padding(start = 8.dp)
                .size(36.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "ドラッグして並び替え",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatFolderInfo(folder: FavoriteFolder): String {
    val dateStr = try {
        SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(folder.lastModified))
    } catch (_: Exception) {
        ""
    }
    return "$dateStr ・ ${folder.imageCount}枚"
}
