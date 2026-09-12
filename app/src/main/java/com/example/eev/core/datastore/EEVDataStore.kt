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
import com.example.eev.core.model.ThumbnailSize
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
        private val KEY_SCROLL_POSITIONS = stringPreferencesKey("explorer_scroll_positions")
        private val KEY_THUMBNAIL_SIZE = stringPreferencesKey("thumbnail_size")
        private val KEY_SHOW_ITEM_INFO = booleanPreferencesKey("show_item_info")
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

    /**
     * エクスプローラのサムネイル表示サイズ（大・中・小）。
     * 未設定時は [ThumbnailSize.MEDIUM] を既定とする。
     */
    val thumbnailSize: Flow<ThumbnailSize> = context.dataStore.data.map { prefs ->
        ThumbnailSize.fromName(prefs[KEY_THUMBNAIL_SIZE] ?: ThumbnailSize.MEDIUM.name)
    }

    /**
     * エクスプローラで日付・枚数などの付加情報を表示するか。
     * 未設定時は false（非表示）を既定とする。
     */
    val showItemInfo: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SHOW_ITEM_INFO] ?: false
    }

    /**
     * フォルダパスをキーとしたエクスプローラのスクロール位置（先頭表示アイテムのインデックス）マップ。
     *
     * 保存形式は `パス=インデックス` を改行で連結した文字列。
     * パスに `=` や改行が含まれる場合は保存対象外とする（破損防止）。
     */
    val explorerScrollPositions: Flow<Map<String, Int>> = context.dataStore.data.map { prefs ->
        decodeScrollPositions(prefs[KEY_SCROLL_POSITIONS] ?: "")
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

    suspend fun setThumbnailSize(size: ThumbnailSize) {
        context.dataStore.edit { prefs ->
            prefs[KEY_THUMBNAIL_SIZE] = size.name
        }
    }

    suspend fun setShowItemInfo(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SHOW_ITEM_INFO] = enabled
        }
    }

    /**
     * 指定フォルダのスクロール位置を保存する。
     *
     * [責任]: フォルダごとの先頭表示インデックスの永続化。
     * [影響する状態]: `explorer_scroll_positions`。
     * [潜在的エラー]: パスに `=` または改行を含む場合は保存をスキップする。
     */
    suspend fun setScrollPosition(folderPath: String, index: Int) {
        if (folderPath.contains('=') || folderPath.contains('\n')) return
        context.dataStore.edit { prefs ->
            val current = decodeScrollPositions(prefs[KEY_SCROLL_POSITIONS] ?: "").toMutableMap()
            current[folderPath] = index
            prefs[KEY_SCROLL_POSITIONS] = encodeScrollPositions(current)
        }
    }

    /**
     * 保存済みスクロール位置マップを `パス=インデックス` 形式の文字列へ変換する。
     */
    private fun encodeScrollPositions(map: Map<String, Int>): String =
        map.entries.joinToString("\n") { "${it.key}=${it.value}" }

    /**
     * `パス=インデックス` 形式の文字列をマップへ復元する。不正行は無視する。
     */
    private fun decodeScrollPositions(raw: String): Map<String, Int> {
        if (raw.isBlank()) return emptyMap()
        return raw.lineSequence().mapNotNull { line ->
            val separator = line.lastIndexOf('=')
            if (separator <= 0) return@mapNotNull null
            val path = line.substring(0, separator)
            val index = line.substring(separator + 1).toIntOrNull() ?: return@mapNotNull null
            path to index
        }.toMap()
    }
}
