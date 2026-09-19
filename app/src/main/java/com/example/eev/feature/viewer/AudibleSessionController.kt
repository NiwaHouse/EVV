package com.example.eev.feature.viewer

import com.example.eev.core.model.AudibleMode
import com.example.eev.core.model.Language
import com.example.eev.feature.translation.TranslationEngine
import com.example.eev.feature.translation.TtsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * オーディブル再生（OCR → 翻訳 → TTS → 自動送り）の状態機械を担うコントローラ。
 *
 * [責任]: 再生セッションの開始・停止・自動送り・先読み・ライフサイクル連動。
 * [影響する状態]: [ViewerUiState] の audible 系フィールド（[stateUpdater] 経由でのみ更新）。
 * [潜在的エラー]: スクリプト生成結果が空（文字未検出）の場合は無音時待機を挟んで継続する。
 *
 * [ViewerViewModel] から切り出した副モジュール。画面状態の読み書きは [stateProvider] /
 * [stateUpdater] を通じてのみ行い、状態の単一の真実源を [ViewerViewModel] 側に保つ。
 */
class AudibleSessionController(
    private val scriptProvider: AudibleScriptProvider,
    private val ttsEngine: TtsEngine,
    private val translationEngine: TranslationEngine,
    private val stateProvider: () -> ViewerUiState,
    private val stateUpdater: ((ViewerUiState) -> ViewerUiState) -> Unit,
    private val scope: CoroutineScope,
    private val navigateToNextDirectory: suspend () -> Boolean
) {

    /** 実行中の再生セッションジョブ。停止・再開始時に cancel する。 */
    private var audibleJob: Job? = null

    /** 先読みジョブ。二重起動を防ぐため単一ジョブを保持する。 */
    private var prefetchJob: Job? = null

    /**
     * 再生セッション識別子。停止・移動のたびにインクリメントし、
     * 進行中のコールバック（TTS 完了・OCR 結果）を無効化する。
     */
    private var sessionId = 0

    /** 背景移行の直前に再生中だったかどうか（前面復帰時の自動再開判定に使用）。 */
    private var wasPlayingBeforeBackground = false

    /** 現在オーディブル再生中かどうか。 */
    val isPlaying: Boolean
        get() = stateProvider().isAudiblePlaying

    /**
     * 再生開始要求。翻訳モードの場合はモデル取得状況を確認してから開始する。
     *
     * [責任]: 自動開始条件（画像一覧の有無・翻訳モデル取得状況）の判定。
     * [影響する状態]: `isAudibleModelDownloadRequired`, `isAudiblePlaying`。
     * [潜在的エラー]: ビューワ未オープン（画像一覧が空）の場合は開始せず、
     * [ViewerViewModel.loadImagesFromDirectory] 完了時に改めて開始される。
     */
    suspend fun requestStart() {
        val state = stateProvider()
        if (state.audibleMode == AudibleMode.OFF) return
        if (state.imageFiles.isEmpty()) return
        if (state.audibleMode == AudibleMode.READ_TRANSLATED) {
            val ready = translationEngine.areModelsReady(state.sourceLanguage, state.targetLanguage)
            if (!ready) {
                update { it.copy(isAudibleModelDownloadRequired = true) }
                return
            }
        }
        start()
    }

    /**
     * 再生セッションを開始する。実行中のセッションは破棄し、現在表示中の位置から読み直す。
     *
     * [責任]: セッション識別子の発行と再生ループの起動。
     * [影響する状態]: `isAudiblePlaying`, `audibleItemIndex`, `audibleHighlightRect`。
     * [潜在的エラー]: 画像一覧が空の場合はループが即座に終了する。
     */
    fun start() {
        val session = ++sessionId
        ttsEngine.stop()
        audibleJob?.cancel()
        prefetchJob?.cancel()
        update {
            it.copy(
                isAudiblePlaying = true,
                audibleItemIndex = -1,
                audibleHighlightRect = null,
                isAudibleSilentWaiting = false
            )
        }
        audibleJob = scope.launch { runSession(session) }
    }

    /**
     * 現在位置から再生を停止し、状態を初期化する。
     *
     * [責任]: TTS の即時停止、ジョブ破棄、セッション無効化、先読みキャッシュ破棄。
     * [影響する状態]: 各 audible 系フィールド。
     * [潜在的エラー]: なし。
     */
    fun stop() {
        sessionId++
        audibleJob?.cancel()
        audibleJob = null
        prefetchJob?.cancel()
        prefetchJob = null
        scriptProvider.clearCache()
        ttsEngine.stop()
        update {
            it.copy(
                isAudiblePlaying = false,
                audibleItemIndex = -1,
                audibleHighlightRect = null,
                isAudibleSilentWaiting = false,
                isAudibleModelDownloadRequired = false
            )
        }
    }

    /**
     * アプリが背景へ移行した際の処理。音声が背後で鳴り続けないよう再生を停止する。
     *
     * [責任]: ON_STOP 時に再生中だったかを記録し、オーディブル再生を停止する。
     * [影響する状態]: 各 audible 系フィールド（モードが OFF 以外のとき）。
     * [潜在的エラー]: なし。
     */
    fun onMovedToBackground() {
        wasPlayingBeforeBackground = stateProvider().isAudiblePlaying
        if (stateProvider().audibleMode != AudibleMode.OFF) stop()
    }

    /**
     * アプリが前面へ復帰した際の処理。背景移行時に再生中だった場合のみ自動再開する。
     *
     * [責任]: ON_START 時に「前面時のみ再生」仕様を満たすための再開制御。
     * [影響する状態]: 各 audible 系フィールド（再開時）。
     * [潜在的エラー]: なし。
     */
    fun onMovedToForeground() {
        val shouldResume = wasPlayingBeforeBackground
        wasPlayingBeforeBackground = false
        if (shouldResume && stateProvider().audibleMode != AudibleMode.OFF) start()
    }

    /**
     * 1 つの再生セッション本体。スクリプト生成 → 順次読み上げ → 自動送りを繰り返す。
     *
     * [責任]: オーディブル再生の中核ループ。
     * [影響する状態]: 各 audible 系フィールドおよび表示中画像。
     * [潜在的エラー]: スクリプト生成失敗（空リスト）時は無音時待機を挟んで継続する。
     */
    private suspend fun runSession(session: Int) {
        while (currentCoroutineContext().isActive && isCurrent(session)) {
            val state = stateProvider()
            if (state.audibleMode == AudibleMode.OFF) return
            val file = state.imageFiles.getOrNull(state.currentIndex) ?: return

            val units = scriptProvider.provide(
                file = file,
                cropPosition = state.cropPosition,
                mode = state.audibleMode,
                sourceLanguage = state.sourceLanguage,
                targetLanguage = state.targetLanguage
            )
            if (!isCurrent(session)) return

            // 次の読み上げ対象（漫画モードでは同一画像の残り半分）を先読みしておく。
            requestPrefetch(session, state)

            if (units.isEmpty()) {
                update { it.copy(isAudibleSilentWaiting = true) }
                val completed = waitWhileActive(session, (state.audibleSilentWaitSeconds * 1000f).toLong())
                update { it.copy(isAudibleSilentWaiting = false) }
                if (!completed) return
            } else {
                for (index in units.indices) {
                    if (!isCurrent(session)) return
                    val unit = units[index]
                    update { it.copy(audibleItemIndex = index, audibleHighlightRect = unit.boundingBox) }
                    val spoken = speakAndAwait(
                        text = unit.spokenText,
                        language = speechLanguage(state),
                        speechRate = state.audibleSpeechRate
                    )
                    if (!spoken || !isCurrent(session)) return
                }
                update { it.copy(audibleItemIndex = -1, audibleHighlightRect = null) }
            }

            if (!advanceToNext()) {
                update { it.copy(isAudiblePlaying = false, audibleItemIndex = -1, audibleHighlightRect = null) }
                return
            }
        }
    }

    /**
     * 読み上げに用いる言語を決定する。
     *
     * [責任]: READ_RAW は原文言語、READ_TRANSLATED は訳文言語を返す。
     * [影響する状態]: なし。
     * [潜在的エラー]: なし。
     */
    private fun speechLanguage(state: ViewerUiState): Language =
        if (state.audibleMode == AudibleMode.READ_TRANSLATED) state.targetLanguage else state.sourceLanguage

    /**
     * TTS に 1 文を発話させ、完了まで待機する。
     *
     * [責任]: 発話完了通知をコルーチンの再開へ橋渡しする。
     * [影響する状態]: TTS エンジンの再生キュー。
     * [潜在的エラー]: 停止によりコルーチンがキャンセルされた場合は false を返す。
     *
     * 「読み上げ完了と同時に次の画像へ進む」要件を満たすため、固定時間待機ではなく
     * UtteranceProgressListener.onDone を完了条件とする。
     */
    private suspend fun speakAndAwait(text: String, language: Language, speechRate: Float): Boolean =
        suspendCancellableCoroutine { continuation ->
            var resumed = false
            val finish: (Boolean) -> Unit = { value ->
                if (!resumed) {
                    resumed = true
                    if (continuation.isActive) continuation.resume(value)
                }
            }
            ttsEngine.speak(text, language, onDone = { finish(true) }, speechRate = speechRate)
            continuation.invokeOnCancellation { finish(false) }
        }

    /**
     * 指定時間だけ待機する。待機中も停止・移動を即座に反映するため細分化して確認する。
     *
     * [責任]: 無音時待機の実装。
     * [影響する状態]: なし。
     * [潜在的エラー]: なし（セッションが変わった場合は false を返す）。
     */
    private suspend fun waitWhileActive(session: Int, durationMs: Long): Boolean {
        var remaining = durationMs
        while (remaining > 0) {
            if (!isCurrent(session)) return false
            val chunk = minOf(WAIT_CHECK_INTERVAL_MS, remaining)
            delay(chunk)
            remaining -= chunk
        }
        return isCurrent(session)
    }

    /**
     * 読み上げ完了後に次の表示位置へ進める。
     *
     * [責任]: 通常は次画像、漫画モードでは右→左→次ページの順に進める。
     * [影響する状態]: `currentIndex`, `cropPosition`。
     * [潜在的エラー]: 末尾かつ後続フォルダが無い場合は false を返し再生を終了する。
     */
    private suspend fun advanceToNext(): Boolean {
        if (stateProvider().audibleMode == AudibleMode.OFF) return false
        val state = stateProvider()
        return if (state.isMangaMode && state.cropPosition == CropPosition.RIGHT_HALF) {
            update { it.copy(cropPosition = CropPosition.LEFT_HALF) }
            true
        } else if (state.currentIndex < state.imageFiles.size - 1) {
            update {
                it.copy(
                    currentIndex = state.currentIndex + 1,
                    cropPosition = if (state.isMangaMode) CropPosition.RIGHT_HALF else CropPosition.FULL
                )
            }
            true
        } else {
            navigateToNextDirectory()
        }
    }

    /**
     * 次に読み上げる対象のスクリプトをバックグラウンドで先行生成する。
     *
     * [責任]: 先読みキャッシュの充填。
     * [影響する状態]: [AudibleScriptProvider] のキャッシュ。
     * [潜在的エラー]: 次対象が存在しない場合は何もしない。
     */
    private fun requestPrefetch(session: Int, state: ViewerUiState) {
        if (prefetchJob?.isActive == true) return
        val target: Pair<File, CropPosition> =
            if (state.isMangaMode && state.cropPosition == CropPosition.RIGHT_HALF) {
                state.imageFiles.getOrNull(state.currentIndex)?.let { it to CropPosition.LEFT_HALF }
            } else {
                state.imageFiles.getOrNull(state.currentIndex + 1)?.let {
                    it to (if (state.isMangaMode) CropPosition.RIGHT_HALF else CropPosition.FULL)
                }
            } ?: return

        prefetchJob = scope.launch(Dispatchers.IO) {
            if (!isCurrent(session)) return@launch
            scriptProvider.prefetch(
                file = target.first,
                cropPosition = target.second,
                mode = state.audibleMode,
                sourceLanguage = state.sourceLanguage,
                targetLanguage = state.targetLanguage
            )
        }
    }

    /** 起動中のセッションが現在有効なセッションかどうか。 */
    private fun isCurrent(session: Int): Boolean = session == sessionId

    /** 画面状態を更新する（単一の真実源は [stateUpdater] 側）。 */
    private fun update(block: (ViewerUiState) -> ViewerUiState) = stateUpdater(block)

    private companion object {
        /** 無音時待機中のセッション確認間隔（ミリ秒）。 */
        private const val WAIT_CHECK_INTERVAL_MS = 100L
    }
}
