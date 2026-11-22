package com.example.eev.core.model

import java.io.File

/**
 * 本棚画面でお気に入りフォルダを表示するためのデータモデル。
 *
 * [責任]: お気に入り登録されたフォルダのパス、名称、更新日時、先頭画像パス、内包画像数、翻訳DB有無、カスタム並び順インデックスの保持。
 * [影響する状態]: 本棚画面のリスト・グリッドアイテム描画、ソート、タップ時の画像ビューワ起動パラメータ。
 * [潜在的エラー]: なし。
 */
data class FavoriteFolder(
    val file: File,
    val name: String = file.name,
    val lastModified: Long = file.lastModified(),
    val firstImagePath: String? = null,
    val imageCount: Int = 0,
    val hasTransDb: Boolean = false,
    val customOrder: Int = Int.MAX_VALUE,
    val categoryIds: Set<Long> = emptySet(),
    val categoryNames: List<String> = emptyList()
) {
    /**
     * 共通リスト／グリッド描画コンポーネントやソート処理と連携するための [FileItem] へ変換する。
     *
     * [責任]: [FavoriteFolder] から [FileItem] への写像。
     * [影響する状態]: なし（戻り値のみ）。
     * [潜在的エラー]: なし。
     */
    fun toFileItem(): FileItem = FileItem(
        file = file,
        isDirectory = true,
        name = name,
        lastModified = lastModified,
        imageCount = imageCount,
        firstImagePath = firstImagePath,
        hasTransDb = hasTransDb
    )
}
