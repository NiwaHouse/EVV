package com.example.eev.feature.viewer

import android.graphics.BitmapFactory
import android.graphics.RectF
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eev.core.datastore.EEVDataStore
import com.example.eev.core.model.Language
import com.example.eev.core.model.SortOrder
import com.example.eev.core.storage.FileStorageManager
import com.example.eev.feature.translation.OcrEngine
import com.example.eev.feature.translation.TranslatedOverlayItem
import com.example.eev.feature.translation.TranslationEngine
import com.example.eev.feature.translation.TtsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
    val isBottomSheetVisible: Boolean = false,
    val translationOverlayItems: List<TranslatedOverlayItem> = emptyList(),
    val isTranslating: Boolean = false,
    val isReadingTts: Boolean = false,
    val readingTtsIndex: Int = -1
)

/**
 * 画像ビューワのロジック、独自ソート順、エクスプローラソート準拠のフォルダまたぎ自動移動、TTS読み上げを統括する ViewModel。
 *
 * [責任]: 画像独自ソート適用、エクスプローラ並び順準拠フォルダ間自動移動、漫画モード分割ステップ制御。
 * [影響する状態]: ViewerUiState。
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
                _uiState.value = _uiState.value.copy(viewerSortOrder = sortOrder)
                resortCurrentImages(sortOrder)
            }
        }
    }

    fun loadImagesFromDirectory(directory: File, initialFile: File, sortOrder: SortOrder) {
        viewModelScope.launch {
            val targetSortOrder = _uiState.value.viewerSortOrder
            val images = storageManager.getImageFiles(
                directory = directory,
                sortOrder = targetSortOrder,
                showHiddenFiles = _uiState.value.showHiddenFiles
            )
            val index = images.indexOfFirst { it.absolutePath == initialFile.absolutePath }.coerceAtLeast(0)
            _uiState.value = _uiState.value.copy(
                imageFiles = images,
                currentIndex = index
            )
            if (_uiState.value.isTranslationEnabled) {
                processTranslationForCurrentImage()
            }
        }
    }

    fun updateViewerSortOrder(sortOrder: SortOrder) {
        viewModelScope.launch {
            dataStore.setViewerSortOrder(sortOrder)
        }
    }

    private fun resortCurrentImages(sortOrder: SortOrder) {
        val currentFile = _uiState.value.imageFiles.getOrNull(_uiState.value.currentIndex)
        if (currentFile != null && currentFile.parentFile != null) {
            viewModelScope.launch {
                val sorted = storageManager.getImageFiles(
                    directory = currentFile.parentFile!!,
                    sortOrder = sortOrder,
                    showHiddenFiles = _uiState.value.showHiddenFiles
                )
                val newIndex = sorted.indexOfFirst { it.absolutePath == currentFile.absolutePath }.coerceAtLeast(0)
                _uiState.value = _uiState.value.copy(
                    imageFiles = sorted,
                    currentIndex = newIndex
                )
            }
        }
    }

    fun onNextClicked(): Boolean {
        stopTtsReading()
        val state = _uiState.value
        if (state.isMangaMode) {
            if (state.cropPosition == CropPosition.RIGHT_HALF) {
                _uiState.value = state.copy(cropPosition = CropPosition.LEFT_HALF)
                return true
            } else {
                return moveToNextImage(CropPosition.RIGHT_HALF)
            }
        } else {
            return moveToNextImage(CropPosition.FULL)
        }
    }

    fun onPreviousClicked(): Boolean {
        stopTtsReading()
        val state = _uiState.value
        if (state.isMangaMode) {
            if (state.cropPosition == CropPosition.LEFT_HALF) {
                _uiState.value = state.copy(cropPosition = CropPosition.RIGHT_HALF)
                return true
            } else {
                return moveToPreviousImage(CropPosition.LEFT_HALF)
            }
        } else {
            return moveToPreviousImage(CropPosition.FULL)
        }
    }

    private fun moveToNextImage(initialCropPosition: CropPosition): Boolean {
        val state = _uiState.value
        return if (state.currentIndex < state.imageFiles.size - 1) {
            _uiState.value = state.copy(
                currentIndex = state.currentIndex + 1,
                cropPosition = initialCropPosition
            )
            if (_uiState.value.isTranslationEnabled) processTranslationForCurrentImage()
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
            if (_uiState.value.isTranslationEnabled) processTranslationForCurrentImage()
            true
        } else {
            navigateToPreviousDirectory()
        }
    }

    private fun navigateToNextDirectory(): Boolean {
        val currentFile = _uiState.value.imageFiles.getOrNull(_uiState.value.currentIndex) ?: return false
        val currentDir = currentFile.parentFile ?: return false
        val parentDir = currentDir.parentFile ?: return false

        viewModelScope.launch {
            val subDirs = storageManager.getSubDirectories(
                directory = parentDir,
                sortOrder = _uiState.value.explorerSortOrder,
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
                            cropPosition = if (_uiState.value.isMangaMode) CropPosition.RIGHT_HALF else CropPosition.FULL
                        )
                        if (_uiState.value.isTranslationEnabled) processTranslationForCurrentImage()
                        break
                    }
                }
            }
        }
        return true
    }

    private fun navigateToPreviousDirectory(): Boolean {
        val currentFile = _uiState.value.imageFiles.getOrNull(_uiState.value.currentIndex) ?: return false
        val currentDir = currentFile.parentFile ?: return false
        val parentDir = currentDir.parentFile ?: return false

        viewModelScope.launch {
            val subDirs = storageManager.getSubDirectories(
                directory = parentDir,
                sortOrder = _uiState.value.explorerSortOrder,
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
                            cropPosition = if (_uiState.value.isMangaMode) CropPosition.LEFT_HALF else CropPosition.FULL
                        )
                        if (_uiState.value.isTranslationEnabled) processTranslationForCurrentImage()
                        break
                    }
                }
            }
        }
        return true
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

    fun startTtsReading() {
        val items = _uiState.value.translationOverlayItems
        if (items.isEmpty()) return

        _uiState.value = _uiState.value.copy(
            isReadingTts = true,
            readingTtsIndex = 0
        )
        speakNextTtsItem()
    }

    private fun speakNextTtsItem() {
        val state = _uiState.value
        if (!state.isReadingTts) return

        val items = state.translationOverlayItems
        val index = state.readingTtsIndex

        if (index >= 0 && index < items.size) {
            val item = items[index]
            val textToSpeak = item.translatedText
            val targetLang = state.targetLanguage

            ttsEngine.speak(textToSpeak, targetLang) {
                viewModelScope.launch(Dispatchers.Main) {
                    if (_uiState.value.isReadingTts) {
                        val nextIndex = _uiState.value.readingTtsIndex + 1
                        if (nextIndex < _uiState.value.translationOverlayItems.size) {
                            _uiState.value = _uiState.value.copy(readingTtsIndex = nextIndex)
                            speakNextTtsItem()
                        } else {
                            stopTtsReading()
                        }
                    }
                }
            }
        } else {
            stopTtsReading()
        }
    }

    fun stopTtsReading() {
        ttsEngine.stop()
        _uiState.value = _uiState.value.copy(
            isReadingTts = false,
            readingTtsIndex = -1
        )
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

    private fun processTranslationForCurrentImage() {
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

    override fun onCleared() {
        super.onCleared()
        ttsEngine.shutdown()
        translationEngine.close()
    }
}
