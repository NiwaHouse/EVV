package com.example.eev.feature.viewer

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * 画像ビューワでトリプルタップ時に表示する操作選択モーダル。
 *
 * [責任]: ビューワ終了・次フォルダ移動・先頭画像復帰の3択を提示し、選択結果を通知する。
 * [影響する状態]: なし（表示専用。選択はコールバック経由で呼び出し元が状態を変更する）。
 * [潜在的エラー]: なし。
 *
 * @param onDismiss モーダルを閉じる（何も選択しない）際のコールバック。
 * @param onCloseViewer 「画像ビューワの終了」選択時のコールバック。
 * @param onNextFolder 「次のフォルダに移動」選択時のコールバック。
 * @param onFirstImage 「今のフォルダの最初の画像に戻る」選択時のコールバック。
 */
@Composable
fun ViewerTripleTapDialog(
    onDismiss: () -> Unit,
    onCloseViewer: () -> Unit,
    onNextFolder: () -> Unit,
    onFirstImage: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "操作を選択") },
        text = { Text(text = "実行する操作を選んでください。") },
        confirmButton = {
            TextButton(onClick = onCloseViewer) {
                Text(text = "画像ビューワの終了")
            }
        },
        dismissButton = {
            androidx.compose.foundation.layout.Column {
                TextButton(onClick = onNextFolder) {
                    Text(text = "次のフォルダに移動")
                }
                TextButton(onClick = onFirstImage) {
                    Text(text = "今のフォルダの最初の画像に戻る")
                }
            }
        }
    )
}
