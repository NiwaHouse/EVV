package com.example.eev.feature.translation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * OCRテキスト認識および翻訳結果を、画像の正確な位置に調整して重畳描画するコンポーザブル。
 *
 * [責任]: 余白補正、枠面積と文字数に連動した段階的最適フォントサイズ計算、X軸固定・Y軸Auto可変。
 * [影響する状態]: 視覚描画のみ。
 */
@Composable
fun TranslationOverlay(
    items: List<TranslatedOverlayItem>,
    imageWidth: Float,
    imageHeight: Float,
    textColor: Color = Color.White,
    backgroundColor: Color = Color(0xCC000000),
    modifier: Modifier = Modifier
) {
    if (imageWidth <= 0f || imageHeight <= 0f) return

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val containerWidthPx = with(density) { maxWidth.toPx() }
        val containerHeightPx = with(density) { maxHeight.toPx() }

        if (containerWidthPx <= 0f || containerHeightPx <= 0f) return@BoxWithConstraints

        val imageAspect = imageWidth / imageHeight
        val containerAspect = containerWidthPx / containerHeightPx

        val (scaledW, scaledH, offsetX, offsetY) = if (containerAspect > imageAspect) {
            val h = containerHeightPx
            val w = h * imageAspect
            val ox = (containerWidthPx - w) / 2f
            Quad(w, h, ox, 0f)
        } else {
            val w = containerWidthPx
            val h = w / imageAspect
            val oy = (containerHeightPx - h) / 2f
            Quad(w, h, 0f, oy)
        }

        val scaleX = scaledW / imageWidth
        val scaleY = scaledH / imageHeight

        items.forEach { item ->
            val rect = item.boundingBox ?: return@forEach

            val leftPx = offsetX + (rect.left * scaleX)
            val topPx = offsetY + (rect.top * scaleY)
            val boxWidthPx = ((rect.right - rect.left) * scaleX).coerceAtLeast(30f)
            val minBoxHeightPx = ((rect.bottom - rect.top) * scaleY).coerceAtLeast(30f)

            val leftDp = with(density) { leftPx.toDp() }
            val topDp = with(density) { topPx.toDp() }
            val widthDp = with(density) { boxWidthPx.toDp() }
            val minHeightDp = with(density) { minBoxHeightPx.toDp() }

            // 枠面積 (幅 x 高さ) と文字数連動による段階的フォントサイズ計算
            val areaPx = boxWidthPx * minBoxHeightPx
            val charCount = item.translatedText.length.coerceAtLeast(1)
            val calculatedFontSizePx = kotlin.math.sqrt(areaPx / (charCount * 1.35f))
            val fontSize = with(density) { calculatedFontSizePx.coerceIn(11f, 26f).toSp() }

            // X軸（幅）は検出枠サイズで固定、Y軸（高さ）のみ文章量に応じてAuto（自動可変）
            Box(
                modifier = Modifier
                    .offset(x = leftDp, y = topDp)
                    .width(widthDp)
                    .heightIn(min = minHeightDp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(backgroundColor)
                    .padding(horizontal = 4.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.translatedText,
                    color = textColor,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    lineHeight = fontSize * 1.25f
                )
            }
        }
    }
}

private data class Quad(val w: Float, val h: Float, val ox: Float, val oy: Float)
