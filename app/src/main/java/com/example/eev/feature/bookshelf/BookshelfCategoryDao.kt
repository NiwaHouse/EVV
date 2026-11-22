package com.example.eev.feature.bookshelf

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.example.eev.core.model.BookshelfCategory
import com.example.eev.core.model.FavoriteFolder

private const val TAG = "BookshelfCategoryDao"

/**
 * [BookshelfDatabase] のカテゴリ登録・編集・削除およびフォルダ・カテゴリ多対多紐付けを行う拡張関数群。
 *
 * [責任]: `bookshelf_categories` および `bookshelf_folder_categories` テーブルへのCRUD操作。
 * [影響する状態]: 本棚の登録済みカテゴリ一覧および各フォルダの所属カテゴリ。
 * [潜在的エラー]: SQLite 例外発生時はすべて `try-catch` で捕捉し安全値へフォールバックする。
 */
@Synchronized
fun BookshelfDatabase.getAllCategories(): List<BookshelfCategory> {
    val result = mutableListOf<BookshelfCategory>()
    try {
        readableDatabase.query(
            BookshelfDatabase.TABLE_CATEGORIES,
            arrayOf(BookshelfDatabase.COL_CAT_ID, BookshelfDatabase.COL_CAT_NAME),
            null,
            null,
            null,
            null,
            "${BookshelfDatabase.COL_CAT_CREATED} ASC, ${BookshelfDatabase.COL_CAT_ID} ASC"
        ).use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(BookshelfDatabase.COL_CAT_ID)
            val nameIdx = cursor.getColumnIndexOrThrow(BookshelfDatabase.COL_CAT_NAME)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIdx)
                val name = cursor.getString(nameIdx) ?: continue
                result.add(BookshelfCategory(id = id, name = name))
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "カテゴリ一覧の取得に失敗しました", e)
    }
    return result
}

/**
 * 新規カテゴリを登録する。同名カテゴリが既に存在する場合は既存カテゴリを返す。
 *
 * [責任]: カテゴリのINSERTおよび重複防止。
 * [影響する状態]: `bookshelf_categories` テーブル。
 * [潜在的エラー]: DB書き込み失敗時は `null` を返す。
 */
@Synchronized
fun BookshelfDatabase.addCategory(rawName: String): BookshelfCategory? {
    val trimmed = rawName.trim()
    if (trimmed.isEmpty()) return null
    try {
        val existing = getAllCategories().firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
        if (existing != null) return existing

        val values = ContentValues().apply {
            put(BookshelfDatabase.COL_CAT_NAME, trimmed)
            put(BookshelfDatabase.COL_CAT_CREATED, System.currentTimeMillis())
        }
        val id = writableDatabase.insertWithOnConflict(
            BookshelfDatabase.TABLE_CATEGORIES,
            null,
            values,
            SQLiteDatabase.CONFLICT_IGNORE
        )
        if (id != -1L) {
            return BookshelfCategory(id = id, name = trimmed)
        }
    } catch (e: Exception) {
        Log.e(TAG, "カテゴリの追加に失敗しました: $trimmed", e)
    }
    return getAllCategories().firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
}

/**
 * 指定されたカテゴリの表示名を変更する。
 *
 * [責任]: カテゴリ名のUPDATE処理。
 * [影響する状態]: `bookshelf_categories` テーブル。
 * [潜在的エラー]: 同名重複やDBエラー時は `false` を返す。
 */
@Synchronized
fun BookshelfDatabase.renameCategory(categoryId: Long, newRawName: String): Boolean {
    val trimmed = newRawName.trim()
    if (trimmed.isEmpty()) return false
    return try {
        val values = ContentValues().apply {
            put(BookshelfDatabase.COL_CAT_NAME, trimmed)
        }
        val rows = writableDatabase.update(
            BookshelfDatabase.TABLE_CATEGORIES,
            values,
            "${BookshelfDatabase.COL_CAT_ID} = ?",
            arrayOf(categoryId.toString())
        )
        rows > 0
    } catch (e: Exception) {
        Log.e(TAG, "カテゴリ名の変更に失敗しました: id=$categoryId", e)
        false
    }
}

/**
 * 指定されたカテゴリを削除し、フォルダとの紐付けも一括解除する。
 *
 * [責任]: カテゴリ本体および中間テーブルからの関連行削除。
 * [影響する状態]: `bookshelf_categories` および `bookshelf_folder_categories` テーブル。
 * [潜在的エラー]: トランザクション失敗時はロールバックしログ出力する。
 */
