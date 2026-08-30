package com.example.eev.feature.translation

import android.graphics.Bitmap
import android.graphics.RectF
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * OCRで検出された単語・行の位置と認識テキストを保持するモデル。
 */
data class OcrTextLine(
    val text: String,
    val boundingBox: RectF
)

/**
 * Google ML Kit Text Recognition を使用して画像内文字認識を行うエンジン。
 *
 * [責任]: ビットマップ画像からのテキストおよび画面座標の抽出。
 * [影響する状態]: 翻訳オーバーレイ描画用の文字枠データ。
 * [潜在的エラー]: ML Kit非対応画像形式・メモリエラー。
 */
class OcrEngine {

    private val recognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())

    suspend fun recognizeText(bitmap: Bitmap): List<OcrTextLine> = suspendCancellableCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val lines = mutableListOf<OcrTextLine>()
                for (block in visionText.textBlocks) {
                    for (line in block.lines) {
                        val box = line.boundingBox
                        if (box != null) {
                            lines.add(
                                OcrTextLine(
                                    text = line.text,
                                    boundingBox = RectF(box)
                                )
                            )
                        }
                    }
                }
                continuation.resume(lines)
            }
            .addOnFailureListener {
                continuation.resume(emptyList())
            }
    }
}
