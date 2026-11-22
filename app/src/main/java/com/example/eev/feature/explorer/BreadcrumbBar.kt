package com.example.eev.feature.explorer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File

/**
 * 現在のディレクトリ階層をパンくずリスト形式で表示・移動するUIバー。
 *
 * [責任]: 階層パスの分解・表示および特定階層タップによるジャンプナビゲーション。
 * [影響する状態]: タップされたフォルダへのディレクトリ移動。
 * [潜在的エラー]: なし。
 */
@Composable
fun BreadcrumbBar(
    currentDirectory: File,
    onDirectoryClick: (File) -> Unit
) {
    val pathParts = remember(currentDirectory) {
        val parts = mutableListOf<File>()
        var curr: File? = currentDirectory
        while (curr != null) {
            parts.add(0, curr)
            curr = curr.parentFile
        }
        parts
    }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            IconButton(
                onClick = { pathParts.firstOrNull()?.let { onDirectoryClick(it) } },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "ホーム",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        items(pathParts) { file ->
            Text(
                text = " > ${file.name.ifEmpty { "root" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { onDirectoryClick(file) }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}