@Synchronized
fun BookshelfDatabase.deleteCategory(categoryId: Long) {
    try {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(
                BookshelfDatabase.TABLE_FOLDER_CATEGORIES,
                "${BookshelfDatabase.COL_FC_CAT_ID} = ?",
                arrayOf(categoryId.toString())
            )
            db.delete(
                BookshelfDatabase.TABLE_CATEGORIES,
                "${BookshelfDatabase.COL_CAT_ID} = ?",
                arrayOf(categoryId.toString())
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    } catch (e: Exception) {
        Log.e(TAG, "カテゴリの削除に失敗しました: id=$categoryId", e)
    }
}

/**
 * 全フォルダの所属カテゴリID集合マップ（`folderPath -> Set<categoryId>`）を取得する。
 *
 * [責任]: 中間テーブルの一括読み取り。
 * [影響する状態]: なし（読み取りのみ）。
 * [潜在的エラー]: 読み取り失敗時は空マップを返す。
 */
@Synchronized
fun BookshelfDatabase.getFolderCategoryIdsMap(): Map<String, Set<Long>> {
    val map = mutableMapOf<String, MutableSet<Long>>()
    try {
        readableDatabase.query(
            BookshelfDatabase.TABLE_FOLDER_CATEGORIES,
            arrayOf(BookshelfDatabase.COL_FC_PATH, BookshelfDatabase.COL_FC_CAT_ID),
            null,
            null,
            null,
            null,
            null
        ).use { cursor ->
            val pathIdx = cursor.getColumnIndexOrThrow(BookshelfDatabase.COL_FC_PATH)
            val catIdx = cursor.getColumnIndexOrThrow(BookshelfDatabase.COL_FC_CAT_ID)
            while (cursor.moveToNext()) {
                val path = cursor.getString(pathIdx) ?: continue
                val catId = cursor.getLong(catIdx)
                map.getOrPut(path) { mutableSetOf() }.add(catId)
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "フォルダ別カテゴリ紐付けの取得に失敗しました", e)
    }
    return map
}

/**
 * 指定フォルダの所属カテゴリID集合を上書き保存する（複数カテゴリ対応）。
 *
 * [責任]: 対象フォルダのカテゴリ割り当て更新。
 * [影響する状態]: `bookshelf_folder_categories` テーブル。
 * [潜在的エラー]: トランザクション失敗時はロールバックする。
 */
@Synchronized
fun BookshelfDatabase.setFolderCategories(folderPath: String, categoryIds: Set<Long>) {
    try {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(
                BookshelfDatabase.TABLE_FOLDER_CATEGORIES,
                "${BookshelfDatabase.COL_FC_PATH} = ?",
                arrayOf(folderPath)
            )
            categoryIds.forEach { catId ->
                val values = ContentValues().apply {
                    put(BookshelfDatabase.COL_FC_PATH, folderPath)
                    put(BookshelfDatabase.COL_FC_CAT_ID, catId)
                }
                db.insertWithOnConflict(
                    BookshelfDatabase.TABLE_FOLDER_CATEGORIES,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_IGNORE
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    } catch (e: Exception) {
        Log.e(TAG, "フォルダのカテゴリ割り当て更新に失敗しました: $folderPath", e)
    }
}

/**
 * [FavoriteFolder] リストに最新のカテゴリID集合とカテゴリ名一覧を付与した新しいリストを返す。
 *
 * [責任]: フォルダモデルへの所属カテゴリ情報マージ。
 * [影響する状態]: なし（戻り値のみ）。
 * [潜在的エラー]: なし。
 */
fun BookshelfDatabase.attachCategories(
    folders: List<FavoriteFolder>,
    categories: List<BookshelfCategory>
): List<FavoriteFolder> {
    return try {
        val categoryMap = categories.associateBy { it.id }
        val folderCatMap = getFolderCategoryIdsMap()
        folders.map { folder ->
            val rawIds = folderCatMap[folder.file.absolutePath].orEmpty()
            val validIds = rawIds.filter { categoryMap.containsKey(it) }.toSet()
            val names = categories.filter { validIds.contains(it.id) }.map { it.name }
            folder.copy(categoryIds = validIds, categoryNames = names)
        }
    } catch (e: Exception) {
        Log.w(TAG, "カテゴリ情報のマージに失敗しました", e)
        folders
    }
}
