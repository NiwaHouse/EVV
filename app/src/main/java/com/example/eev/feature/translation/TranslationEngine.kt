package com.example.eev.feature.translation

import com.example.eev.core.model.Language
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
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

    fun close() {
        currentTranslator?.close()
        currentTranslator = null
    }
}
