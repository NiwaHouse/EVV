package com.example.eev.feature.explorer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.eev.core.model.ThumbnailSize

/**
 * エクスプローラの表示オプション（サムネイルサイズ・情報表示）を選択するダイアログ。
 *
 * [責任]: サムネイルサイズ3段階（大・中・小）の選択と、
 *         日付・枚数などの付加情報表示ON/OFFチェックの受け渡し。
 * [影響する状態]: エクスプローラのサムネイル描画サイズおよび情報表示可否。
 * [潜在的エラー]: なし。
 */
@Composable
fun DisplayOptionDialog(
    currentThumbnailSize: ThumbnailSize,
    currentShowItemInfo: Boolean,
    onThumbnailSizeSelected: (ThumbnailSize) -> Unit,
    onShowItemInfoChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "表示オプション") },
        text = {
            Column {
                Text(text = "サムネイルサイズ", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))
                ThumbnailSize.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onThumbnailSizeSelected(option) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (option == currentThumbnailSize),
                            onClick = { onThumbnailSizeSelected(option) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = option.displayName)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onShowItemInfoChanged(!currentShowItemInfo) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = currentShowItemInfo,
                        onCheckedChange = onShowItemInfoChanged
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "日付・枚数などの情報を表示")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("閉じる")
            }
        }
    )
}
