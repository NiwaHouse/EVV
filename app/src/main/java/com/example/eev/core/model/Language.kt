package com.example.eev.core.model

/**
 * 画像内翻訳でサポートする言語を表す列挙型。
 *
 * [責任]: 翻訳元・翻訳先言語の選択肢およびML Kit用言語コードの定義。
 * [影響する状態]: OCR後の翻訳処理およびオーバーレイ表示言語。
 */
enum class Language(val code: String, val displayName: String) {
    JAPANESE("ja", "日本語"),
    ENGLISH("en", "英語"),
    CHINESE("zh", "中国語"),
    KOREAN("ko", "韓国語"),
    SPANISH("es", "スペイン語"),
    FRENCH("fr", "フランス語"),
    GERMAN("de", "ドイツ語");

    companion object {
        fun fromCode(code: String): Language {
            return entries.firstOrNull { it.code == code } ?: JAPANESE
        }
    }
}
