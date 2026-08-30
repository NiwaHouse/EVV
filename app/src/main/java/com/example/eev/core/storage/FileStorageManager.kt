package com.example.eev.core.storage

import com.example.eev.core.model.FileItem
import com.example.eev.core.model.SortOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ファイルシステムおよびストレージにアクセスし、ファイル・フォルダ情報を提供するマネージャ。
 *
 * [責任]: 指定ディレクトリのファイル一覧取得、画像判定、画像数カウント、並び替えソート。
 * [影響する状態]: エクスプローラ画面のリスト表示および画像ビューワの閲覧候補画像リスト。
 * [潜在的エラー]: パーミッション不足によるSecurityException、非存在パス指定によるNullPointerException。
 */
class FileStorageManager {

    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")

    /**
     * 指定された拡張子が画像ファイルかどうかを判定する。
     */
    fun isImageFile(file: File): Boolean {
        if (!file.isFile) return false
        val ext = file.extension.lowercase()
        return imageExtensions.contains(ext)
    }

    /**
     * 指定ディレクトリ内のファイル・フォルダ基本一覧を高速取得する（サムネイル探索は行わず即座に返す）。
     */
    suspend fun getQuickFilesAndFolders(
        directory: File,
        sortOrder: SortOrder,
        showHiddenFiles: Boolean = false
    ): List<FileItem> = withContext(Dispatchers.IO) {
        if (!directory.exists() || !directory.isDirectory) return@withContext emptyList()

        val listFiles = (directory.listFiles() ?: emptyArray()).filter { file ->
            if (!showHiddenFiles && (file.isHidden || file.name.startsWith("."))) {
                false
            } else true
        }

        val items = listFiles.map { file ->
            FileItem(
                file = file,
                isDirectory = file.isDirectory
            )
        }

        sortFileItems(items, sortOrder)
    }

    /**
     * 指定されたフォルダの「画像数」および「最初の画像パス」を非同期に取得する。
     */
    suspend fun getFolderMetadata(
        folder: File,
        showHiddenFiles: Boolean = false
    ): Pair<Int, String?> = withContext(Dispatchers.IO) {
        if (!folder.isDirectory) return@withContext Pair(0, null)
        val imageFiles = (folder.listFiles { f -> isImageFile(f) } ?: emptyArray()).filter { file ->
            if (!showHiddenFiles && (file.isHidden || file.name.startsWith("."))) {
                false
            } else true
        }
        val firstPath = imageFiles.minByOrNull { it.name }?.absolutePath
        Pair(imageFiles.size, firstPath)
    }

    /**
     * 指定ディレクトリ内の全画像ファイルを指定ソート順で取得する（ビューワ用）。
     */
    suspend fun getImageFiles(
        directory: File,
        sortOrder: SortOrder,
        showHiddenFiles: Boolean = false
    ): List<File> = withContext(Dispatchers.IO) {
        if (!directory.exists() || !directory.isDirectory) return@withContext emptyList()

        val imageFiles = (directory.listFiles() ?: emptyArray()).filter { file ->
            if (!showHiddenFiles && (file.isHidden || file.name.startsWith("."))) {
                false
            } else isImageFile(file)
        }
        val items = imageFiles.map { FileItem(file = it, isDirectory = false) }
        sortFileItems(items, sortOrder).map { it.file }
    }

    /**
     * 指定ディレクトリ内のサブフォルダ群を指定ソート順で取得する（次のフォルダ探査用）。
     */
    suspend fun getSubDirectories(
        directory: File,
        sortOrder: SortOrder,
        showHiddenFiles: Boolean = false
    ): List<File> = withContext(Dispatchers.IO) {
        if (!directory.exists() || !directory.isDirectory) return@withContext emptyList()

        val subDirs = (directory.listFiles() ?: emptyArray()).filter { file ->
            if (!showHiddenFiles && (file.isHidden || file.name.startsWith("."))) {
                false
            } else file.isDirectory
        }
        val items = subDirs.map { FileItem(file = it, isDirectory = true) }
        sortFileItems(items, sortOrder).map { it.file }
    }

    /**
     * 指定されたソート順に従って FileItem リストをソートする。
     */
    private fun sortFileItems(items: List<FileItem>, sortOrder: SortOrder): List<FileItem> {
        val (folders, files) = items.partition { it.isDirectory }

        val sortedFolders = when (sortOrder) {
            SortOrder.NAME_ASC -> folders.sortedBy { it.name.lowercase() }
            SortOrder.NAME_DESC -> folders.sortedByDescending { it.name.lowercase() }
            SortOrder.DATE_DESC -> folders.sortedByDescending { it.lastModified }
            SortOrder.DATE_ASC -> folders.sortedBy { it.lastModified }
            SortOrder.IMAGE_COUNT_DESC -> folders.sortedByDescending { it.imageCount }
            SortOrder.IMAGE_COUNT_ASC -> folders.sortedBy { it.imageCount }
        }

        val sortedFiles = when (sortOrder) {
            SortOrder.NAME_ASC -> files.sortedBy { it.name.lowercase() }
            SortOrder.NAME_DESC -> files.sortedByDescending { it.name.lowercase() }
            SortOrder.DATE_DESC -> files.sortedByDescending { it.lastModified }
            SortOrder.DATE_ASC -> files.sortedBy { it.lastModified }
            SortOrder.IMAGE_COUNT_DESC -> files.sortedByDescending { it.imageCount }
            SortOrder.IMAGE_COUNT_ASC -> files.sortedBy { it.imageCount }
        }

        return sortedFolders + sortedFiles
    }
}
