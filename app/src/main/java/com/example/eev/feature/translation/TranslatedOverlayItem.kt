package com.example.eev.feature.translation

import android.graphics.RectF

/**
 * 翻訳結果と画像上の表示領域（バウンディングボックス）を保持するモデルデータクラス。
 */
data class TranslatedOverlayItem(
    val translatedText: String,
    val boundingBox: RectF?
)
