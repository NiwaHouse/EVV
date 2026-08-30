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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.eev.core.model.SortOrder

/**
 * ソート順（名前順、日付順、画像数順）を選択するためのダイアログ。
 *
 * [責任]: 並び替えオプションの提示およびユーザー選択の受け渡し。
 * [影響する状態]: 表示中のソート設定。
 * [潜在的エラー]: なし。
 */
@Composable
fun SortOptionDialog(
    currentSortOrder: SortOrder,
    onSortSelected: (SortOrder) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "並び替え順を選択") },
        text = {
            Column {
                SortOrder.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSortSelected(option) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (option == currentSortOrder),
                            onClick = { onSortSelected(option) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = option.displayName)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}
