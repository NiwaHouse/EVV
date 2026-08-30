package com.example.eev.feature.translation

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.eev.core.model.Language
import java.util.Locale

/**
 * Android の TextToSpeech (TTS) エンジンをラップしたテキスト音声読み上げユーティリティ。
 *
 * [責任]: テキストの連続順次発話、言語切り替え、再生終了通知、初期化失敗時のエンジンパッケージ自動フォールバック。
 * [影響する状態]: 音声出力。
 */
class TtsEngine(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isInitialized = false
    private var onSpeechDoneListener: (() -> Unit)? = null
    private var pendingSpeakTask: (() -> Unit)? = null

    init {
        Log.d("EEV_TTS", "TtsEngine initialized. Waiting for onInit callback...")
        setupUtteranceListener()
    }

    private fun setupUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d("EEV_TTS", "onStart utteranceId: $utteranceId")
            }
            override fun onDone(utteranceId: String?) {
                Log.d("EEV_TTS", "onDone utteranceId: $utteranceId")
                onSpeechDoneListener?.invoke()
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Log.e("EEV_TTS", "onError utteranceId: $utteranceId")
                onSpeechDoneListener?.invoke()
            }
        })
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            Log.d("EEV_TTS", "TextToSpeech onInit SUCCESS")
            val res = tts?.setLanguage(Locale.JAPANESE)
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w("EEV_TTS", "Japanese not supported directly, falling back to default locale")
                tts?.language = Locale.getDefault()
            }
            pendingSpeakTask?.let { task ->
                Log.d("EEV_TTS", "Executing pending speak task after onInit")
                pendingSpeakTask = null
                task.invoke()
            }
        } else {
            Log.e("EEV_TTS", "TextToSpeech default onInit FAILED with status: $status. Trying Google TTS package fallback...")
            tryInitWithPackage("com.google.android.tts")
        }
    }

    private fun tryInitWithPackage(packageName: String) {
        try {
            tts?.shutdown()
            tts = TextToSpeech(context.applicationContext, { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isInitialized = true
                    Log.d("EEV_TTS", "TextToSpeech with package '$packageName' SUCCESS")
                    setupUtteranceListener()
                    tts?.language = Locale.JAPANESE
                    pendingSpeakTask?.let { task ->
                        pendingSpeakTask = null
                        task.invoke()
                    }
                } else {
                    Log.e("EEV_TTS", "TextToSpeech with package '$packageName' FAILED with status: $status")
                }
            }, packageName)
        } catch (e: Exception) {
            Log.e("EEV_TTS", "Exception initializing TTS with package $packageName", e)
        }
    }

    fun speak(text: String, language: Language, onDone: () -> Unit) {
        Log.d("EEV_TTS", "speak requested: text='$text', lang=$language, initialized=$isInitialized")
        if (text.isBlank()) {
            Log.d("EEV_TTS", "Text is blank. Skipping speech.")
            onDone()
            return
        }

        if (!isInitialized) {
            Log.w("EEV_TTS", "TTS not initialized yet. Saving task to pendingSpeakTask.")
            pendingSpeakTask = { speak(text, language, onDone) }
            return
        }

        onSpeechDoneListener = onDone
        val locale = when (language) {
            Language.JAPANESE -> Locale.JAPANESE
            Language.ENGLISH -> Locale.ENGLISH
            Language.CHINESE -> Locale.CHINESE
            Language.KOREAN -> Locale.KOREAN
            Language.SPANISH -> Locale("es", "ES")
            Language.FRENCH -> Locale.FRENCH
            Language.GERMAN -> Locale.GERMAN
        }

        val result = tts?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w("EEV_TTS", "Language $locale missing/not supported. Fallback to default.")
            tts?.language = Locale.getDefault()
        }

        val utteranceId = "EEV_UTTERANCE_${System.currentTimeMillis()}_${(0..999).random()}"
        val params = android.os.Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)

        val speakResult = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        Log.d("EEV_TTS", "tts.speak returned result code: $speakResult (SUCCESS=${TextToSpeech.SUCCESS})")
        if (speakResult != TextToSpeech.SUCCESS) {
            Log.e("EEV_TTS", "tts.speak failed with error code $speakResult. Notifying onDone callback.")
            onDone()
        }
    }

    fun stop() {
        Log.d("EEV_TTS", "stop requested")
        if (isInitialized) {
            tts?.stop()
        }
        pendingSpeakTask = null
    }

    fun shutdown() {
        Log.d("EEV_TTS", "shutdown requested")
        if (isInitialized) {
            tts?.stop()
            tts?.shutdown()
            tts = null
        }
        pendingSpeakTask = null
    }
}
