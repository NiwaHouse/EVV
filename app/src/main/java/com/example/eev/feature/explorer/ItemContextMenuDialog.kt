package com.example.eev.feature.explorer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.eev.core.model.FileItem

/**
 * ファイル・フォルダの長押し時に表示される操作コンテキストメニューダイアログ。
 *
 * [責任]: 対象アイテムに対する「お気に入り登録/解除」「削除」操作の選択UIを提供。
 * [影響する状態]: お気に入り登録状態、削除確認ダイアログの表示状態。
 * [潜在的エラー]: なし。
 */
@Composable
fun ItemContextMenuDialog(
    item: FileItem,
    isFavorite: Boolean,
    onDismissRequest: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRequestDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = item.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (item.isDirectory) {
                    ContextMenuItemRow(
                        icon = if (isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                        title = if (isFavorite) "お気に入りを解除" else "お気に入りに追加",
                        onClick = {
                            onToggleFavorite()
                            onDismissRequest()
                        }
                    )
                }
                ContextMenuItemRow(
                    icon = Icons.Default.Delete,
                    title = "削除",
                    isDestructive = true,
                    onClick = {
                        onDismissRequest()
                        onRequestDelete()
                    }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("閉じる")
            }
        }
    )
}

/**
 * コンテキストメニュー内の各操作項目行。
 *
 * [責任]: アイコンと操作タイトルの横並び配置およびクリックイベントの処理。
 * [影響する状態]: 行タップ時の各アクション発火。
 * [潜在的エラー]: なし。
 */
@Composable
private fun ContextMenuItemRow(
    icon: ImageVector,
    title: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = color
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            color = color,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
