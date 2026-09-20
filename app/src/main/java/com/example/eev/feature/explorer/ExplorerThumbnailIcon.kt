package com.example.eev.feature.explorer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.eev.core.model.FileItem

/**
 * 翻訳 DB チェックバッジのサイズ（dp）。サムネイル・アイコンの右上に重ねる。
 */
private const val TRANS_DB_BADGE_SIZE_DP = 16

/**
 * 翻訳 DB チェックバッジの色（緑）。白抜きのチェックが重なるため濃い緑を用いる。
 */
private val TRANS_DB_BADGE_COLOR = Color(0xFF2E7D32)

/**
 * 一覧項目のサムネイルまたは種別アイコンを描画する。
 *
 * [責任]: フォルダ／ファイル種別に応じたサムネイル・アイコンの選択描画と、
 *          翻訳 DB（`.trans_image.db`）を直下に持つフォルダへのチェックバッジ重畳表示。
 * [影響する状態]: なし（[FileItem] の内容を読み取るのみ。状態を書き換えない）。
 * [潜在的エラー]: サムネイル画像の読み込み失敗時は Coil が空表示となり、例外は送出しない。
 *
 * バッジはフォルダのみを対象とし、ファイルには表示しない。
 * サムネイルが無い（[FileItem.firstImagePath] が null の）フォルダでもフォルダアイコン上に表示する。
 *
 * @param item 表示対象の一覧項目。
 * @param size サムネイル・アイコンの一辺のサイズ（dp）。
 */
@Composable
fun FileThumbnailIcon(item: FileItem, size: Int) {
    Box {
        if (item.isDirectory) {
            if (item.firstImagePath != null) {
                AsyncImage(
                    model = item.firstImagePath,
                    contentDescription = "サムネイル",
                    modifier = Modifier.size(size.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(imageVector = Icons.Default.Folder, contentDescription = "フォルダ", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(size.dp))
            }
        } else {
            val ext = item.file.extension.lowercase()
            if (listOf("jpg", "jpeg", "png", "webp", "bmp", "gif").contains(ext)) {
                AsyncImage(
                    model = item.file.absolutePath,
                    contentDescription = "画像",
                    modifier = Modifier.size(size.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(imageVector = Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = "ファイル", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(size.dp))
            }
        }

        // 直下に trans_image 翻訳 DB を持つフォルダであることを示す緑丸＋白チェックのバッジ。
        if (item.isDirectory && item.hasTransDb) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "翻訳DBあり",
                tint = TRANS_DB_BADGE_COLOR,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(TRANS_DB_BADGE_SIZE_DP.dp)
            )
        }
    }
}
