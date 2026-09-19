package com.example.eev.feature.viewer

import android.graphics.RectF
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * オーディブル再生中に「いま読み上げている検出領域」を枠線のみで強調表示するコンポーザブル。
 *
 * [責任]: 画像座標系の [rect] を、ContentScale.Fit で描画された実表示領域へ座標変換し、
 * 半透明塗り＋枠線のみを重畳描画する（翻訳テキストは描画しない）。
 * [影響する状態]: 視覚描画のみ。
 * [潜在的エラー]: [rect] が null または画像サイズが不正な場合は何も描画しない。
 *
 * 座標変換式は [com.example.eev.feature.translation.TranslationOverlay] と同一に保つこと。
 */
@Composable
fun AudibleHighlightOverlay(
    rect: RectF?,
    imageWidth: Float,
    imageHeight: Float,
    borderColor: Color = Color(0xFF4CAF50),
    modifier: Modifier = Modifier
) {
    if (rect == null || imageWidth <= 0f || imageHeight <= 0f) return

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val containerWidthPx = with(density) { maxWidth.toPx() }
        val containerHeightPx = with(density) { maxHeight.toPx() }
        if (containerWidthPx <= 0f || containerHeightPx <= 0f) return@BoxWithConstraints

        val imageAspect = imageWidth / imageHeight
        val containerAspect = containerWidthPx / containerHeightPx

        // ContentScale.Fit 相当のレターボックス余白を算出する。
        val scaledWidth: Float
        val scaledHeight: Float
        val offsetX: Float
        val offsetY: Float
        if (containerAspect > imageAspect) {
            scaledHeight = containerHeightPx
            scaledWidth = containerHeightPx * imageAspect
            offsetX = (containerWidthPx - scaledWidth) / 2f
            offsetY = 0f
        } else {
            scaledWidth = containerWidthPx
            scaledHeight = containerWidthPx / imageAspect
            offsetX = 0f
            offsetY = (containerHeightPx - scaledHeight) / 2f
        }

        val scaleX = scaledWidth / imageWidth
        val scaleY = scaledHeight / imageHeight

        val leftPx = offsetX + (rect.left * scaleX)
        val topPx = offsetY + (rect.top * scaleY)
        val widthPx = ((rect.right - rect.left) * scaleX).coerceAtLeast(8f)
        val heightPx = ((rect.bottom - rect.top) * scaleY).coerceAtLeast(8f)

        Box(
            modifier = Modifier
                .offset(
                    x = with(density) { leftPx.toDp() },
                    y = with(density) { topPx.toDp() }
                )
                .size(
                    width = with(density) { widthPx.toDp() },
                    height = with(density) { heightPx.toDp() }
                )
                .background(borderColor.copy(alpha = 0.18f), RoundedCornerShape(4.dp))
                .border(width = 2.dp, color = borderColor, shape = RoundedCornerShape(4.dp))
        )
    }
}
