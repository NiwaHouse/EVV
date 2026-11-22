package com.example.eev.feature.explorer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.eev.core.model.FileItem
import com.example.eev.core.model.ThumbnailSize
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ITEM_INFO_DATE_FORMAT = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())

/**
 * ファイルエクスプローラのリスト表示行コンポーネント。
 *
 * [責任]: リスト形式でのファイル・フォルダ表示、クリックおよび長押し操作の受け付け。
 * [影響する状態]: タップ時の画面遷移、長押し時のコンテキストメニュー表示。
 * [潜在的エラー]: なし。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListItemRow(
    item: FileItem,
    thumbnailSize: ThumbnailSize,
    showItemInfo: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FileThumbnailIcon(item = item, size = thumbnailSize.listSizeDp)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            if (showItemInfo) {
                Text(
                    text = buildItemInfoText(item),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/**
 * ファイルエクスプローラのグリッド表示セルコンポーネント。
 *
 * [責任]: グリッド形式でのファイル・フォルダ表示、クリックおよび長押し操作の受け付け。
 * [影響する状態]: タップ時の画面遷移、長押し時のコンテキストメニュー表示。
 * [潜在的エラー]: なし。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGridItemCell(
    item: FileItem,
    thumbnailSize: ThumbnailSize,
    showItemInfo: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .padding(6.dp)
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(8.dp)
        ) {
            FileThumbnailIcon(item = item, size = thumbnailSize.gridSizeDp)
            Spacer(modifier = Modifier.size(4.dp))
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
            if (showItemInfo) {
                Text(
                    text = buildItemInfoText(item),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * 一覧項目に表示する付加情報（日付・画像枚数）の文字列を生成する。
 */
private fun buildItemInfoText(item: FileItem): String {
    val dateText = ITEM_INFO_DATE_FORMAT.format(Date(item.lastModified))
    return if (item.isDirectory) {
        "画像: ${item.imageCount}枚 / $dateText"
    } else {
        dateText
    }
}
