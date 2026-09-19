package com.example.eev.feature.viewer

import android.graphics.BitmapFactory
import android.graphics.RectF
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eev.core.datastore.EEVDataStore
import com.example.eev.core.model.AudibleMode
import com.example.eev.core.model.Language
import com.example.eev.core.model.SortOrder
import com.example.eev.core.storage.FileStorageManager
import com.example.eev.feature.translation.OcrEngine
import com.example.eev.feature.translation.TranslatedOverlayItem
import com.example.eev.feature.translation.TranslationEngine
import com.example.eev.feature.translation.TtsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * 画像ビューワ画面のUI State。
 */
data class ViewerUiState(
    val imageFiles: List<File> = emptyList(),
    val currentIndex: Int = 0,
    val cropPosition: CropPosition = CropPosition.FULL,
    val isMangaMode: Boolean = false,
    val isTranslationEnabled: Boolean = false,
    val showHiddenFiles: Boolean = false,
    val sourceLanguage: Language = Language.JAPANESE,
    val targetLanguage: Language = Language.ENGLISH,
    val textColorHex: String = "#FFFFFF",
    val backgroundColorHex: String = "#CC000000",
    val viewerSortOrder: SortOrder = SortOrder.NAME_ASC,
    val explorerSortOrder: SortOrder = SortOrder.NAME_ASC,
    /**
     * ビューワ起動時に指定された起点画像の絶対パス。
     * ソート順変更時の再ソートで、表示位置を維持するための基準として使用する。
     */
    val anchorFilePath: String? = null,
    val isBottomSheetVisible: Boolean = false,
    val translationOverlayItems: List<TranslatedOverlayItem> = emptyList(),
    val isTranslating: Boolean = false,
    /** オーディブル再生モード（永続化値のミラー）。 */
    val audibleMode: AudibleMode = AudibleMode.OFF,
    /** 再生セッションが動作中かどうか。 */
    val isAudiblePlaying: Boolean = false,
    /** 読み上げ中の結合単位インデックス。未再生時は -1。 */
    val audibleItemIndex: Int = -1,
    /** 読み上げ中の検出領域（ハイライト枠の描画用）。 */
    val audibleHighlightRect: RectF? = null,
    /** 文字が検出できず無音時待機中かどうか。 */
    val isAudibleSilentWaiting: Boolean = false,
    /** 翻訳モデル未取得のため、ダウンロード確認ダイアログを表示すべきかどうか。 */
    val isAudibleModelDownloadRequired: Boolean = false,
    /** 無音時待機秒数（秒）。 */
    val audibleSilentWaitSeconds: Float = DEFAULT_AUDIBLE_SILENT_WAIT_SECONDS,
    /** 読み上げ速度（倍）。 */
    val audibleSpeechRate: Float = DEFAULT_AUDIBLE_SPEECH_RATE
) {
    companion object {
        /** 無音時待機秒数の既定値（秒）。DataStore 側の既定値と一致させること。 */
        const val DEFAULT_AUDIBLE_SILENT_WAIT_SECONDS = 3.0f

        /** 読み上げ速度の既定値（倍）。DataStore 側の既定値と一致させること。 */
        const val DEFAULT_AUDIBLE_SPEECH_RATE = 1.0f
    }
}

/**
 * 画像ビューワのロジック、独自ソート順、エクスプローラソート準拠のフォルダまたぎ自動移動、
 * オーディブル再生（OCR→翻訳→TTS→自動送り）の状態機械を統括する ViewModel。
 *
 * [責任]: 画像独自ソート適用、フォルダ間自動移動、漫画モード分割ステップ制御、オーディブル再生制御。
 * [影響する状態]: ViewerUiState。
 * [潜在的エラー]: 画像一覧が空の場合は各操作を安全に無視する。
 */
