package com.example.eev.core.model

/**
 * ファイルエクスプローラのサムネイル表示サイズを表す列挙型。
 *
 * [責任]: サムネイルの3段階（大・中・小）の選択肢と、各サイズに対応する
 *         リスト表示・グリッド表示それぞれのピクセル寸法を定義する。
 * [影響する状態]: エクスプローラ画面のサムネイル描画サイズおよびグリッド列数。
 * [潜在的エラー]: なし。
 */
enum class ThumbnailSize(
    val displayName: String,
    /** リスト表示時のサムネイル一辺サイズ（dp）。 */
    val listSizeDp: Int,
    /** グリッド表示時のサムネイル一辺サイズ（dp）。 */
    val gridSizeDp: Int,
    /** グリッド表示時の1行あたり列数。サイズが大きいほど列数を減らす。 */
    val gridColumns: Int
) {
    SMALL("小", 40, 56, 4),
    MEDIUM("中", 56, 88, 3),
    LARGE("大", 72, 128, 2);

    companion object {
        /**
         * 保存された名前文字列から [ThumbnailSize] を復元する。
         * 該当が無い場合は既定値 [MEDIUM] を返す。
         */
        fun fromName(name: String): ThumbnailSize {
            return entries.firstOrNull { it.name == name } ?: MEDIUM
        }
    }
}
