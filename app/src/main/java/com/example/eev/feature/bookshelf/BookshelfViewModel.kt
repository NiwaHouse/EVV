package com.example.eev.feature.bookshelf

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eev.core.datastore.EEVDataStore
import com.example.eev.core.model.BookshelfCategory
import com.example.eev.core.model.FavoriteFolder
import com.example.eev.core.model.SortOrder
import com.example.eev.core.model.ThumbnailSize
import com.example.eev.core.model.ViewMode
import com.example.eev.core.storage.FileStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 本棚画面の状態管理・2段階即時キャッシュロード・カスタム並び順・カテゴリ管理を担当する ViewModel。
 *
 * [責任]: `bookshelf.db` からの即時表示、差分メタデータ同期、カテゴリCRUD・フォルダ割り当て、ドラッグ順序保存、共有設定反映。
 * [影響する状態]: [BookshelfUiState]、`bookshelf.db`、`filesDir/bookshelf_thumbs`、DataStore の共有設定値。
 * [潜在的エラー]: I/O・DB・ソート例外時はすべて `try-catch` で捕捉し、アプリのクラッシュを防止する。
 */
class BookshelfViewModel(
    private val dataStore: EEVDataStore,
    private val storageManager: FileStorageManager = FileStorageManager(),
    private val bookshelfDb: BookshelfDatabase? = null,
    private val thumbnailStore: BookshelfThumbnailStore? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookshelfUiState())
    val uiState: StateFlow<BookshelfUiState> = _uiState.asStateFlow()

    init {
        // 第0段階: 起動直後にDBキャッシュがあれば即座にメモリへ反映
        bookshelfDb?.let { db ->
            val cats = db.getAllCategories()
            val cached = db.attachCategories(db.getAllCachedFolders(), cats)
            _uiState.value = _uiState.value.copy(
                categories = cats,
                favoriteFolders = cached
            )
        }

        viewModelScope.launch {
            combine(
                dataStore.favoriteFolders,
                dataStore.showHiddenFiles
            ) { folderPaths, showHidden ->
                folderPaths to showHidden
            }.collectLatest { (folderPaths, showHidden) ->
                syncFavoriteFolders(folderPaths, showHidden)
            }
        }
        viewModelScope.launch {
            dataStore.folderSortOrder.collectLatest { sortOrder ->
                val sorted = sortFavoriteFolders(_uiState.value.favoriteFolders, sortOrder)
                _uiState.value = _uiState.value.copy(
                    folderSortOrder = sortOrder,
                    favoriteFolders = sorted
                )
            }
        }
        viewModelScope.launch {
            dataStore.fileSortOrder.collectLatest { sortOrder ->
                _uiState.value = _uiState.value.copy(fileSortOrder = sortOrder)
            }
        }
        viewModelScope.launch {
            dataStore.viewMode.collectLatest { mode ->
                _uiState.value = _uiState.value.copy(viewMode = mode)
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
    }

    /**
     * 第1段階（DBキャッシュ即時表示）と第2段階（変更フォルダのみ差分走査・DB更新）でお気に入り一覧を同期する。
     */
    private suspend fun syncFavoriteFolders(
        folderPaths: Set<String>,
        showHiddenFiles: Boolean
    ) = withContext(Dispatchers.IO) {
        try {
            val categories = bookshelfDb?.getAllCategories().orEmpty()
            val cachedByPath = bookshelfDb?.getAllCachedFolders()
                ?.let { bookshelfDb.attachCategories(it, categories) }
                ?.associateBy { it.file.absolutePath }
                .orEmpty()

            // 第1段階: DBに存在するエントリをファイルシステム走査なしで即時表示
            val instantList = folderPaths.mapNotNull { cachedByPath[it] }
            if (instantList.isNotEmpty()) {
                val sortedInstant = sortFavoriteFolders(instantList, _uiState.value.folderSortOrder)
                _uiState.value = _uiState.value.copy(
                    categories = categories,
                    favoriteFolders = sortedInstant,
                    isLoading = false
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    categories = categories,
                    isLoading = folderPaths.isNotEmpty()
                )
            }

            // 第2段階: 実在確認および未キャッシュ・更新済みフォルダのみメタデータを再取得
            val validPaths = mutableSetOf<String>()
            val resultList = mutableListOf<FavoriteFolder>()
            var nextCustomOrder = (cachedByPath.values.maxOfOrNull { it.customOrder } ?: -1) + 1

            folderPaths.forEach { path ->
                try {
                    val file = File(path)
                    if (file.exists() && file.isDirectory) {
                        validPaths.add(path)
                        val currentModified = file.lastModified()
                        val cached = cachedByPath[path]
                        val isThumbReady = cached?.firstImagePath?.let { File(it).isFile } == true

                        if (cached != null && cached.lastModified == currentModified && isThumbReady) {
                            resultList.add(cached)
                        } else {
                            val metadata = storageManager.getFolderMetadata(file, showHiddenFiles)
                            val thumbPath = metadata.firstImagePath?.let { src ->
                                thumbnailStore?.getOrCreateBookshelfThumbnail(path, src) ?: src
                            }
                            val order = cached?.customOrder ?: nextCustomOrder++
                            resultList.add(
                                FavoriteFolder(
                                    file = file,
                                    name = file.name,
                                    lastModified = currentModified,
                                    firstImagePath = thumbPath,
                                    imageCount = metadata.imageCount,
                                    hasTransDb = metadata.hasTransDb,
                                    customOrder = order
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.w("BookshelfViewModel", "フォルダ情報の差分取得失敗: $path", e)
                }
            }

            if (validPaths.size != folderPaths.size) {
                dataStore.setFavoriteFolders(validPaths)
            }
            bookshelfDb?.retainOnly(validPaths)
            bookshelfDb?.upsertFolders(resultList)
            thumbnailStore?.retainOnly(validPaths)

            val latestCategories = bookshelfDb?.getAllCategories().orEmpty()
            val withCategories = bookshelfDb?.attachCategories(resultList, latestCategories) ?: resultList
            val sorted = sortFavoriteFolders(withCategories, _uiState.value.folderSortOrder)
            _uiState.value = _uiState.value.copy(
                categories = latestCategories,
                favoriteFolders = sorted,
                isLoading = false
            )
        } catch (e: Exception) {
            Log.e("BookshelfViewModel", "お気に入りフォルダ同期失敗", e)
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    /**
     * ヘッダーのカテゴリ絞り込み対象をトグルする（複数選択AND検索対応）。
     * - `null`（「すべて」）: 全選択解除
     * - [UNCATEGORIZED_CATEGORY_ID]（「未分類」）: 排他選択トグル
     * - 通常カテゴリID: 「未分類」を解除し、対象IDを集合に追加／除外トグル
     */
    fun toggleCategoryFilter(categoryId: Long?) {
        val current = _uiState.value.selectedCategoryIds
        val next = when (categoryId) {
            null -> emptySet()
            UNCATEGORIZED_CATEGORY_ID -> {
                if (current.contains(UNCATEGORIZED_CATEGORY_ID)) emptySet()
                else setOf(UNCATEGORIZED_CATEGORY_ID)
            }
            else -> {
                val base = current - UNCATEGORIZED_CATEGORY_ID
                if (base.contains(categoryId)) base - categoryId else base + categoryId
            }
        }
        _uiState.value = _uiState.value.copy(selectedCategoryIds = next)
    }

    /**
     * 新規カテゴリを追加し、最新のカテゴリ一覧で状態を更新する。
     */
    fun addCategory(name: String, onCreated: (BookshelfCategory?) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val created = bookshelfDb?.addCategory(name)
                refreshCategoryAssignmentsInMemory()
                withContext(Dispatchers.Main) { onCreated(created) }
            } catch (e: Exception) {
                Log.e("BookshelfViewModel", "カテゴリ追加失敗", e)
            }
        }
    }

    /**
     * カテゴリ名を変更し、最新のカテゴリ一覧で状態を更新する。
     */
    fun renameCategory(categoryId: Long, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bookshelfDb?.renameCategory(categoryId, newName)
                refreshCategoryAssignmentsInMemory()
            } catch (e: Exception) {
                Log.e("BookshelfViewModel", "カテゴリ名変更失敗", e)
            }
        }
    }

    /**
     * カテゴリを削除し、選択集合からも除外する。
     */
    fun deleteCategory(categoryId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bookshelfDb?.deleteCategory(categoryId)
                val nextSelected = _uiState.value.selectedCategoryIds - categoryId
                _uiState.value = _uiState.value.copy(selectedCategoryIds = nextSelected)
                refreshCategoryAssignmentsInMemory()
            } catch (e: Exception) {
                Log.e("BookshelfViewModel", "カテゴリ削除失敗", e)
            }
        }
    }

    /**
     * 特定フォルダに割り当てる複数カテゴリID集合を保存して即時反映する。
     */
    fun updateFolderCategories(folderPath: String, categoryIds: Set<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bookshelfDb?.setFolderCategories(folderPath, categoryIds)
                refreshCategoryAssignmentsInMemory()
            } catch (e: Exception) {
                Log.e("BookshelfViewModel", "フォルダカテゴリ更新失敗", e)
            }
        }
    }

    private fun refreshCategoryAssignmentsInMemory() {
        val db = bookshelfDb ?: return
        val cats = db.getAllCategories()
        val updatedFolders = db.attachCategories(_uiState.value.favoriteFolders, cats)
        _uiState.value = _uiState.value.copy(
            categories = cats,
            favoriteFolders = updatedFolders
        )
    }

    /**
     * ドラッグ＆ドロップ中に2つのフォルダ位置をメモリ上で入れ替える。
     */
    fun moveFolderInMemory(fromPath: String, toPath: String) {
        try {
            if (fromPath == toPath) return
            val current = _uiState.value.favoriteFolders.toMutableList()
            val fromIdx = current.indexOfFirst { it.file.absolutePath == fromPath }
            val toIdx = current.indexOfFirst { it.file.absolutePath == toPath }
            if (fromIdx < 0 || toIdx < 0) return

            val moved = current.removeAt(fromIdx)
            current.add(toIdx, moved)
            val reindexed = current.mapIndexed { idx, item -> item.copy(customOrder = idx) }
            _uiState.value = _uiState.value.copy(
                favoriteFolders = reindexed,
                folderSortOrder = SortOrder.CUSTOM
            )
        } catch (e: Exception) {
            Log.w("BookshelfViewModel", "ドラッグ移動失敗", e)
        }
    }

    /**
     * ドラッグ＆ドロップ完了時に現在の並び順を `bookshelf.db` に永続化し、ソート順を `CUSTOM` に設定する。
     */
    fun commitCustomOrder() {
        val currentList = _uiState.value.favoriteFolders
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bookshelfDb?.updateCustomOrders(currentList.map { it.file.absolutePath })
                dataStore.setFolderSortOrder(SortOrder.CUSTOM)
            } catch (e: Exception) {
                Log.e("BookshelfViewModel", "カスタム順序の保存に失敗しました", e)
            }
        }
    }

    private fun sortFavoriteFolders(
        folders: List<FavoriteFolder>,
        sortOrder: SortOrder
    ): List<FavoriteFolder> {
        return try {
            when (sortOrder) {
                SortOrder.CUSTOM -> folders.sortedWith(
                    compareBy<FavoriteFolder> { it.customOrder }.thenBy { it.name.lowercase() }
                )
                SortOrder.CUSTOM_DESC -> folders.sortedWith(
                    compareByDescending<FavoriteFolder> { it.customOrder }
                        .thenByDescending { it.name.lowercase() }
                )
                else -> {
                    val byPath = folders.associateBy { it.file.absolutePath }
                    storageManager.sortFileItemsSeparately(
                        items = folders.map { it.toFileItem() },
                        folderOrder = sortOrder,
                        fileOrder = sortOrder
                    ).mapNotNull { byPath[it.file.absolutePath] }
                }
            }
        } catch (_: Exception) {
            folders
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun toggleSearchActive(active: Boolean) {
        _uiState.value = _uiState.value.copy(
            isSearchActive = active,
            searchQuery = if (active) _uiState.value.searchQuery else ""
        )
    }

    fun updateFolderSortOrder(sortOrder: SortOrder) {
        viewModelScope.launch { try { dataStore.setFolderSortOrder(sortOrder) } catch (_: Exception) {} }
    }

    fun updateFileSortOrder(sortOrder: SortOrder) {
        viewModelScope.launch { try { dataStore.setFileSortOrder(sortOrder) } catch (_: Exception) {} }
    }

    fun updateViewMode(viewMode: ViewMode) {
        viewModelScope.launch { try { dataStore.setViewMode(viewMode) } catch (_: Exception) {} }
    }

    fun updateThumbnailSize(size: ThumbnailSize) {
        viewModelScope.launch { try { dataStore.setThumbnailSize(size) } catch (_: Exception) {} }
    }

    fun updateShowItemInfo(enabled: Boolean) {
        viewModelScope.launch { try { dataStore.setShowItemInfo(enabled) } catch (_: Exception) {} }
    }

    fun removeFavorite(folderPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                dataStore.removeFavoriteFolder(folderPath)
                thumbnailStore?.deleteThumbnailForFolder(folderPath)
            } catch (_: Exception) {}
        }
    }
}