class ViewerViewModel(
    private val storageManager: FileStorageManager = FileStorageManager(),
    private val dataStore: EEVDataStore,
    private val ocrEngine: OcrEngine = OcrEngine(),
    private val translationEngine: TranslationEngine = TranslationEngine(),
    private val ttsEngine: TtsEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(ViewerUiState())
    val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

    /** 読み上げスクリプト（OCR→翻訳）の生成と先読みキャッシュを担う。 */
    private val audibleScriptProvider = AudibleScriptProvider(ocrEngine, translationEngine)

    /** 実行中の再生セッションジョブ。停止・再開始時に cancel する。 */
    private var audibleJob: Job? = null

    /** 先読みジョブ。二重起動を防ぐため単一ジョブを保持する。 */
    private var audiblePrefetchJob: Job? = null

    /**
     * 再生セッション識別子。停止・移動のたびにインクリメントし、
     * 進行中のコールバック（TTS 完了・OCR 結果）を無効化する。
     */
    private var audibleSessionId = 0

    init {
        viewModelScope.launch {
            dataStore.isMangaModeEnabled.collectLatest { enabled ->
                _uiState.value = _uiState.value.copy(
                    isMangaMode = enabled,
                    cropPosition = if (enabled) CropPosition.RIGHT_HALF else CropPosition.FULL
                )
            }
        }
        viewModelScope.launch {
            dataStore.isTranslationEnabled.collectLatest { enabled ->
                _uiState.value = _uiState.value.copy(isTranslationEnabled = enabled)
                if (enabled) {
                    processTranslationForCurrentImage()
                } else {
                    _uiState.value = _uiState.value.copy(translationOverlayItems = emptyList())
                }
            }
        }
        viewModelScope.launch {
            dataStore.showHiddenFiles.collectLatest { show ->
                _uiState.value = _uiState.value.copy(showHiddenFiles = show)
            }
        }
        viewModelScope.launch {
            dataStore.textColorHex.collectLatest { hex ->
                _uiState.value = _uiState.value.copy(textColorHex = hex)
            }
        }
        viewModelScope.launch {
            dataStore.backgroundColorHex.collectLatest { hex ->
                _uiState.value = _uiState.value.copy(backgroundColorHex = hex)
            }
        }
        viewModelScope.launch {
            dataStore.sourceLanguage.collectLatest { lang ->
                _uiState.value = _uiState.value.copy(sourceLanguage = lang)
                if (_uiState.value.isTranslationEnabled) processTranslationForCurrentImage()
            }
        }
        viewModelScope.launch {
            dataStore.targetLanguage.collectLatest { lang ->
                _uiState.value = _uiState.value.copy(targetLanguage = lang)
                if (_uiState.value.isTranslationEnabled) processTranslationForCurrentImage()
            }
        }
        viewModelScope.launch {
            dataStore.sortOrder.collectLatest { sortOrder ->
                _uiState.value = _uiState.value.copy(explorerSortOrder = sortOrder)
            }
        }
        viewModelScope.launch {
            dataStore.viewerSortOrder.collectLatest { sortOrder ->
                // DataStore は永続化された設定値の供給元。
                // 既に同一ソート順が state に反映済みの場合は再ソートをスキップし、
                // loadImagesFromDirectory で確定した表示位置を不必要に上書きしない。
                if (_uiState.value.viewerSortOrder == sortOrder) return@collectLatest
                _uiState.value = _uiState.value.copy(viewerSortOrder = sortOrder)
                resortCurrentImages(sortOrder)
            }
        }
        // オーディブル再生モード。DataStore を唯一の真実源とし、OFF 以外で自動開始する。
        viewModelScope.launch {
            dataStore.audibleMode.collectLatest { mode ->
                val changed = _uiState.value.audibleMode != mode
                _uiState.value = _uiState.value.copy(audibleMode = mode)
                if (mode == AudibleMode.OFF) {
                    stopAudiblePlayback()
                    if (_uiState.value.isTranslationEnabled) processTranslationForCurrentImage()
                } else if (changed) {
                    requestStartAudiblePlayback()
                }
            }
        }
        viewModelScope.launch {
            dataStore.audibleSilentWaitSeconds.collectLatest { seconds ->
                _uiState.value = _uiState.value.copy(audibleSilentWaitSeconds = seconds)
            }
        }
        viewModelScope.launch {
            dataStore.audibleSpeechRate.collectLatest { rate ->
                _uiState.value = _uiState.value.copy(audibleSpeechRate = rate)
            }
        }
    }

    /**
     * 指定ディレクトリの画像一覧を [sortOrder] で読み込み、[initialFile] を起点にビューワを初期化する。
     *
     * [責任]: ビューワ表示用の画像一覧取得と起点インデックス決定。
     * [影響する状態]: ViewerUiState.imageFiles, currentIndex, viewerSortOrder, anchorFilePath。
     * [潜在的エラー]: ディレクトリ非存在時は空リストとなり currentIndex は 0 にフォールバックする。
     *
     * ソート順の唯一の真実源は本メソッドの引数 [sortOrder] である。
     * DataStore の viewerSortOrder は永続化のための保存先であり、読み込み時の決定には使用しない。
     */
    fun loadImagesFromDirectory(directory: File, initialFile: File, sortOrder: SortOrder) {
        viewModelScope.launch {
            val images = storageManager.getImageFiles(
                directory = directory,
                sortOrder = sortOrder,
                showHiddenFiles = _uiState.value.showHiddenFiles
            )
            val index = images.indexOfFirst { it.absolutePath == initialFile.absolutePath }.coerceAtLeast(0)
            _uiState.value = _uiState.value.copy(
                imageFiles = images,
                currentIndex = index,
                viewerSortOrder = sortOrder,
                anchorFilePath = initialFile.absolutePath
            )
            afterImageChanged()
        }
    }

    fun updateViewerSortOrder(sortOrder: SortOrder) {
        viewModelScope.launch {
            dataStore.setViewerSortOrder(sortOrder)
        }
    }

    /**
     * 現在のフォルダから、ビューワ独自の並び順に従って次の画像を含むフォルダへ移動する。
     *
     * [責任]: トリプルタップメニュー「次のフォルダに移動」の実行。
     * [影響する状態]: ViewerUiState.imageFiles, currentIndex, anchorFilePath, cropPosition。
     * [潜在的エラー]: 親フォルダが存在しない、または後続に画像を含むフォルダが無い場合は何もせず false を返す。
     */
    fun navigateToNextDirectoryFromMenu(): Boolean {
        val currentFile = _uiState.value.imageFiles.getOrNull(_uiState.value.currentIndex) ?: return false
        val currentDir = currentFile.parentFile ?: return false
        if (currentDir.parentFile == null) return false
        viewModelScope.launch {
            if (navigateToNextDirectorySuspending()) afterImageChanged()
        }
        return true
    }

    /**
     * 現在のフォルダの先頭画像へ移動する（ビューワは開いたまま）。
     *
     * [責任]: トリプルタップメニュー「今のフォルダの最初の画像に戻る」の実行。
     * [影響する状態]: ViewerUiState.currentIndex, cropPosition。
     * [潜在的エラー]: 画像一覧が空の場合は何もしない。
     *
     * 漫画モード時は先頭画像の右半分（RIGHT_HALF）から表示を再開する。
     */
    fun jumpToFirstImage() {
        val state = _uiState.value
        if (state.imageFiles.isEmpty()) return
        _uiState.value = state.copy(
            currentIndex = 0,
            cropPosition = if (state.isMangaMode) CropPosition.RIGHT_HALF else CropPosition.FULL
        )
        afterImageChanged()
    }

    /**
     * 現在表示中の画像を維持したまま、指定ソート順で画像一覧を再ソートする。
     *
     * [責任]: ソート順変更時の一覧再構築と表示位置の維持。
     * [影響する状態]: ViewerUiState.imageFiles, currentIndex。
     * [潜在的エラー]: 現在画像が取得できない場合は何もしない。
     */
    private fun resortCurrentImages(sortOrder: SortOrder) {
        val state = _uiState.value
        val currentFile = state.imageFiles.getOrNull(state.currentIndex)
        val anchorPath = state.anchorFilePath
            ?: currentFile?.absolutePath
            ?: return
        val targetDir = currentFile?.parentFile ?: return

        viewModelScope.launch {
            val sorted = storageManager.getImageFiles(
                directory = targetDir,
                sortOrder = sortOrder,
                showHiddenFiles = state.showHiddenFiles
            )
            val newIndex = sorted.indexOfFirst { it.absolutePath == anchorPath }.coerceAtLeast(0)
            _uiState.value = _uiState.value.copy(
                imageFiles = sorted,
                currentIndex = newIndex
            )
        }
    }

    fun onNextClicked(): Boolean {
        val state = _uiState.value
        val result = if (state.isMangaMode) {
            if (state.cropPosition == CropPosition.RIGHT_HALF) {
                _uiState.value = state.copy(cropPosition = CropPosition.LEFT_HALF)
                true
            } else {
                moveToNextImage(CropPosition.RIGHT_HALF)
            }
        } else {
            moveToNextImage(CropPosition.FULL)
        }
        afterImageChanged()
        return result
    }

    fun onPreviousClicked(): Boolean {
        val state = _uiState.value
        val result = if (state.isMangaMode) {
            if (state.cropPosition == CropPosition.LEFT_HALF) {
                _uiState.value = state.copy(cropPosition = CropPosition.RIGHT_HALF)
                true
            } else {
                moveToPreviousImage(CropPosition.LEFT_HALF)
            }
        } else {
            moveToPreviousImage(CropPosition.FULL)
        }
        afterImageChanged()
        return result
    }

    private fun moveToNextImage(initialCropPosition: CropPosition): Boolean {
        val state = _uiState.value
        return if (state.currentIndex < state.imageFiles.size - 1) {
            _uiState.value = state.copy(
                currentIndex = state.currentIndex + 1,
                cropPosition = initialCropPosition
            )
            true
        } else {
            navigateToNextDirectory()
        }
    }

    private fun moveToPreviousImage(initialCropPosition: CropPosition): Boolean {
        val state = _uiState.value
        return if (state.currentIndex > 0) {
            _uiState.value = state.copy(
                currentIndex = state.currentIndex - 1,
                cropPosition = initialCropPosition
            )
            true
        } else {
            navigateToPreviousDirectory()
        }
    }

    /**
     * 手動操作・一覧再読み込みの直後に、再生またはオーバーレイ生成を同期させる。
     *
     * [責任]: 「移動先の画像からそのまま読み上げを継続する」挙動の実現。
     * [影響する状態]: ViewerUiState.isAudiblePlaying, isTranslating。
     * [潜在的エラー]: 画像一覧が空の場合は再生を開始しない。
     */
    private fun afterImageChanged() {
        if (_uiState.value.audibleMode != AudibleMode.OFF) {
            startAudiblePlayback()
        } else if (_uiState.value.isTranslationEnabled) {
            processTranslationForCurrentImage()
        }
    }

    private fun navigateToNextDirectory(): Boolean {
        val currentFile = _uiState.value.imageFiles.getOrNull(_uiState.value.currentIndex) ?: return false
        val currentDir = currentFile.parentFile ?: return false
        if (currentDir.parentFile == null) return false

        viewModelScope.launch {
            if (navigateToNextDirectorySuspending()) afterImageChanged()
        }
        return true
    }

    /**
     * 次の画像を含むフォルダへ移動する（完了を待機できる形）。
     *
     * [責任]: フォルダまたぎ移動の実行本体。再生ループからも呼べるよう待機可能にしている。
     * [影響する状態]: ViewerUiState.imageFiles, currentIndex, anchorFilePath, cropPosition。
     * [潜在的エラー]: 後続に画像を含むフォルダが無い場合は false を返し何も変更しない。
     */
    private suspend fun navigateToNextDirectorySuspending(): Boolean {
        val currentFile = _uiState.value.imageFiles.getOrNull(_uiState.value.currentIndex) ?: return false
        val currentDir = currentFile.parentFile ?: return false
        val parentDir = currentDir.parentFile ?: return false

        val subDirs = storageManager.getSubDirectories(
            directory = parentDir,
            sortOrder = _uiState.value.viewerSortOrder,
            showHiddenFiles = _uiState.value.showHiddenFiles
        )
        val currentIndex = subDirs.indexOfFirst { it.absolutePath == currentDir.absolutePath }
        if (currentIndex in 0 until subDirs.size - 1) {
            for (i in (currentIndex + 1) until subDirs.size) {
                val nextDir = subDirs[i]
                val images = storageManager.getImageFiles(
                    directory = nextDir,
                    sortOrder = _uiState.value.viewerSortOrder,
                    showHiddenFiles = _uiState.value.showHiddenFiles
                )
                if (images.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        imageFiles = images,
                        currentIndex = 0,
                        anchorFilePath = images.first().absolutePath,
                        cropPosition = if (_uiState.value.isMangaMode) CropPosition.RIGHT_HALF else CropPosition.FULL
                    )
                    return true
                }
            }
        }
        return false
    }

    private fun navigateToPreviousDirectory(): Boolean {
        val currentFile = _uiState.value.imageFiles.getOrNull(_uiState.value.currentIndex) ?: return false
        val currentDir = currentFile.parentFile ?: return false
        if (currentDir.parentFile == null) return false

        viewModelScope.launch {
            val moved = navigateToPreviousDirectorySuspending()
            if (moved) afterImageChanged()
        }
        return true
    }

    /**
     * 前の画像を含むフォルダへ移動する（完了を待機できる形）。
     *
     * [責任]: 逆方向のフォルダまたぎ移動の実行本体。
     * [影響する状態]: ViewerUiState.imageFiles, currentIndex, anchorFilePath, cropPosition。
     * [潜在的エラー]: 先行に画像を含むフォルダが無い場合は false を返し何も変更しない。
     */
    private suspend fun navigateToPreviousDirectorySuspending(): Boolean {
        val currentFile = _uiState.value.imageFiles.getOrNull(_uiState.value.currentIndex) ?: return false
        val currentDir = currentFile.parentFile ?: return false
        val parentDir = currentDir.parentFile ?: return false

        val subDirs = storageManager.getSubDirectories(
            directory = parentDir,
            sortOrder = _uiState.value.viewerSortOrder,
            showHiddenFiles = _uiState.value.showHiddenFiles
        )
        val currentIndex = subDirs.indexOfFirst { it.absolutePath == currentDir.absolutePath }
        if (currentIndex > 0) {
            for (i in (currentIndex - 1) downTo 0) {
                val prevDir = subDirs[i]
                val images = storageManager.getImageFiles(
                    directory = prevDir,
                    sortOrder = _uiState.value.viewerSortOrder,
                    showHiddenFiles = _uiState.value.showHiddenFiles
                )
                if (images.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        imageFiles = images,
                        currentIndex = images.size - 1,
                        anchorFilePath = images.last().absolutePath,
                        cropPosition = if (_uiState.value.isMangaMode) CropPosition.LEFT_HALF else CropPosition.FULL
                    )
                    return true
                }
            }
        }
        return false
    }

    // ------------------------------------------------------------------
    // オーディブル再生（状態機械）
    // ------------------------------------------------------------------

    /**
     * オーディブル再生モードを変更する（唯一の公開エントリポイント）。
     *
     * [責任]: 3 状態の切り替え要求を DataStore へ書き込み、購読側で再生を開始／停止する。
     * [影響する状態]: `audible_mode`（および READ_TRANSLATED 時は `is_translation_enabled`）。
     * [潜在的エラー]: なし。
     */
    fun updateAudibleMode(mode: AudibleMode) {
        viewModelScope.launch {
            // 「翻訳してから読み上げる」を選んだ場合は、翻訳表示設定も連動して ON にする。
            if (mode == AudibleMode.READ_TRANSLATED) dataStore.setTranslationEnabled(true)
            dataStore.setAudibleMode(mode)
        }
    }

    /** 無音時待機秒数を変更する。 */
    fun updateAudibleSilentWaitSeconds(seconds: Float) {
        viewModelScope.launch { dataStore.setAudibleSilentWaitSeconds(seconds) }
    }

    /** 読み上げ速度（倍）を変更する。 */
    fun updateAudibleSpeechRate(rate: Float) {
        viewModelScope.launch { dataStore.setAudibleSpeechRate(rate) }
    }

    /**
     * ビューワ右上の再生ボタンからのトグル。
     *
     * [責任]: ボタン 1 つで再生開始／停止を行う。
     * [影響する状態]: `audible_mode`。
     * [潜在的エラー]: なし。
     */
    fun toggleAudiblePlayback() {
        val next = if (_uiState.value.audibleMode == AudibleMode.OFF) AudibleMode.READ_RAW else AudibleMode.OFF
        updateAudibleMode(next)
    }

    /**
     * 翻訳モデルのダウンロード確認ダイアログで「ダウンロード」を選択した際の処理。
     *
     * [責任]: モデル取得の実行と、成功時の自動再生開始。
     * [影響する状態]: ViewerUiState.isAudibleModelDownloadRequired, isAudiblePlaying。
     * [潜在的エラー]: 取得失敗時はモードを OFF に戻すのみでクラッシュしない。
     */
    fun confirmAudibleModelDownload() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAudibleModelDownloadRequired = false)
            val state = _uiState.value
            val success = translationEngine.downloadModels(state.sourceLanguage, state.targetLanguage)
            if (success) {
                startAudiblePlayback()
            } else {
                updateAudibleMode(AudibleMode.OFF)
            }
        }
    }

    /**
     * 翻訳モデルのダウンロード確認ダイアログで「キャンセル」を選択した際の処理。
     */
    fun cancelAudibleModelDownload() {
        _uiState.value = _uiState.value.copy(isAudibleModelDownloadRequired = false)
        updateAudibleMode(AudibleMode.OFF)
    }

    /**
     * 再生開始要求。翻訳モードの場合はモデル取得状況を確認してから開始する。
     *
     * [責任]: 自動開始条件（画像一覧の有無・モデル取得状況）の判定。
     * [影響する状態]: ViewerUiState.isAudibleModelDownloadRequired, isAudiblePlaying。
     * [潜在的エラー]: ビューワ未オープン（画像一覧が空）の場合は開始せず、
     * [loadImagesFromDirectory] 完了時に改めて開始される。
     */
    private suspend fun requestStartAudiblePlayback() {
        val state = _uiState.value
        if (state.audibleMode == AudibleMode.OFF) return
        if (state.imageFiles.isEmpty()) return
        if (state.audibleMode == AudibleMode.READ_TRANSLATED) {
            val ready = translationEngine.areModelsReady(state.sourceLanguage, state.targetLanguage)
            if (!ready) {
                _uiState.value = _uiState.value.copy(isAudibleModelDownloadRequired = true)
                return
            }
        }
        startAudiblePlayback()
    }

    /**
     * 再生セッションを開始する。実行中のセッションは破棄し、現在表示中の位置から読み直す。
     *
     * [責任]: セッション識別子の発行と再生ループの起動。
     * [影響する状態]: ViewerUiState.isAudiblePlaying, audibleItemIndex, audibleHighlightRect。
     * [潜在的エラー]: 画像一覧が空の場合はループが即座に終了する。
     */
    private fun startAudiblePlayback() {
        val session = ++audibleSessionId
        ttsEngine.stop()
        audibleJob?.cancel()
        audiblePrefetchJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isAudiblePlaying = true,
            audibleItemIndex = -1,
            audibleHighlightRect = null,
            isAudibleSilentWaiting = false
        )
        audibleJob = viewModelScope.launch { runAudibleSession(session) }
    }

    /**
     * 現在位置から再生を停止し、状態を初期化する。
     *
     * [責任]: TTS の即時停止、ジョブ破棄、セッション無効化、先読みキャッシュ破棄。
     * [影響する状態]: 各 audible 系フィールド。
     * [潜在的エラー]: なし。
     */
    fun stopAudiblePlayback() {
        audibleSessionId++
        audibleJob?.cancel()
        audibleJob = null
        audiblePrefetchJob?.cancel()
        audiblePrefetchJob = null
        audibleScriptProvider.clearCache()
        ttsEngine.stop()
        _uiState.value = _uiState.value.copy(
            isAudiblePlaying = false,
            audibleItemIndex = -1,
            audibleHighlightRect = null,
            isAudibleSilentWaiting = false,
            isAudibleModelDownloadRequired = false
        )
    }

    /**
     * 背景移行の直前にオーディブル再生中だったかどうか（前面復帰時の自動再開判定に使用）。
     *
     * [責任]: ライフサイクル連動で停止→再開すべきかの記憶。
     * [影響する状態]: 自身のみ（画面状態には公開しない）。
     * [潜在的エラー]: なし。
     */
    private var wasAudiblePlayingBeforeBackground = false

    /**
     * アプリが背景へ移行した際の処理。音声が背後で鳴り続けないよう再生を停止する。
     *
     * [責任]: ON_STOP 時に再生中だったかを記録し、オーディブル再生を停止する。
     * [影響する状態]: wasAudiblePlayingBeforeBackground、各 audible 系フィールド（OFF 以外のとき）。
     * [潜在的エラー]: なし。
     */
    fun onAppMovedToBackground() {
        wasAudiblePlayingBeforeBackground = _uiState.value.isAudiblePlaying
        if (_uiState.value.audibleMode != AudibleMode.OFF) {
            stopAudiblePlayback()
        }
    }

    /**
     * アプリが前面へ復帰した際の処理。背景移行時に再生中だった場合のみ自動再開する。
     *
     * [責任]: ON_START 時に「前面時のみ再生」仕様を満たすための再開制御。
     * [影響する状態]: 各 audible 系フィールド（再開時）。
     * [潜在的エラー]: なし。
     */
    fun onAppMovedToForeground() {
        val shouldResume = wasAudiblePlayingBeforeBackground
        wasAudiblePlayingBeforeBackground = false
        if (shouldResume && _uiState.value.audibleMode != AudibleMode.OFF) {
            startAudiblePlayback()
        }
    }

    /**
     * 1 つの再生セッション本体。スクリプト生成 → 順次読み上げ → 自動送りを繰り返す。
     *
     * [責任]: オーディブル再生の中核ループ。
     * [影響する状態]: ViewerUiState の audible 系フィールドおよび表示中画像。
     * [潜在的エラー]: スクリプト生成失敗（空リスト）時は無音時待機を挟んで継続する。
     */
    private suspend fun runAudibleSession(session: Int) {
        while (currentCoroutineContext().isActive && session == audibleSessionId) {
            val state = _uiState.value
            if (state.audibleMode == AudibleMode.OFF) return
            val file = state.imageFiles.getOrNull(state.currentIndex) ?: return

            val units = audibleScriptProvider.provide(
                file = file,
                cropPosition = state.cropPosition,
                mode = state.audibleMode,
                sourceLanguage = state.sourceLanguage,
                targetLanguage = state.targetLanguage
            )
            if (session != audibleSessionId) return

            // 次の読み上げ対象（漫画モードでは同一画像の残り半分）を先読みしておく。
            requestPrefetch(session, state)

            if (units.isEmpty()) {
                _uiState.value = _uiState.value.copy(isAudibleSilentWaiting = true)
                val completed = waitWhileActive(session, (state.audibleSilentWaitSeconds * 1000f).toLong())
                _uiState.value = _uiState.value.copy(isAudibleSilentWaiting = false)
                if (!completed) return
            } else {
                for (index in units.indices) {
                    if (session != audibleSessionId) return
                    val unit = units[index]
                    _uiState.value = _uiState.value.copy(
                        audibleItemIndex = index,
                        audibleHighlightRect = unit.boundingBox
                    )
                    val spoken = speakAndAwait(
                        text = unit.spokenText,
                        language = speechLanguage(state),
                        speechRate = state.audibleSpeechRate
                    )
                    if (!spoken || session != audibleSessionId) return
                }
                _uiState.value = _uiState.value.copy(audibleItemIndex = -1, audibleHighlightRect = null)
            }

            if (!advanceForAudible()) {
                _uiState.value = _uiState.value.copy(isAudiblePlaying = false, audibleItemIndex = -1, audibleHighlightRect = null)
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
     * [潜在的エラー]: なし（セッションが変わった場合は false）。
     */
    private suspend fun waitWhileActive(session: Int, durationMs: Long): Boolean {
        var remaining = durationMs
        while (remaining > 0) {
            if (session != audibleSessionId) return false
            val chunk = minOf(WAIT_CHECK_INTERVAL_MS, remaining)
            delay(chunk)
            remaining -= chunk
        }
        return session == audibleSessionId
    }

    /**
     * 読み上げ完了後に次の表示位置へ進める。
     *
     * [責任]: 通常は次画像、漫画モードでは右→左→次ページの順に進める。
     * [影響する状態]: ViewerUiState.currentIndex, cropPosition, imageFiles。
     * [潜在的エラー]: 末尾かつ後続フォルダが無い場合は false を返し再生を終了する。
     */
    private suspend fun advanceForAudible(): Boolean {
        if (_uiState.value.audibleMode == AudibleMode.OFF) return false
        val state = _uiState.value
        return if (state.isMangaMode && state.cropPosition == CropPosition.RIGHT_HALF) {
            _uiState.value = state.copy(cropPosition = CropPosition.LEFT_HALF)
            true
        } else if (state.currentIndex < state.imageFiles.size - 1) {
            _uiState.value = state.copy(
                currentIndex = state.currentIndex + 1,
                cropPosition = if (state.isMangaMode) CropPosition.RIGHT_HALF else CropPosition.FULL
            )
            true
        } else {
            navigateToNextDirectorySuspending()
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
        if (audiblePrefetchJob?.isActive == true) return
        val target: Pair<File, CropPosition> = if (state.isMangaMode && state.cropPosition == CropPosition.RIGHT_HALF) {
            state.imageFiles.getOrNull(state.currentIndex)?.let { it to CropPosition.LEFT_HALF }
        } else {
            state.imageFiles.getOrNull(state.currentIndex + 1)?.let {
                it to (if (state.isMangaMode) CropPosition.RIGHT_HALF else CropPosition.FULL)
            }
        } ?: return

        audiblePrefetchJob = viewModelScope.launch(Dispatchers.IO) {
            if (session != audibleSessionId) return@launch
            audibleScriptProvider.prefetch(
                file = target.first,
                cropPosition = target.second,
                mode = state.audibleMode,
                sourceLanguage = state.sourceLanguage,
                targetLanguage = state.targetLanguage
            )
        }
    }

    fun toggleBottomSheet(visible: Boolean) {
        _uiState.value = _uiState.value.copy(isBottomSheetVisible = visible)
    }

    fun updateMangaMode(enabled: Boolean) {
        viewModelScope.launch { dataStore.setMangaModeEnabled(enabled) }
    }

    fun updateTranslationEnabled(enabled: Boolean) {
        viewModelScope.launch { dataStore.setTranslationEnabled(enabled) }
    }

    fun updateShowHiddenFiles(enabled: Boolean) {
        viewModelScope.launch { dataStore.setShowHiddenFiles(enabled) }
    }

    fun updateTextColorHex(hex: String) {
        viewModelScope.launch { dataStore.setTextColorHex(hex) }
    }

    fun updateBackgroundColorHex(hex: String) {
        viewModelScope.launch { dataStore.setBackgroundColorHex(hex) }
    }

    fun updateSourceLanguage(language: Language) {
        viewModelScope.launch { dataStore.setSourceLanguage(language) }
    }

    fun updateTargetLanguage(language: Language) {
        viewModelScope.launch { dataStore.setTargetLanguage(language) }
    }

    /**
     * 表示中画像の翻訳オーバーレイを生成する。
     *
     * [責任]: OCR→結合→翻訳→描画順ソートの実行。
     * [影響する状態]: ViewerUiState.translationOverlayItems, isTranslating。
     * [潜在的エラー]: 画像デコード失敗時は isTranslating が false に戻らない点に注意（既存挙動を踏襲）。
     */
    private fun processTranslationForCurrentImage() {
        // オーディブル再生中は翻訳オーバーレイを描画しないため、二重の OCR 実行を避ける。
        if (_uiState.value.audibleMode != AudibleMode.OFF) return

        val currentFile = _uiState.value.imageFiles.getOrNull(_uiState.value.currentIndex) ?: return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isTranslating = true)
            val bitmap = BitmapFactory.decodeFile(currentFile.absolutePath) ?: return@launch
            val rawOcrResults = ocrEngine.recognizeText(bitmap)

            val mergedOcrResults = com.example.eev.feature.translation.OcrGroupMerger.groupAndMergeLines(rawOcrResults)

            val overlayItems = mutableListOf<TranslatedOverlayItem>()
            for (line in mergedOcrResults) {
                val translated = translationEngine.translate(
                    text = line.text,
                    sourceLanguage = _uiState.value.sourceLanguage,
                    targetLanguage = _uiState.value.targetLanguage
                )
                overlayItems.add(TranslatedOverlayItem(translatedText = translated, boundingBox = line.boundingBox))
            }

            val sortedItems = sortOverlayItemsForMangaReading(
                items = overlayItems,
                imageWidth = bitmap.width.toFloat(),
                imageHeight = bitmap.height.toFloat()
            )

            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    translationOverlayItems = sortedItems,
                    isTranslating = false
                )
            }
        }
    }

    private fun sortOverlayItemsForMangaReading(
        items: List<TranslatedOverlayItem>,
        imageWidth: Float,
        imageHeight: Float
    ): List<TranslatedOverlayItem> {
        val midX = imageWidth / 2f
        val midY = imageHeight / 2f

        return items.sortedWith { a, b ->
            val rectA = a.boundingBox ?: RectF()
            val rectB = b.boundingBox ?: RectF()

            val zoneA = getZonePriority(rectA, midX, midY)
            val zoneB = getZonePriority(rectB, midX, midY)

            if (zoneA != zoneB) {
                zoneA.compareTo(zoneB)
            } else {
                if (kotlin.math.abs(rectA.top - rectB.top) > 40f) {
                    rectA.top.compareTo(rectB.top)
                } else {
                    rectB.right.compareTo(rectA.right)
                }
            }
        }
    }

    private fun getZonePriority(rect: RectF, midX: Float, midY: Float): Int {
        val cx = rect.centerX()
        val cy = rect.centerY()
        return when {
            cy < midY && cx >= midX -> 1 // 右上
            cy < midY && cx < midX -> 2  // 左上
            cy >= midY && cx >= midX -> 3 // 右下
            else -> 4                    // 左下
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopAudiblePlayback()
        ttsEngine.shutdown()
        translationEngine.close()
    }

    companion object {
        /** 無音時待機中のセッション確認間隔（ミリ秒）。 */
        private const val WAIT_CHECK_INTERVAL_MS = 100L
    }
}
