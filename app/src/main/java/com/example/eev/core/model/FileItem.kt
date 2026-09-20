package com.example.eev.core.model

import java.io.File

/**
 * エクスプローラ上で扱うファイルまたはフォルダの情報を保持するデータモデル。
 *
 * [責任]: ファイル・フォルダのパス、名前、種別、更新日時、内包画像数、翻訳 DB 有無の保持。
 * [影響する状態]: 一覧画面での表示項目、サムネイル表示可否、翻訳 DB チェックバッジ表示可否、ソート基準。
 * [潜在的エラー]: なし。
 */
data class FileItem(
    val file: File,
    val isDirectory: Boolean,
    val name: String = file.name,
    val lastModified: Long = file.lastModified(),
    val imageCount: Int = 0,
    val firstImagePath: String? = null,
    /** 直下に trans_image 翻訳 DB（`.trans_image.db`）が存在するか。フォルダのチェックバッジ表示に用いる。 */
    val hasTransDb: Boolean = false
)
