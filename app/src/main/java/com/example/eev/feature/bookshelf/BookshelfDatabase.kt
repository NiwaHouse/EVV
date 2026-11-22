package com.example.eev.feature.bookshelf

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.example.eev.core.model.FavoriteFolder
import java.io.File

/**
 * 本棚専用 SQLite データベース（`bookshelf.db`）の管理クラス。
 *
 * お気に入りフォルダのカスタム並び順（`custom_order`）および高速即時表示用のメタデータ
 * （先頭サムネイルパス、画像枚数、フォルダ最終更新日時、翻訳DB有無）を永続化する。
 *
 * [責任]: `bookshelf_items` テーブルの生成、即時キャッシュ取得、差分Upsert、カスタム並び順の一括更新、不要行削除。
 * [影響する状態]: アプリ内部DB `bookshelf.db`。
 * [潜在的エラー]: SQLite 例外発生時はすべて `try-catch` で捕捉し、空リストや安全値へフォールバックしてクラッシュを防止する。
 */
class BookshelfDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val TAG = "BookshelfDatabase"
        private const val DB_NAME = "bookshelf.db"
        private const val DB_VERSION = 2

        internal const val TABLE_ITEMS = "bookshelf_items"
        internal const val COL_PATH = "folder_path"
        internal const val COL_NAME = "folder_name"
        internal const val COL_ORDER = "custom_order"
        internal const val COL_THUMB = "thumb_path"
        internal const val COL_COUNT = "image_count"
        internal const val COL_MODIFIED = "last_modified"
        internal const val COL_TRANS_DB = "has_trans_db"

        internal const val TABLE_CATEGORIES = "bookshelf_categories"
        internal const val COL_CAT_ID = "id"
        internal const val COL_CAT_NAME = "name"
        internal const val COL_CAT_CREATED = "created_at"

        internal const val TABLE_FOLDER_CATEGORIES = "bookshelf_folder_categories"
        internal const val COL_FC_PATH = "folder_path"
        internal const val COL_FC_CAT_ID = "category_id"
    }

    override fun onCreate(db: SQLiteDatabase) {
        try {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_ITEMS (
                    $COL_PATH TEXT PRIMARY KEY,
                    $COL_NAME TEXT NOT NULL,
                    $COL_ORDER INTEGER NOT NULL,
                    $COL_THUMB TEXT,
                    $COL_COUNT INTEGER NOT NULL DEFAULT 0,
                    $COL_MODIFIED INTEGER NOT NULL DEFAULT 0,
                    $COL_TRANS_DB INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_CATEGORIES (
                    $COL_CAT_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COL_CAT_NAME TEXT NOT NULL UNIQUE,
                    $COL_CAT_CREATED INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_FOLDER_CATEGORIES (
                    $COL_FC_PATH TEXT NOT NULL,
                    $COL_FC_CAT_ID INTEGER NOT NULL,
                    PRIMARY KEY ($COL_FC_PATH, $COL_FC_CAT_ID)
                )
                """.trimIndent()
            )
        } catch (e: Exception) {
            Log.e(TAG, "テーブル作成に失敗しました", e)
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onCreate(db)
    }

    /**
     * DBにキャッシュされているお気に入りフォルダ一覧を `custom_order` 昇順で即座に取得する。
     *
     * [責任]: ファイルシステム走査を行わない高速初期表示データの提供。
     * [影響する状態]: なし（読み取りのみ）。
     * [潜在的エラー]: DB読み取り失敗時は空リストを返す。
     */
    @Synchronized
    fun getAllCachedFolders(): List<FavoriteFolder> {
        val list = mutableListOf<FavoriteFolder>()
        try {
            val db = readableDatabase
            db.query(
                TABLE_ITEMS,
                null,
                null,
                null,
                null,
                null,
                "$COL_ORDER ASC, $COL_NAME COLLATE NOCASE ASC"
            ).use { cursor ->
                val pathIdx = cursor.getColumnIndexOrThrow(COL_PATH)
                val nameIdx = cursor.getColumnIndexOrThrow(COL_NAME)
                val orderIdx = cursor.getColumnIndexOrThrow(COL_ORDER)
                val thumbIdx = cursor.getColumnIndexOrThrow(COL_THUMB)
                val countIdx = cursor.getColumnIndexOrThrow(COL_COUNT)
                val modIdx = cursor.getColumnIndexOrThrow(COL_MODIFIED)
                val transIdx = cursor.getColumnIndexOrThrow(COL_TRANS_DB)

                while (cursor.moveToNext()) {
                    val path = cursor.getString(pathIdx) ?: continue
                    list.add(
                        FavoriteFolder(
                            file = File(path),
                            name = cursor.getString(nameIdx) ?: File(path).name,
                            lastModified = cursor.getLong(modIdx),
                            firstImagePath = if (cursor.isNull(thumbIdx)) null else cursor.getString(thumbIdx),
                            imageCount = cursor.getInt(countIdx),
                            hasTransDb = cursor.getInt(transIdx) != 0,
                            customOrder = cursor.getInt(orderIdx)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "キャッシュ済み本棚データの取得に失敗しました", e)
        }
        return list
    }

    /**
     * フォルダ絶対パスと `custom_order` のマップを取得する。
     *
     * [責任]: ビューワやソート処理へのカスタム順序マップ提供。
     * [影響する状態]: なし（読み取りのみ）。
     * [潜在的エラー]: 読み取り失敗時は空マップを返す。
     */
    @Synchronized
    fun getCustomOrderMap(): Map<String, Int> {
        return getAllCachedFolders().associate { it.file.absolutePath to it.customOrder }
    }

    /**
     * フォルダ一覧のメタデータおよびカスタム順序を一括 Upsert する。
     *
     * [責任]: 差分同期後の最新メタデータのトランザクション保存。
     * [影響する状態]: `bookshelf_items` テーブル。
     * [潜在的エラー]: 書き込み失敗時はロールバックしログ出力する。
     */
    @Synchronized
    fun upsertFolders(folders: List<FavoriteFolder>) {
        if (folders.isEmpty()) return
        try {
            val db = writableDatabase
            db.beginTransaction()
            try {
                folders.forEach { folder ->
                    val values = ContentValues().apply {
                        put(COL_PATH, folder.file.absolutePath)
                        put(COL_NAME, folder.name)
                        put(COL_ORDER, folder.customOrder)
                        put(COL_THUMB, folder.firstImagePath)
                        put(COL_COUNT, folder.imageCount)
                        put(COL_MODIFIED, folder.lastModified)
                        put(COL_TRANS_DB, if (folder.hasTransDb) 1 else 0)
                    }
                    db.insertWithOnConflict(TABLE_ITEMS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            Log.e(TAG, "本棚データのUpsertに失敗しました", e)
        }
    }

    /**
     * ドラッグ＆ドロップ後のフォルダパス順序に従って `custom_order` を一括更新する。
     *
     * [責任]: カスタム並び順の永続保存。
     * [影響する状態]: `bookshelf_items` テーブルの `custom_order` 列。
     * [潜在的エラー]: 更新失敗時はログ出力して保護する。
     */
    @Synchronized
    fun updateCustomOrders(orderedPaths: List<String>) {
        if (orderedPaths.isEmpty()) return
        try {
            val db = writableDatabase
            db.beginTransaction()
            try {
                orderedPaths.forEachIndexed { index, path ->
                    val values = ContentValues().apply { put(COL_ORDER, index) }
                    db.update(TABLE_ITEMS, values, "$COL_PATH = ?", arrayOf(path))
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            Log.e(TAG, "カスタム並び順の更新に失敗しました", e)
        }
    }

    /**
     * 指定セットに含まれないフォルダ行をDBから削除する。
     *
     * [責任]: お気に入り解除・削除済みフォルダのDBクリーンアップ。
     * [影響する状態]: `bookshelf_items` テーブル。
     * [潜在的エラー]: 削除失敗時はログ出力して保護する。
     */
    @Synchronized
    fun retainOnly(validPaths: Set<String>) {
        try {
            val existing = getAllCachedFolders().map { it.file.absolutePath }
            val toDelete = existing.filter { !validPaths.contains(it) }
            if (toDelete.isEmpty()) return
            val db = writableDatabase
            db.beginTransaction()
            try {
                toDelete.forEach { path ->
                    db.delete(TABLE_ITEMS, "$COL_PATH = ?", arrayOf(path))
                    db.delete(TABLE_FOLDER_CATEGORIES, "$COL_FC_PATH = ?", arrayOf(path))
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            Log.w(TAG, "不要な本棚キャッシュ行の削除に失敗しました", e)
        }
    }
}
