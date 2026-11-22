package com.example.eev.feature.viewer

import com.example.eev.core.model.FileItem
import com.example.eev.core.model.SortOrder
import com.example.eev.core.storage.FileStorageManager
import java.io.File

/**
 * フォルダまたぎ移動時の解決結果を表すデータクラス。
 *
 * [責任]: 移動先フォルダとソート済み画像ファイル一覧の保持。
 * [影響する状態]: [ViewerUiState.imageFiles], [ViewerUiState.currentIndex], [ViewerUiState.anchorFilePath]。
 * [潜在的エラー]: なし。
 *
 * @property directory 移動先として確定したフォルダ。
 * @property images 移動先フォルダ内のソート済み画像ファイル一覧（1件以上）。
 */
data class DirectoryNavigationResult(
    val directory: File,
    val images: List<File>
)

/**
 * 画像ビューワにおける「次のフォルダ／前のフォルダ」の探索・選択を担うナビゲータ。
 *
 * エクスプローラ起動時は親ディレクトリ直下のサブフォルダ群を非ループで走査し、
 * 本棚（お気に入り一覧）起動時はお気に入り登録フォルダ群を [SortOrder]（`CUSTOM` 時は `bookshelf.db` のカスタム順序）でソートして循環（ループ）走査する。
 *
 * [責任]: 起動経路（通常／本棚）に応じた次・前フォルダおよび画像一覧の特定。
 * [影響する状態]: なし（純粋な探索結果を返し、呼び出し元の [ViewerViewModel] が UI State を更新する）。
 * [潜在的エラー]: 移動可能な画像入りフォルダが存在しない場合や例外発生時は null を返す。
 */
class ViewerDirectoryNavigator(
    private val storageManager: FileStorageManager
) {

    /**
     * 次の画像を含むフォルダを探索する。
     */
    suspend fun findNextDirectory(
        currentDir: File,
        sortOrder: SortOrder,
        showHiddenFiles: Boolean,
        isFromBookshelf: Boolean,
        favoritePaths: Set<String>,
        customOrderMap: Map<String, Int> = emptyMap()
    ): DirectoryNavigationResult? {
        return try {
            if (isFromBookshelf) {
                findInBookshelf(
                    currentDir = currentDir,
                    sortOrder = sortOrder,
                    showHiddenFiles = showHiddenFiles,
                    favoritePaths = favoritePaths,
                    customOrderMap = customOrderMap,
                    forward = true
                )
            } else {
                findInParentSubDirectories(
                    currentDir = currentDir,
                    sortOrder = sortOrder,
                    showHiddenFiles = showHiddenFiles,
                    forward = true
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 前の画像を含むフォルダを探索する。
     */
    suspend fun findPreviousDirectory(
        currentDir: File,
        sortOrder: SortOrder,
        showHiddenFiles: Boolean,
        isFromBookshelf: Boolean,
        favoritePaths: Set<String>,
        customOrderMap: Map<String, Int> = emptyMap()
    ): DirectoryNavigationResult? {
        return try {
            if (isFromBookshelf) {
                findInBookshelf(
                    currentDir = currentDir,
                    sortOrder = sortOrder,
                    showHiddenFiles = showHiddenFiles,
                    favoritePaths = favoritePaths,
                    customOrderMap = customOrderMap,
                    forward = false
                )
            } else {
                findInParentSubDirectories(
                    currentDir = currentDir,
                    sortOrder = sortOrder,
                    showHiddenFiles = showHiddenFiles,
                    forward = false
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun findInBookshelf(
        currentDir: File,
        sortOrder: SortOrder,
        showHiddenFiles: Boolean,
        favoritePaths: Set<String>,
        customOrderMap: Map<String, Int>,
        forward: Boolean
    ): DirectoryNavigationResult? {
        if (favoritePaths.isEmpty()) return null

        val allPaths = LinkedHashSet<String>(favoritePaths.size + 1).apply {
            addAll(favoritePaths)
            add(currentDir.absolutePath)
        }
        val candidateDirs = allPaths
            .map { File(it) }
            .filter { it.isDirectory && (showHiddenFiles || !it.name.startsWith(".")) }
        if (candidateDirs.isEmpty()) return null

        val sortedDirs = sortDirectories(candidateDirs, sortOrder, showHiddenFiles, customOrderMap)
        val size = sortedDirs.size
        if (size == 0) return null

        val currentIndex = sortedDirs.indexOfFirst { it.absolutePath == currentDir.absolutePath }
        val startOffset = if (currentIndex >= 0) 1 else 0
        val baseIndex = if (currentIndex >= 0) currentIndex else 0

        for (step in startOffset..size) {
            val targetIndex = if (forward) {
                (baseIndex + step) % size
            } else {
                (baseIndex - step % size + size) % size
            }
            val candidate = sortedDirs[targetIndex]
            if (!favoritePaths.contains(candidate.absolutePath)) continue

            val images = storageManager.getImageFiles(
                directory = candidate,
                sortOrder = sortOrder,
                showHiddenFiles = showHiddenFiles
            )
            if (images.isNotEmpty()) {
                return DirectoryNavigationResult(directory = candidate, images = images)
            }
        }
        return null
    }

    private suspend fun findInParentSubDirectories(
        currentDir: File,
        sortOrder: SortOrder,
        showHiddenFiles: Boolean,
        forward: Boolean
    ): DirectoryNavigationResult? {
        val parentDir = currentDir.parentFile ?: return null
        val subDirs = storageManager.getSubDirectories(
            directory = parentDir,
            sortOrder = sortOrder,
            showHiddenFiles = showHiddenFiles
        )
        val currentIndex = subDirs.indexOfFirst { it.absolutePath == currentDir.absolutePath }
        if (currentIndex < 0) return null

        val indices = if (forward) {
            (currentIndex + 1) until subDirs.size
        } else {
            (currentIndex - 1) downTo 0
        }
        for (i in indices) {
            val candidate = subDirs[i]
            val images = storageManager.getImageFiles(
                directory = candidate,
                sortOrder = sortOrder,
                showHiddenFiles = showHiddenFiles
            )
            if (images.isNotEmpty()) {
                return DirectoryNavigationResult(directory = candidate, images = images)
            }
        }
        return null
    }

    private suspend fun sortDirectories(
        directories: List<File>,
        sortOrder: SortOrder,
        showHiddenFiles: Boolean,
        customOrderMap: Map<String, Int>
    ): List<File> {
        if (sortOrder == SortOrder.CUSTOM) {
            return directories.sortedWith(
                compareBy<File> { customOrderMap[it.absolutePath] ?: Int.MAX_VALUE }
                    .thenBy { it.name.lowercase() }
            )
        }
        if (sortOrder == SortOrder.CUSTOM_DESC) {
            return directories.sortedWith(
                compareByDescending<File> { customOrderMap[it.absolutePath] ?: Int.MIN_VALUE }
                    .thenByDescending { it.name.lowercase() }
            )
        }
        val needImageCount =
            sortOrder == SortOrder.IMAGE_COUNT_ASC || sortOrder == SortOrder.IMAGE_COUNT_DESC
        val items = directories.map { dir ->
            val count = if (needImageCount) {
                storageManager.getFolderMetadata(dir, showHiddenFiles).imageCount
            } else {
                0
            }
            FileItem(
                file = dir,
                isDirectory = true,
                name = dir.name,
                lastModified = dir.lastModified(),
                imageCount = count
            )
        }
        return storageManager.sortFileItemsSeparately(
            items = items,
            folderOrder = sortOrder,
            fileOrder = sortOrder
        ).map { it.file }
    }
}
