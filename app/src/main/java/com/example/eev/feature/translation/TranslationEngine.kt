package com.example.eev.feature.translation

import com.example.eev.core.model.Language
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Google ML Kit On-Device Translation によるオフライン翻訳エンジン。
 *
 * [責任]: 指定言語に基づくテキスト翻訳、敬語排除（カジュアル化）フィルター処理。
 * [影響する状態]: 翻訳オーバーレイ画面の出力文字。
 */
class TranslationEngine {

    private var currentTranslator: Translator? = null
    private var currentSource: Language? = null
    private var currentTarget: Language? = null

    private fun mapLanguage(language: Language): String {
        return when (language) {
            Language.JAPANESE -> TranslateLanguage.JAPANESE
            Language.ENGLISH -> TranslateLanguage.ENGLISH
            Language.CHINESE -> TranslateLanguage.CHINESE
            Language.KOREAN -> TranslateLanguage.KOREAN
            Language.SPANISH -> TranslateLanguage.SPANISH
            Language.FRENCH -> TranslateLanguage.FRENCH
            Language.GERMAN -> TranslateLanguage.GERMAN
        }
    }

    suspend fun translate(
        text: String,
        sourceLanguage: Language,
        targetLanguage: Language
    ): String = suspendCancellableCoroutine { continuation ->
        if (text.isBlank()) {
            continuation.resume("")
            return@suspendCancellableCoroutine
        }

        val sourceCode = mapLanguage(sourceLanguage)
        val targetCode = mapLanguage(targetLanguage)

        if (currentTranslator == null || currentSource != sourceLanguage || currentTarget != targetLanguage) {
            currentTranslator?.close()
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(sourceCode)
                .setTargetLanguage(targetCode)
                .build()
            currentTranslator = Translation.getClient(options)
            currentSource = sourceLanguage
            currentTarget = targetLanguage
        }

        val translator = currentTranslator!!
        val conditions = DownloadConditions.Builder().build()

        translator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                translator.translate(text)
                    .addOnSuccessListener { translatedText ->
                        val finalText = if (targetLanguage == Language.JAPANESE) {
                            makeCasualJapanese(translatedText)
                        } else {
                            translatedText
                        }
                        continuation.resume(finalText)
                    }
                    .addOnFailureListener {
                        continuation.resume(text)
                    }
            }
            .addOnFailureListener {
                continuation.resume(text)
            }
    }

    /**
     * 日本語翻訳文の「〜です」「〜ます」等の敬語を排除し、短くカジュアルな漫画セリフ調に変換する。
     */
    private fun makeCasualJapanese(text: String): String {
        if (text.isBlank()) return text
        var result = text
        result = result.replace(Regex("でしょうか[？?]"), "かな？")
        result = result.replace(Regex("ですか[？?]"), "なのか？")
        result = result.replace(Regex("ますか[？?]"), "るのか？")
        result = result.replace(Regex("でした[。.]?"), "だった")
        result = result.replace(Regex("ございます[。.]?"), "ある")
        result = result.replace(Regex("いたします[。.]?"), "する")
        result = result.replace(Regex("してください[。.]?"), "してくれ")
        result = result.replace(Regex("しなさい[。.]?"), "しろ")
        result = result.replace(Regex("です[。.]?"), "だ")
        result = result.replace(Regex("ます[。.]?"), "る")
        result = result.replace(Regex("れません[。.]?"), "れない")
        result = result.replace(Regex("みません[。.]?"), "まない")
        result = result.replace(Regex("きません[。.]?"), "かない")
        result = result.replace(Regex("りません[。.]?"), "らない")
        result = result.replace(Regex("いません[。.]?"), "いない")
        result = result.replace(Regex("ありません[。.]?"), "ない")
        result = result.replace(Regex("ません[。.]?"), "ない")
        return result
    }

    /**
     * 指定言語ペアの翻訳モデルが端末にダウンロード済みかどうかを判定する。
     *
     * [責任]: オーディブル再生（翻訳モード）開始前に、通信なしで再生可能かを判定する。
     * [影響する状態]: なし（翻訳エンジンの内部状態は変更しない）。
     * [潜在的エラー]: 判定失敗時は false（未取得）として扱い、呼び出し側でダウンロード導線へ進める。
     *
     * オンデバイス翻訳モデルは初回のみダウンロードが必要なため、
     * 「モデル未取得」を検出してユーザー同意のうえ取得する導線に用いる。
     */
    suspend fun areModelsReady(sourceLanguage: Language, targetLanguage: Language): Boolean =
        suspendCancellableCoroutine { continuation ->
            val manager = RemoteModelManager.getInstance()
            val sourceModel = TranslateRemoteModel.Builder(mapLanguage(sourceLanguage)).build()
            val targetModel = TranslateRemoteModel.Builder(mapLanguage(targetLanguage)).build()
            manager.isModelDownloaded(sourceModel)
                .addOnSuccessListener { sourceReady ->
                    if (!continuation.isActive) return@addOnSuccessListener
                    if (!sourceReady) {
                        continuation.resume(false)
                        return@addOnSuccessListener
                    }
                    manager.isModelDownloaded(targetModel)
                        .addOnSuccessListener { targetReady ->
                            if (continuation.isActive) continuation.resume(targetReady)
                        }
                        .addOnFailureListener {
                            if (continuation.isActive) continuation.resume(false)
                        }
                }
                .addOnFailureListener {
                    if (continuation.isActive) continuation.resume(false)
                }
        }

    /**
     * 指定言語ペアの翻訳モデルのダウンロードを実行する（取得済みの場合は即座に成功する）。
     *
     * [責任]: ユーザー同意後のモデル取得。
     * [影響する状態]: 端末内の ML Kit 翻訳モデル。
     * [潜在的エラー]: オフライン等で取得に失敗した場合は false を返す（例外は投げない）。
     */
    suspend fun downloadModels(sourceLanguage: Language, targetLanguage: Language): Boolean =
        suspendCancellableCoroutine { continuation ->
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(mapLanguage(sourceLanguage))
                .setTargetLanguage(mapLanguage(targetLanguage))
                .build()
            val translator = Translation.getClient(options)
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
                .addOnSuccessListener {
                    translator.close()
                    if (continuation.isActive) continuation.resume(true)
                }
                .addOnFailureListener {
                    translator.close()
                    if (continuation.isActive) continuation.resume(false)
                }
        }

    fun close() {
        currentTranslator?.close()
        currentTranslator = null
    }
}
