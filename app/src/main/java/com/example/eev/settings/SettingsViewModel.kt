package com.example.eev.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eev.core.datastore.EEVDataStore
import com.example.eev.core.model.Language
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 設定画面のUI State。
 */
data class SettingsUiState(
    val isMangaModeEnabled: Boolean = false,
    val isTranslationEnabled: Boolean = false,
    val showHiddenFiles: Boolean = false,
    val textColorHex: String = "#FFFFFF",
    val backgroundColorHex: String = "#CC000000",
    val sourceLanguage: Language = Language.JAPANESE,
    val targetLanguage: Language = Language.ENGLISH
)

/**
 * アプリ設定画面のロジックを管理する ViewModel。
 *
 * [責任]: DataStore への設定変更の書き込みと UI 状態の共有。
 * [影響する状態]: SettingsUiState。
 */
class SettingsViewModel(
    private val dataStore: EEVDataStore
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(dataStore.isMangaModeEnabled, dataStore.isTranslationEnabled, dataStore.showHiddenFiles) { m, t, h ->
            Triple(m, t, h)
        },
        combine(dataStore.textColorHex, dataStore.backgroundColorHex) { text, bg ->
            Pair(text, bg)
        },
        combine(dataStore.sourceLanguage, dataStore.targetLanguage) { src, tgt ->
            Pair(src, tgt)
        }
    ) { (manga, translation, hidden), (textHex, bgHex), (source, target) ->
        SettingsUiState(
            isMangaModeEnabled = manga,
            isTranslationEnabled = translation,
            showHiddenFiles = hidden,
            textColorHex = textHex,
            backgroundColorHex = bgHex,
            sourceLanguage = source,
            targetLanguage = target
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

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
}
