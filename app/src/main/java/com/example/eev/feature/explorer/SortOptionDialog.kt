package com.example.eev.feature.explorer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.eev.core.model.SortOrder

/**
 * フォルダとファイルの並び順を個別に選択・設定するためのダイアログ。
 *
 * [責任]: フォルダ用・ファイル用ソート順のタブ別選択UIを提供。
 * [影響する状態]: フォルダ並び順およびファイル並び順の設定値。
 * [潜在的エラー]: なし。
 */
@Composable
fun SortOptionDialog(
    currentFolderSortOrder: SortOrder,
    currentFileSortOrder: SortOrder,
    onFolderSortSelected: (SortOrder) -> Unit,
    onFileSortSelected: (SortOrder) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("フォルダの並び順", "ファイルの並び順")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "並び替え設定",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (selectedTab == 0) {
                        // フォルダ用ソート順（全項目）
                        SortOrder.entries.forEach { option ->
                            SortOptionRow(
                                option = option,
                                isSelected = option == currentFolderSortOrder,
                                onSelect = { onFolderSortSelected(option) }
                            )
                        }
                    } else {
                        // ファイル用ソート順（名前・日付）
                        val fileOptions = listOf(
                            SortOrder.NAME_ASC,
                            SortOrder.NAME_DESC,
                            SortOrder.DATE_DESC,
                            SortOrder.DATE_ASC
                        )
                        fileOptions.forEach { option ->
                            SortOptionRow(
                                option = option,
                                isSelected = option == currentFileSortOrder,
                                onSelect = { onFileSortSelected(option) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完了")
            }
        }
    )
}

/**
 * ソート選択行のコンポーネント。
 *
 * [責任]: ラジオボタンとソート表示名の描画およびクリックイベントの処理。
 * [影響する状態]: 各ソート選択イベント。
 * [潜在的エラー]: なし。
 */
@Composable
private fun SortOptionRow(
    option: SortOrder,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onSelect
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = option.displayName,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
