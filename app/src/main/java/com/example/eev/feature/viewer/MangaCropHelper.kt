package com.example.eev.feature.viewer

import android.graphics.Bitmap

/**
 * 漫画モード表示位置を表す列挙型。
 */
enum class CropPosition {
    RIGHT_HALF, // 右半分表示
    LEFT_HALF,  // 左半分表示
    FULL        // 通常全体表示
}

/**
 * 漫画モード（見開き右→左閲覧）時の画像クロップおよび描画領域の計算を行うヘルパー。
 *
 * [責任]: 画像の右半分・左半分の部分切り出し処理。
 * [影響する状態]: 漫画モード時の表示画像ビットマップ。
 * [潜在的エラー]: ビットマップ領域不足による IllegalArgumentException。
 */
object MangaCropHelper {

    /**
     * 指定された CropPosition に基づいて Bitmap を切り出す。
     */
    fun getCroppedBitmap(source: Bitmap, position: CropPosition): Bitmap {
        if (position == CropPosition.FULL) return source

        val width = source.width
        val height = source.height
        val halfWidth = width / 2

        if (halfWidth <= 0 || height <= 0) return source

        return when (position) {
            CropPosition.RIGHT_HALF -> {
                Bitmap.createBitmap(source, halfWidth, 0, halfWidth, height)
            }
            CropPosition.LEFT_HALF -> {
                Bitmap.createBitmap(source, 0, 0, halfWidth, height)
            }
            CropPosition.FULL -> source
        }
    }
}
