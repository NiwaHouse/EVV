package com.example.eev.feature.viewer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import com.example.eev.core.model.AudibleMode
import com.example.eev.core.model.Language
import com.example.eev.feature.translation.OcrEngine
import com.example.eev.feature.translation.OcrGroupMerger
import com.example.eev.feature.translation.TranslationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * オーディブル再生用の読み上げスクリプト（1 枚分の [AudibleUnit] 列）を生成するプロバイダ。
 *
 * [責任]: 表示中クロップ画像の OCR →（必要時のみ）翻訳 → 読み上げ順への並べ替え、および先読みキャッシュの適用。
 * [影響する状態]: なし（永続化状態を変更しない。結果は [AudiblePrefetchCache] にのみ保持）。
 * [潜在的エラー]: 画像デコード失敗時は空リストを返し、呼び出し側で無音時待機へ遷移させる。
 *
 * オーディブル再生中は翻訳オーバーレイを描画しないため、本プロバイダが唯一の OCR 実行経路となる。
 */
class AudibleScriptProvider(
    private val ocrEngine: OcrEngine,
    private val translationEngine: TranslationEngine,
    private val prefetchCache: AudiblePrefetchCache = AudiblePrefetchCache()
) {

    /**
     * 指定画像・クロップ位置の読み上げスクリプトを取得する（キャッシュ優先）。
     *
     * [責任]: キャッシュヒット時は即返し、未取得時のみ OCR／翻訳を実行する。
     * [影響する状態]: [AudiblePrefetchCache]。
     * [潜在的エラー]: なし（失敗時は空リスト）。
     */
    suspend fun provide(
        file: File,
        cropPosition: CropPosition,
        mode: AudibleMode,
        sourceLanguage: Language,
        targetLanguage: Language
    ): List<AudibleUnit> {
        val key = prefetchCache.buildKey(file.absolutePath, cropPosition, mode, sourceLanguage, targetLanguage)
        prefetchCache.get(key)?.let { return it }
        val units = build(file, cropPosition, mode, sourceLanguage, targetLanguage)
        prefetchCache.put(key, units)
        return units
    }

    /**
     * 次に読み上げる対象を先行生成してキャッシュへ格納する。
     *
     * [責任]: ページ送り時の待ち時間短縮。
     * [影響する状態]: [AudiblePrefetchCache]。
     * [潜在的エラー]: なし（失敗時は空リストがキャッシュされる）。
     */
    suspend fun prefetch(
        file: File,
        cropPosition: CropPosition,
        mode: AudibleMode,
        sourceLanguage: Language,
        targetLanguage: Language
    ) {
        val key = prefetchCache.buildKey(file.absolutePath, cropPosition, mode, sourceLanguage, targetLanguage)
        if (prefetchCache.get(key) != null) return
        val units = build(file, cropPosition, mode, sourceLanguage, targetLanguage)
        prefetchCache.put(key, units)
    }

    /**
     * キャッシュを破棄する。再生停止・フォルダ移動時に呼び出す。
     */
    fun clearCache() = prefetchCache.clear()

    /**
     * OCR と（モードに応じた）翻訳を実行し、読み上げ順の [AudibleUnit] 列を生成する。
     *
     * [責任]: 画像デコード、OCR、結合単位マージ、翻訳、読み上げ順ソート。
     * [影響する状態]: [TranslationEngine] 内部の翻訳モデル。
     * [潜在的エラー]: デコード失敗時は空リスト。翻訳失敗時は原文へフォールバックする（エンジン側の仕様）。
     */
    private suspend fun build(
        file: File,
        cropPosition: CropPosition,
        mode: AudibleMode,
        sourceLanguage: Language,
        targetLanguage: Language
    ): List<AudibleUnit> = withContext(Dispatchers.IO) {
        val bitmap = loadCroppedBitmap(file, cropPosition) ?: return@withContext emptyList()

        val rawOcrResults = ocrEngine.recognizeText(bitmap)
        val mergedOcrResults = OcrGroupMerger.groupAndMergeLines(rawOcrResults)

        val units = if (mode == AudibleMode.READ_TRANSLATED) {
            mergedOcrResults.mapNotNull { line ->
                val translated = translationEngine.translate(
                    text = line.text,
                    sourceLanguage = sourceLanguage,
                    targetLanguage = targetLanguage
                )
                if (translated.isBlank()) null else AudibleUnit(translated, line.boundingBox)
            }
        } else {
            mergedOcrResults
                .filter { it.text.isNotBlank() }
                .map { AudibleUnit(it.text, it.boundingBox) }
        }

        sortForReading(units, bitmap.width.toFloat(), bitmap.height.toFloat())
    }

    /**
     * 対象画像をデコードし、漫画モード時は表示中の半分のみを切り出す。
     *
     * [責任]: OCR 対象ビットマップの生成。
     * [影響する状態]: なし。
     * [潜在的エラー]: デコード失敗時は null（呼び出し側で空スクリプト扱い）。
     */
    private fun loadCroppedBitmap(file: File, cropPosition: CropPosition): Bitmap? {
        val original = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        return MangaCropHelper.getCroppedBitmap(original, cropPosition)
    }

    /**
     * 結合単位を「上→下、同一行なら右→左」で並べ替える（右綴じ漫画に配慮）。
     *
     * [責任]: 読み上げ順序の決定。領域情報を持たない単位は末尾扱いとならないよう
     * 空 RectF として比較する。
     * [影響する状態]: なし。
     * [潜在的エラー]: なし。
     *
     * 並び順の規則は [ViewerViewModel] の翻訳オーバーレイ用ソートと同一に保つこと。
     */
    private fun sortForReading(
        units: List<AudibleUnit>,
        imageWidth: Float,
        imageHeight: Float
    ): List<AudibleUnit> {
        if (units.size <= 1) return units
        val midX = imageWidth / 2f
        val midY = imageHeight / 2f

        return units.sortedWith { a, b ->
            val rectA = a.boundingBox ?: RectF()
            val rectB = b.boundingBox ?: RectF()
            val zoneA = zonePriority(rectA, midX, midY)
            val zoneB = zonePriority(rectB, midX, midY)
            if (zoneA != zoneB) {
                zoneA.compareTo(zoneB)
            } else if (kotlin.math.abs(rectA.top - rectB.top) > SAME_LINE_THRESHOLD_PX) {
                rectA.top.compareTo(rectB.top)
            } else {
                rectB.right.compareTo(rectA.right)
            }
        }
    }

    /**
     * 領域が画像のどの象限に属するかを返す（右上=1, 左上=2, 右下=3, 左下=4）。
     */
    private fun zonePriority(rect: RectF, midX: Float, midY: Float): Int {
        val cx = rect.centerX()
        val cy = rect.centerY()
        return when {
            cy < midY && cx >= midX -> 1
            cy < midY && cx < midX -> 2
            cy >= midY && cx >= midX -> 3
            else -> 4
        }
    }

    companion object {
        /** 同一行とみなす上下位置の許容差（ピクセル）。 */
        private const val SAME_LINE_THRESHOLD_PX = 40f
    }
}
