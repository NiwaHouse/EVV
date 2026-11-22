package com.example.eev.feature.bookshelf

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.eev.core.model.BookshelfCategory
import com.example.eev.core.model.FavoriteFolder

/**
 * 本棚ヘッダー直下に表示する横スクロール可能なカテゴリ絞り込みチップバー（複数選択AND検索対応）。
 *
 * [責任]: 「すべて」「未分類」および各登録カテゴリの複数選択状態表示とトグル切り替え。
 * [影響する状態]: [BookshelfUiState.selectedCategoryIds]。
 * [潜在的エラー]: なし。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookshelfCategoryFilterBar(
    categories: List<BookshelfCategory>,
    selectedCategoryIds: Set<Long>,
    onToggleCategory: (Long?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = selectedCategoryIds.isEmpty(),
            onClick = { onToggleCategory(null) },
            label = { Text("すべて") }
        )
        FilterChip(
            selected = selectedCategoryIds.contains(UNCATEGORIZED_CATEGORY_ID),
            onClick = { onToggleCategory(UNCATEGORIZED_CATEGORY_ID) },
            label = { Text("未分類") }
        )
        categories.forEach { category ->
            FilterChip(
                selected = selectedCategoryIds.contains(category.id),
                onClick = { onToggleCategory(category.id) },
                label = { Text(category.name) }
            )
        }
    }
}

/**
 * カテゴリの登録・名前変更・削除を行う管理ダイアログ。
 *
 * [責任]: 本棚カテゴリマスタの追加・編集・削除UIの提供。
 * [影響する状態]: `bookshelf_categories` テーブルおよび [BookshelfUiState.categories]。
 * [潜在的エラー]: なし。
 */
@Composable
fun BookshelfCategoryManageDialog(
    categories: List<BookshelfCategory>,
    onAddCategory: (String) -> Unit,
    onRenameCategory: (Long, String) -> Unit,
    onDeleteCategory: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var newCategoryName by remember { mutableStateOf("") }
    var editingCategoryId by remember { mutableStateOf<Long?>(null) }
    var editingName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("カテゴリ登録・管理", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        placeholder = { Text("新しいカテゴリ名") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            val trimmed = newCategoryName.trim()
                            if (trimmed.isNotEmpty()) {
                                onAddCategory(trimmed)
                                newCategoryName = ""
                            }
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "カテゴリ追加")
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                if (categories.isEmpty()) {
                    Text(
                        text = "登録されているカテゴリはありません。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        categories.forEach { category ->
                            if (editingCategoryId == category.id) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = editingName,
                                        onValueChange = { editingName = it },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = {
                                        val trimmed = editingName.trim()
                                        if (trimmed.isNotEmpty()) {
                                            onRenameCategory(category.id, trimmed)
                                        }
                                        editingCategoryId = null
                                    }) {
                                        Icon(Icons.Default.Check, contentDescription = "保存")
                                    }
                                    IconButton(onClick = { editingCategoryId = null }) {
                                        Icon(Icons.Default.Close, contentDescription = "キャンセル")
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = category.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    IconButton(onClick = {
                                        editingCategoryId = category.id
                                        editingName = category.name
                                    }) {
                                        Icon(Icons.Default.Edit, contentDescription = "名前変更")
                                    }
                                    IconButton(onClick = { onDeleteCategory(category.id) }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "削除",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("閉じる") }
        }
    )
}

/**
 * フォルダ長押し時に表示する「複数カテゴリ選択＋新規カテゴリ作成＋お気に入り解除」統合ダイアログ。
 *
 * [責任]: 対象フォルダへの複数カテゴリ割り当て、その場でのカテゴリ新規追加、およびお気に入り解除導線の提供。
 * [影響する状態]: 対象フォルダの所属カテゴリ一覧、またはお気に入り登録状態。
 * [潜在的エラー]: なし。
 */
@Composable
fun BookshelfFolderCategoryDialog(
    folder: FavoriteFolder,
    categories: List<BookshelfCategory>,
    onAddCategoryAndSelect: (String, (BookshelfCategory?) -> Unit) -> Unit,
    onSaveCategories: (Set<Long>) -> Unit,
    onRequestRemoveFavorite: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedIds by remember(folder.file.absolutePath) { mutableStateOf(folder.categoryIds) }
    var newCategoryName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "「${folder.name}」のカテゴリ設定",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        placeholder = { Text("新規カテゴリを追加") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = {
                            val trimmed = newCategoryName.trim()
                            if (trimmed.isNotEmpty()) {
                                newCategoryName = ""
                                onAddCategoryAndSelect(trimmed) { created ->
                                    if (created != null) {
                                        selectedIds = selectedIds + created.id
                                    }
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "新規カテゴリ追加")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(6.dp))

                if (categories.isEmpty()) {
                    Text(
                        text = "上の入力欄からカテゴリを追加してください。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        categories.forEach { category ->
                            val isChecked = selectedIds.contains(category.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedIds = if (isChecked) {
                                            selectedIds - category.id
                                        } else {
                                            selectedIds + category.id
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        selectedIds = if (checked) {
                                            selectedIds + category.id
                                        } else {
                                            selectedIds - category.id
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = category.name,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onRequestRemoveFavorite,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("お気に入りから解除")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSaveCategories(selectedIds)
                onDismiss()
            }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}
