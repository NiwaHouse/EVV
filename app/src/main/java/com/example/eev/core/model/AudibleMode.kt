package com.example.eev.core.model

/**
 * 画像ビューワのオーディブル再生モードを表す列挙型。
 *
 * [責任]: 「OCR→（翻訳）→読み上げ→自動送り」の動作モードを 3 状態で表現する。
 * [影響する状態]: 永続化キー `audible_mode` および ViewerUiState.audibleMode。
 * [潜在的エラー]: 未知の文字列は [fromName] で [OFF] にフォールバックするため例外は発生しない。
 *
 * OFF を唯一の停止状態とし、再生系 2 状態は排他である（片方を選ぶと他方は自動的に解除される）。
 */
enum class AudibleMode(val displayName: String) {
    /** オーディブル再生を行わない（既定）。 */
    OFF("オフ"),

    /** OCR で取得した原文をそのまま読み上げる。 */
    READ_RAW("オーディブル再生"),

    /** OCR で取得した原文を翻訳してから読み上げる。 */
    READ_TRANSLATED("翻訳してからオーディブル再生");

    /** 再生指示が出ている状態かどうか。 */
    val isPlaying: Boolean get() = this != OFF

    companion object {
        /**
         * 永続化された文字列から [AudibleMode] を復元する。
         *
         * [責任]: DataStore の破損・未知値に対する安全な復元。
         * [影響する状態]: ViewerUiState.audibleMode。
         * [潜在的エラー]: 該当なし（未知値は [OFF]）。
         */
        fun fromName(name: String): AudibleMode =
            entries.firstOrNull { it.name == name } ?: OFF
    }
}
