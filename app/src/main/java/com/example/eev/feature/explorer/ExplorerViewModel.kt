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
import com.example.eev.core.storage.FolderMetadata
import com.example.eev.feature.thumbnail.FolderThumbnailCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/**
 * エクスプローラ画面の状態保持とメタデータ読み込み、ナビゲーション、削除・お気に入り操作を担当する ViewModel。
 *
 * [責任]: ディレクトリ表示、個別ソート順制御、お気に入り登録/解除、ファイル削除、検索フィルタ。
 * [影響する状態]: [ExplorerUiState]。
 * [潜在的エラー]: パーミッション不足、非存在ディレクトリ。
 */
class ExplorerViewModel(
    private val storageManager: FileStorageManager = FileStorageManager(),
    private val dataStore: EEVDataStore,
    thumbnailCache: FolderThumbnailCache? = null,
    initialDirectory: File = Environment.getExternalStorageDirectory()
) : ViewModel() {

    private val metadataLoader = ExplorerMetadataLoader(storageManager, thumbnailCache)
    private val _uiState = MutableStateFlow(ExplorerUiState(currentDirectory = initialDirectory))
    val uiState: StateFlow<ExplorerUiState> = _uiState.asStateFlow()

    val scrollPositions: StateFlow<Map<String, Int>> = dataStore.explorerScrollPositions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyMap()
        )

    private var isInitialLoadDone = false
    private val directoryHistory = ArrayDeque<String>()

    init {
        // フォルダ用ソート順の監視
        viewModelScope.launch {
            dataStore.folderSortOrder.collectLatest { order ->
                val changed = _uiState.value.folderSortOrder != order
                _uiState.value = _uiState.value.copy(folderSortOrder = order)
                if (changed && isInitialLoadDone) {
                    loadDirectory(_uiState.value.currentDirectory, recordHistory = false)
                }
            }
        }
        // ファイル用ソート順の監視
        viewModelScope.launch {
            dataStore.fileSortOrder.collectLatest { order ->
                val changed = _uiState.value.fileSortOrder != order
                _uiState.value = _uiState.value.copy(fileSortOrder = order)
                if (changed && isInitialLoadDone) {
                    loadDirectory(_uiState.value.currentDirectory, recordHistory = false)
                }
            }
        }
        // お気に入りフォルダの監視
        viewModelScope.launch {
            dataStore.favoriteFolders.collectLatest { folders ->
                _uiState.value = _uiState.value.copy(favoriteFolders = folders)
            }
        }
        viewModelScope.launch {
            dataStore.viewMode.collectLatest { mode ->
                _uiState.value = _uiState.value.copy(viewMode = mode)
            }
        }
        viewModelScope.launch {
            dataStore.showHiddenFiles.collectLatest { show ->
                val changed = _uiState.value.showHiddenFiles != show
                _uiState.value = _uiState.value.copy(showHiddenFiles = show)
                if (changed && isInitialLoadDone) {
                    loadDirectory(_uiState.value.currentDirectory, recordHistory = false)
                }
            }
        }
        viewModelScope.launch {
            dataStore.thumbnailSize.collectLatest { size ->
                _uiState.value = _uiState.value.copy(thumbnailSize = size)
            }
        }
        viewModelScope.launch {
            dataStore.showItemInfo.collectLatest { show ->
                _uiState.value = _uiState.value.copy(showItemInfo = show)
            }
        }

        loadDirectory(initialDirectory, recordHistory = false)
    }

    /**
     * 指定ディレクトリを読み込んで表示する。
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

            val rawItems = storageManager.getQuickFilesAndFolders(
                directory = directory,
                sortOrder = _uiState.value.folderSortOrder,
                showHiddenFiles = _uiState.value.showHiddenFiles
            )
            val sortedItems = storageManager.sortFileItemsSeparately(
                items = rawItems,
                folderOrder = _uiState.value.folderSortOrder,
                fileOrder = _uiState.value.fileSortOrder
            )

            _uiState.value = _uiState.value.copy(
                currentDirectory = directory,
                fileItems = sortedItems,
                isLoading = false,
                canNavigateBack = directoryHistory.isNotEmpty()
            )
            isInitialLoadDone = true

            metadataLoader.loadAsync(
                scope = viewModelScope,
                items = sortedItems,
                showHiddenFiles = _uiState.value.showHiddenFiles,
                onBatchUpdates = { updates -> applyMetadataUpdates(updates) }
            )
        }
    }

    /**
     * 履歴スタックを1つ戻り、直前に表示していたフォルダへ移動する。
     */
    fun navigateBack(): Boolean {
        val previousPath = directoryHistory.removeLastOrNull() ?: return false
        loadDirectory(File(previousPath), recordHistory = false)
        return true
    }

    private fun applyMetadataUpdates(updates: Map<String, FolderMetadata>) {
        if (updates.isEmpty()) return
        val currentItems = _uiState.value.fileItems.toMutableList()
        var modified = false
        currentItems.forEachIndexed { index, current ->
            val update = updates[current.file.absolutePath] ?: return@forEachIndexed
            currentItems[index] = current.copy(
                imageCount = update.imageCount,
                firstImagePath = update.firstImagePath,
                hasTransDb = update.hasTransDb
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

    /** フォルダソート順を更新する。 */
    fun updateFolderSortOrder(order: SortOrder) {
        viewModelScope.launch { dataStore.setFolderSortOrder(order) }
    }

    /** ファイルソート順を更新する。 */
    fun updateFileSortOrder(order: SortOrder) {
        viewModelScope.launch { dataStore.setFileSortOrder(order) }
    }

    /** 旧互換用ソート順更新。 */
    fun updateSortOrder(sortOrder: SortOrder) {
        updateFolderSortOrder(sortOrder)
        updateFileSortOrder(sortOrder)
    }

    /** お気に入りフォルダの登録・解除をトグルする。 */
    fun toggleFavorite(folderPath: String) {
        viewModelScope.launch {
            if (_uiState.value.favoriteFolders.contains(folderPath)) {
                dataStore.removeFavoriteFolder(folderPath)
            } else {
                dataStore.addFavoriteFolder(folderPath)
            }
        }
    }

    /** ファイルまたはフォルダを完全削除する。 */
    fun deleteItem(item: FileItem, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val success = storageManager.deleteItem(item.file)
            if (success) {
                if (item.isDirectory) {
                    dataStore.removeFavoriteFolder(item.file.absolutePath)
                }
                loadDirectory(_uiState.value.currentDirectory, recordHistory = false)
            }
            onComplete(success)
        }
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

    fun saveScrollPosition(folderPath: String, index: Int) {
        viewModelScope.launch { dataStore.setScrollPosition(folderPath, index) }
    }
}
