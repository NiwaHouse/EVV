package com.example.eev.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.eev.core.model.Language
import com.example.eev.core.model.SortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "eev_settings")

/**
 * アプリ全体の設定値を DataStore Preferences により永続化・管理するクラス。
 *
 * [責任]: 漫画モードON/OFF、翻訳ON/OFF、言語設定、ソート順の保存と非同期読み込み。
 * [影響する状態]: 永続化された設定値全般。
 * [潜在的エラー]: DataStore読み書き失敗時のIOException発生可能性（デフォルト値返却で保護）。
 */
class EEVDataStore(private val context: Context) {

    companion object {
        private val KEY_MANGA_MODE = booleanPreferencesKey("is_manga_mode_enabled")
        private val KEY_TRANSLATION_ENABLED = booleanPreferencesKey("is_translation_enabled")
        private val KEY_SOURCE_LANG = stringPreferencesKey("source_language")
        private val KEY_TARGET_LANG = stringPreferencesKey("target_language")
        private val KEY_SORT_ORDER = stringPreferencesKey("sort_order")
        private val KEY_VIEW_MODE = stringPreferencesKey("view_mode")
        private val KEY_SHOW_HIDDEN_FILES = booleanPreferencesKey("show_hidden_files")
        private val KEY_TEXT_COLOR = stringPreferencesKey("text_color_hex")
        private val KEY_BG_COLOR = stringPreferencesKey("bg_color_hex")
        private val KEY_VIEWER_SORT_ORDER = stringPreferencesKey("viewer_sort_order")
    }

    val isMangaModeEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_MANGA_MODE] ?: false
    }

    val isTranslationEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_TRANSLATION_ENABLED] ?: false
    }

    val sourceLanguage: Flow<Language> = context.dataStore.data.map { prefs ->
        Language.fromCode(prefs[KEY_SOURCE_LANG] ?: Language.JAPANESE.code)
    }

    val targetLanguage: Flow<Language> = context.dataStore.data.map { prefs ->
        Language.fromCode(prefs[KEY_TARGET_LANG] ?: Language.ENGLISH.code)
    }

    val sortOrder: Flow<SortOrder> = context.dataStore.data.map { prefs ->
        SortOrder.fromName(prefs[KEY_SORT_ORDER] ?: SortOrder.NAME_ASC.name)
    }

    val viewMode: Flow<com.example.eev.core.model.ViewMode> = context.dataStore.data.map { prefs ->
        com.example.eev.core.model.ViewMode.fromName(prefs[KEY_VIEW_MODE] ?: com.example.eev.core.model.ViewMode.LIST.name)
    }

    val showHiddenFiles: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SHOW_HIDDEN_FILES] ?: false
    }

    val textColorHex: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_TEXT_COLOR] ?: "#FFFFFF"
    }

    val backgroundColorHex: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_BG_COLOR] ?: "#CC000000"
    }

    val viewerSortOrder: Flow<SortOrder> = context.dataStore.data.map { prefs ->
        SortOrder.fromName(prefs[KEY_VIEWER_SORT_ORDER] ?: SortOrder.NAME_ASC.name)
    }

    suspend fun setMangaModeEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_MANGA_MODE] = enabled
        }
    }

    suspend fun setTranslationEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TRANSLATION_ENABLED] = enabled
        }
    }

    suspend fun setSourceLanguage(language: Language) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SOURCE_LANG] = language.code
        }
    }

    suspend fun setTargetLanguage(language: Language) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TARGET_LANG] = language.code
        }
    }

    suspend fun setSortOrder(sortOrder: SortOrder) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SORT_ORDER] = sortOrder.name
        }
    }

    suspend fun setViewMode(viewMode: com.example.eev.core.model.ViewMode) {
        context.dataStore.edit { prefs ->
            prefs[KEY_VIEW_MODE] = viewMode.name
        }
    }

    suspend fun setShowHiddenFiles(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SHOW_HIDDEN_FILES] = enabled
        }
    }

    suspend fun setTextColorHex(hex: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TEXT_COLOR] = hex
        }
    }

    suspend fun setBackgroundColorHex(hex: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BG_COLOR] = hex
        }
    }

    suspend fun setViewerSortOrder(sortOrder: SortOrder) {
        context.dataStore.edit { prefs ->
            prefs[KEY_VIEWER_SORT_ORDER] = sortOrder.name
        }
    }
}
