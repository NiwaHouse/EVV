package com.example.eev.core.storage

/**
 * フォルダ 1 件分のメタ情報を表すデータモデル。
 *
 * [責任]: フォルダ直下の画像数・先頭画像パス・trans_image 翻訳 DB の有無の保持。
 * [影響する状態]: エクスプローラ一覧（[com.example.eev.core.model.FileItem]）の表示内容。
 * [潜在的エラー]: なし（値の生成は [FileStorageManager] が担う）。
 *
 * 3 値のうち 2 値が同型（Int / String?）であるため、位置指定の取り違えを防ぐ目的で
 * [Pair] ではなく本データクラスを用いる。
 *
 * @property imageCount 直下に存在する表示対象画像ファイルの件数。
 * @property firstImagePath 直下で名前順最先の画像ファイルの絶対パス。画像が無い場合は null。
 * @property hasTransDb 直下に trans_image 翻訳 DB（`.trans_image.db`）が存在する場合 true。
 */
data class FolderMetadata(
    val imageCount: Int = 0,
    val firstImagePath: String? = null,
    val hasTransDb: Boolean = false
)
