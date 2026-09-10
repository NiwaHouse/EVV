package com.example.eev.core.model

/**
 * ファイルおよびフォルダのソート順を表す列挙型。
 *
 * [責任]: エクスプローラおよび画像ビューワでの並び替え条件を定義する。
 * [影響する状態]: 画面に表示されるファイル・フォルダのリスト順序。
 */
enum class SortOrder(val displayName: String) {
    NAME_ASC("ファイル名昇順"),
    NAME_DESC("ファイル名降順"),
    DATE_DESC("日時(新しい順)"),
    DATE_ASC("日時(古い順)"),
    IMAGE_COUNT_DESC("画像数順(多い順)"),
    IMAGE_COUNT_ASC("画像数順(少ない順)");

    companion object {
        fun fromName(name: String): SortOrder {
            return entries.firstOrNull { it.name == name } ?: NAME_ASC
        }
    }
}
