package com.example.eev.feature.explorer

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.example.eev.core.model.FileItem

/**
 * ファイルまたはフォルダの削除直前に表示する最終確認ダイアログ。
 *
 * [責任]: 削除対象の確認および誤操作防止。
 * [影響する状態]: ユーザー承認時の削除処理実行トリガー。
 * [潜在的エラー]: なし。
 */
@Composable
fun DeleteConfirmDialog(
    item: FileItem,
    onDismissRequest: () -> Unit,
    onConfirmDelete: () -> Unit
) {
    val message = if (item.isDirectory) {
        "フォルダ「${item.name}」を完全に削除しますか？\n\n※フォルダ内のすべてのファイルおよびサブフォルダも削除されます。\n※この操作は取り消せません。"
    } else {
        "ファイル「${item.name}」を完全に削除しますか？\n\n※この操作は取り消せません。"
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = if (item.isDirectory) "フォルダの削除" else "ファイルの削除",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirmDelete()
                    onDismissRequest()
                },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("削除する")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("キャンセル")
            }
        }
    )
}
