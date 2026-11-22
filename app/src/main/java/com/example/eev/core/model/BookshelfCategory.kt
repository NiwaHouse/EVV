package com.example.eev.core.model

/**
 * 本棚のお気に入りフォルダを分類するカテゴリモデル。
 *
 * [責任]: カテゴリの一意識別子（ID）と表示名を保持する。
 * [影響する状態]: 本棚画面のカテゴリ絞り込みチップ、カテゴリ管理ダイアログ、フォルダ長押し時のカテゴリ選択状態。
 * [潜在的エラー]: なし。
 */
data class BookshelfCategory(
    val id: Long,
    val name: String
)
