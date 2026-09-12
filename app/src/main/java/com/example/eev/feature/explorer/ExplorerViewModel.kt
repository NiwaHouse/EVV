package com.example.eev.feature.explorer

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eev.core.datastore.EEVDataStore
import com.example.eev.core.model.FileItem
import com.example.eev.core.model.SortOrder
import com.example.eev.core.model.ThumbnailSize
import com.example.eev.core.model.ViewMode
import com.example.eev.core.storage.FileStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/**
 * ファイルエクスプローラ画面のUI State。
 */
data class ExplorerUiState(
    val currentDirectory: File = Environment.getExternalStorageDirectory(),
    val fileItems: List<FileItem> = emptyList(),
    val sortOrder: SortOrder = SortOrder.NAME_ASC,
    val viewMode: ViewMode = ViewMode.LIST,
    val showHiddenFiles: Boolean = false,
    /** サムネイル表示サイズ（大・中・小）。 */
    val thumbnailSize: ThumbnailSize = ThumbnailSize.MEDIUM,
    /** 日付・枚数などの付加情報を表示するか。 */
    val showItemInfo: Boolean = false,
    /** 戻るボタンで前のフォルダへ戻れるか（履歴が存在するか）。 */
    val canNavigateBack: Boolean = false,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val isLoading: Boolean = false
) {
    val filteredFileItems: List<FileItem>
        get() = if (searchQuery.isBlank()) {
            fileItems
        } else {
            fileItems.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
}

/**
 * エクスプローラ画面の状態保持と非同期メタデータ読み込み、ナビゲーションを担当する ViewModel。
 *
 * [責任]: ディレクトリ表示、非同期サムネイル取得、検索フィルタ、表示モード切り替え。
 * [影響する状態]: ExplorerUiState。
 * [潜在的エラー]: パーミッション不足、非存在ディレクトリ。
 */
class ExplorerViewModel(
    private val storageManager: FileStorageManager = FileStorageManager(),
    private val dataStore: EEVDataStore,
    private val thumbnailCache: com.example.eev.feature.thumbnail.FolderThumbnailCache? = null,
    /**
     * 起動時の初期表示ディレクトリ。
     * 権限不要でアクセス可能なアプリ専用フォルダ（filesDir）を既定とし、
     * 全ファイルアクセス許可時は外部ストレージを指定する。
     */
    initialDirectory: File = Environment.getExternalStorageDirectory()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExplorerUiState(currentDirectory = initialDirectory))
    val uiState: StateFlow<ExplorerUiState> = _uiState.asStateFlow()

    /**
     * フォルダパスをキーとした保存済みスクロール位置マップ。
     * DataStore から購読し、画面側で復元に使用する。
     */
    val scrollPositions: StateFlow<Map<String, Int>> = dataStore.explorerScrollPositions
        .stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.Eagerly,
            initialValue = emptyMap()
        )

    /**
     * DataStore の初回通知で重複ロードが走らないよう、初回ロード済みかを管理するフラグ。
     * sortOrder / showHiddenFiles の初回購読通知では再ロードせず、値の変更時のみ再ロードする。
     */
    private var isInitialLoadDone = false

    /**
     * フォルダ移動履歴スタック（絶対パスのリスト）。
     *
     * [責任]: 権限のないフォルダへ入り込んだ場合でも、直前まで表示していた
     *         フォルダへ戻れるようにするためのナビゲーション履歴を保持する。
     * [影響する状態]: ExplorerUiState.canNavigateBack。
     * [潜在的エラー]: なし（メモリ上のみで管理し、永続化はしない）。
     */
    private val directoryHistory = ArrayDeque<String>()

    init {
        viewModelScope.launch {
            dataStore.sortOrder.collectLatest { order ->
                val changed = _uiState.value.sortOrder != order
                _uiState.value = _uiState.value.copy(sortOrder = order)
                // 初回通知は初期ロードと重複するためスキップし、実際の変更時のみ再ロードする。
                if (changed && isInitialLoadDone) {
                    loadDirectory(_uiState.value.currentDirectory)
                }
            }
        }
    }

    init {
        viewModelScope.launch {
            dataStore.viewMode.collectLatest { mode ->
                _uiState.value = _uiState.value.copy(viewMode = mode)
            }
        }
    }

    init {
        viewModelScope.launch {
            dataStore.showHiddenFiles.collectLatest { show ->
                val changed = _uiState.value.showHiddenFiles != show
                _uiState.value = _uiState.value.copy(showHiddenFiles = show)
                // 初回通知は初期ロードと重複するためスキップし、実際の変更時のみ再ロードする。
                if (changed && isInitialLoadDone) {
                    loadDirectory(_uiState.value.currentDirectory)
                }
            }
        }
    }

    init {
        viewModelScope.launch {
            dataStore.thumbnailSize.collectLatest { size ->
                _uiState.value = _uiState.value.copy(thumbnailSize = size)
            }
        }
    }

    init {
        viewModelScope.launch {
            dataStore.showItemInfo.collectLatest { show ->
                _uiState.value = _uiState.value.copy(showItemInfo = show)
            }
        }
    }

    init {
        // 初期ディレクトリを即座に読み込む（DataStore 購読の初回通知を待たない）。
        loadDirectory(initialDirectory)
    }

    /**
     * 指定ディレクトリを読み込んで表示する。
     *
     * [責任]: ディレクトリ一覧の取得と画面反映、およびナビゲーション履歴の更新。
     * [影響する状態]: ExplorerUiState.currentDirectory, fileItems, canNavigateBack。
     * [潜在的エラー]: 権限不足・非存在ディレクトリの場合は空一覧となる。
     *
     * @param directory 表示対象フォルダ。
     * @param recordHistory true の場合、現在のフォルダを履歴に積んでから移動する
     *        （ユーザー操作による前進移動）。戻る操作や初期ロードでは false を指定する。
     */
    fun loadDirectory(directory: File, recordHistory: Boolean = true) {
        val previous = _uiState.value.currentDirectory
        if (recordHistory && previous.absolutePath != directory.absolutePath) {
            directoryHistory.addLast(previous.absolutePath)
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                searchQuery = "",
                canNavigateBack = directoryHistory.isNotEmpty()
            )

            // 1. まず高速にファイル一覧を取得し画面に初期描画（即座に反応）
            val quickItems = storageManager.getQuickFilesAndFolders(
                directory = directory,
                sortOrder = _uiState.value.sortOrder,
                showHiddenFiles = _uiState.value.showHiddenFiles
            )
            _uiState.value = _uiState.value.copy(
                currentDirectory = directory,
                fileItems = quickItems,
                isLoading = false,
                canNavigateBack = directoryHistory.isNotEmpty()
            )
            isInitialLoadDone = true

            // 2. フォルダのサムネイル・画像数を非同期並列で取得してバッチ反映する
            loadFolderMetadataAsync(quickItems)
        }
    }

    /**
     * 履歴スタックを1つ戻り、直前に表示していたフォルダへ移動する。
     *
     * [責任]: 権限のないフォルダへ入り込んだ際の復帰手段の提供。
     * [影響する状態]: ExplorerUiState.currentDirectory, fileItems, canNavigateBack。
     * [潜在的エラー]: 履歴が空の場合は何もせず false を返す。
     *
     * @return 戻れた場合は true、履歴が無い場合は false。
     */
    fun navigateBack(): Boolean {
        val previousPath = directoryHistory.removeLastOrNull() ?: return false
        loadDirectory(File(previousPath), recordHistory = false)
        return true
    }

    /**
     * フォルダのサムネイル・画像数を非同期で取得し、まとめてUIへ反映する。
     *
     * [責任]: フォルダメタデータの非同期取得とバッチ更新。
     * [影響する状態]: ExplorerUiState.fileItems。
     * [潜在的エラー]: フォルダ読み取り失敗時は該当項目をスキップする。
     *
     * 1件ごとに StateFlow を更新すると再コンポーズが多発するため、
     * 取得結果をローカルに蓄積し、[METADATA_BATCH_SIZE] 件ごとおよび完了時に一括反映する。
     */
    private fun loadFolderMetadataAsync(items: List<FileItem>) {
        viewModelScope.launch(Dispatchers.IO) {
            // パス -> (画像数, サムネイルパス) の差分マップ
            val updates = mutableMapOf<String, Pair<Int, String?>>()
            var processedSinceFlush = 0

            items.forEach { item ->
                if (item.isDirectory) {
                    val (count, firstPath) = storageManager.getFolderMetadata(
                        folder = item.file,
                        showHiddenFiles = _uiState.value.showHiddenFiles
                    )
                    // サムネイルは軽量キャッシュ画像を優先し、生成できない場合は元画像パスへフォールバックする。
                    val thumbnailPath = firstPath?.let { path ->
                        thumbnailCache?.getOrCreateThumbnail(path) ?: path
                    }
                    updates[item.file.absolutePath] = count to thumbnailPath
                    processedSinceFlush++

                    // 一定件数ごとにまとめて反映し、再コンポーズ回数を抑える。
                    if (processedSinceFlush >= METADATA_BATCH_SIZE) {
                        applyMetadataUpdates(updates)
                        updates.clear()
                        processedSinceFlush = 0
                    }
                }
            }

            // 残りを最終反映する。
            if (updates.isNotEmpty()) {
                applyMetadataUpdates(updates)
            }
        }
    }

    /**
     * 蓄積したメタデータ差分を現在の一覧へ一括反映する。
     *
     * [責任]: メタデータ差分のUI反映。
     * [影響する状態]: ExplorerUiState.fileItems。
     * [潜在的エラー]: 対象パスが一覧から消えている場合は該当項目を無視する。
     */
    private fun applyMetadataUpdates(updates: Map<String, Pair<Int, String?>>) {
        if (updates.isEmpty()) return
        val currentItems = _uiState.value.fileItems.toMutableList()
        var modified = false
        currentItems.forEachIndexed { index, current ->
            val update = updates[current.file.absolutePath] ?: return@forEachIndexed
            currentItems[index] = current.copy(
                imageCount = update.first,
                firstImagePath = update.second
            )
            modified = true
        }
        if (modified) {
            _uiState.value = _uiState.value.copy(fileItems = currentItems)
        }
    }

    fun navigateToParent(): Boolean {
        val parent = _uiState.value.currentDirectory.parentFile
        return if (parent != null && parent.canRead()) {
            loadDirectory(parent)
            true
        } else {
            false
        }
    }

    fun updateSortOrder(sortOrder: SortOrder) {
        viewModelScope.launch { dataStore.setSortOrder(sortOrder) }
    }

    fun updateThumbnailSize(size: ThumbnailSize) {
        viewModelScope.launch { dataStore.setThumbnailSize(size) }
    }

    fun updateShowItemInfo(enabled: Boolean) {
        viewModelScope.launch { dataStore.setShowItemInfo(enabled) }
    }

    fun updateViewMode(viewMode: ViewMode) {
        viewModelScope.launch { dataStore.setViewMode(viewMode) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun toggleSearchActive(active: Boolean) {
        _uiState.value = _uiState.value.copy(
            isSearchActive = active,
            searchQuery = if (!active) "" else _uiState.value.searchQuery
        )
    }

    /**
     * 指定フォルダのスクロール位置（先頭表示インデックス）を永続化する。
     *
     * [責任]: フォルダごとのスクロール位置保存。
     * [影響する状態]: DataStore の `explorer_scroll_positions`。
     * [潜在的エラー]: 保存失敗時は DataStore 側で保護される。
     */
    fun saveScrollPosition(folderPath: String, index: Int) {
        viewModelScope.launch { dataStore.setScrollPosition(folderPath, index) }
    }

    companion object {
        /**
         * メタデータをUIへ反映する単位（件数）。
         * 小さすぎると再コンポーズが増え、大きすぎると反映が遅延するため中間値を採用する。
         */
        private const val METADATA_BATCH_SIZE = 8
    }
}
