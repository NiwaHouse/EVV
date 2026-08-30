package com.example.eev.core.model

/**
 * ファイルエクスプローラの表示モードを表す列挙型。
 *
 * [責任]: リスト表示とグリッド表示の選択肢を定義する。
 * [影響する状態]: エクスプローラ画面のレイアウト（LazyColumn ↔ LazyVerticalGrid）。
 * [潜在的エラー]: なし。
 */
enum class ViewMode(val displayName: String) {
    LIST("リスト表示"),
    GRID("グリッド表示");

    companion object {
        fun fromName(name: String): ViewMode {
            return entries.firstOrNull { it.name == name } ?: LIST
        }
    }
}
