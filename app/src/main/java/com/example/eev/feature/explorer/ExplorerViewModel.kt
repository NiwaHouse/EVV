package com.example.eev.feature.explorer

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eev.core.datastore.EEVDataStore
import com.example.eev.core.model.FileItem
import com.example.eev.core.model.SortOrder
import com.example.eev.core.model.ViewMode
import com.example.eev.core.storage.FileStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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
    private val dataStore: EEVDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExplorerUiState())
    val uiState: StateFlow<ExplorerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            dataStore.sortOrder.collectLatest { order ->
                _uiState.value = _uiState.value.copy(sortOrder = order)
                loadDirectory(_uiState.value.currentDirectory)
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
                _uiState.value = _uiState.value.copy(showHiddenFiles = show)
                loadDirectory(_uiState.value.currentDirectory)
            }
        }
    }

    fun loadDirectory(directory: File) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, searchQuery = "")
            
            // 1. まず高速にファイル一覧を取得し画面に初期描画（即座に反応）
            val quickItems = storageManager.getQuickFilesAndFolders(
                directory = directory,
                sortOrder = _uiState.value.sortOrder,
                showHiddenFiles = _uiState.value.showHiddenFiles
            )
            _uiState.value = _uiState.value.copy(
                currentDirectory = directory,
                fileItems = quickItems,
                isLoading = false
            )

            // 2. フォルダのサムネイル・画像数を非同期並列で取得して読み込めた順に差分更新
            loadFolderMetadataAsync(quickItems)
        }
    }

    private fun loadFolderMetadataAsync(items: List<FileItem>) {
        viewModelScope.launch(Dispatchers.IO) {
            items.forEachIndexed { _, item ->
                if (item.isDirectory) {
                    val (count, firstPath) = storageManager.getFolderMetadata(
                        folder = item.file,
                        showHiddenFiles = _uiState.value.showHiddenFiles
                    )
                    val currentItems = _uiState.value.fileItems.toMutableList()
                    val targetIndex = currentItems.indexOfFirst { it.file.absolutePath == item.file.absolutePath }
                    if (targetIndex != -1) {
                        currentItems[targetIndex] = currentItems[targetIndex].copy(
                            imageCount = count,
                            firstImagePath = firstPath
                        )
                        _uiState.value = _uiState.value.copy(fileItems = currentItems)
                    }
                }
            }
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
}
