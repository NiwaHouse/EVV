import * as SQLite from 'expo-sqlite';
import { BookshelfCategory, FavoriteFolder } from '../../core/models/types';

interface RawItemRow {
  folder_path: string;
  folder_name: string;
  custom_order: number;
  thumb_path: string | null;
  image_count: number;
  last_modified: number;
  has_trans_db: number;
}

interface RawCategoryRow {
  id: number;
  name: string;
}

interface RawFolderCategoryRow {
  folder_path: string;
  category_id: number;
}

/**
 * 本棚専用 SQLite データベース（`bookshelf.db`）管理クラス。
 *
 * お気に入りフォルダのメタデータ、永続サムネイルパス、カスタム並び順（`custom_order`）、
 * カテゴリ定義およびフォルダ・カテゴリ多対多紐付けを保持する。
 *
 * [責任]: `bookshelf_items` / `bookshelf_categories` / `bookshelf_folder_categories` の CRUD とトランザクション永続化。
 * [影響する状態]: SQLite DB `bookshelf.db`。
 * [潜在的エラー]: SQLite 例外時はすべて `try-catch` で捕捉し安全値（空配列・false・null）へフォールバックする。
 */
class BookshelfDbImpl {
  private db: SQLite.SQLiteDatabase | null = null;
  private initPromise: Promise<SQLite.SQLiteDatabase | null> | null = null;
  private writeQueue: Promise<void> = Promise.resolve();

  private async ensureDb(): Promise<SQLite.SQLiteDatabase | null> {
    if (this.db) return this.db;
    if (this.initPromise) return this.initPromise;

    this.initPromise = (async () => {
      try {
        const database = await SQLite.openDatabaseAsync('bookshelf.db');
        await database.execAsync(`
          CREATE TABLE IF NOT EXISTS bookshelf_items (
            folder_path TEXT PRIMARY KEY,
            folder_name TEXT NOT NULL,
            custom_order INTEGER NOT NULL DEFAULT 2147483647,
            thumb_path TEXT,
            image_count INTEGER NOT NULL DEFAULT 0,
            last_modified INTEGER NOT NULL DEFAULT 0,
            has_trans_db INTEGER NOT NULL DEFAULT 0
          );
          CREATE TABLE IF NOT EXISTS bookshelf_categories (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL UNIQUE,
            created_at INTEGER NOT NULL DEFAULT 0
          );
          CREATE TABLE IF NOT EXISTS bookshelf_folder_categories (
            folder_path TEXT NOT NULL,
            category_id INTEGER NOT NULL,
            PRIMARY KEY (folder_path, category_id)
          );
        `);
        this.db = database;
        return database;
      } catch (e) {
        console.warn('BookshelfDb init error:', e);
        return null;
      } finally {
        this.initPromise = null;
      }
    })();

    return this.initPromise;
  }

  private enqueueWrite(task: () => Promise<void>): Promise<void> {
    const run = this.writeQueue.then(task, task);
    this.writeQueue = run.catch(() => {});
    return run;
  }

  async getAllCachedFolders(): Promise<FavoriteFolder[]> {
    try {
      const db = await this.ensureDb();
      if (!db) return [];
      const rows = await db.getAllAsync<RawItemRow>(
        `SELECT folder_path, folder_name, custom_order, thumb_path, image_count, last_modified, has_trans_db
         FROM bookshelf_items
         ORDER BY custom_order ASC, folder_name COLLATE NOCASE ASC`
      );
      return rows.map((row) => ({
        path: row.folder_path,
        name: row.folder_name,
        lastModified: row.last_modified,
        thumbPath: row.thumb_path,
        imageCount: row.image_count,
        hasTransDb: row.has_trans_db !== 0,
        customOrder: row.custom_order,
        categoryIds: [],
        categoryNames: [],
      }));
    } catch (e) {
      console.warn('BookshelfDb getAllCachedFolders error:', e);
      return [];
    }
  }

  async getCustomOrderMap(): Promise<Record<string, number>> {
    try {
      const folders = await this.getAllCachedFolders();
      const map: Record<string, number> = {};
      for (const f of folders) {
        map[f.path] = f.customOrder;
      }
      return map;
    } catch (e) {
      console.warn('BookshelfDb getCustomOrderMap error:', e);
      return {};
    }
  }

  async upsertFolders(folders: FavoriteFolder[]): Promise<void> {
    if (folders.length === 0) return;
    await this.enqueueWrite(async () => {
      try {
        const db = await this.ensureDb();
        if (!db) return;
        await db.withExclusiveTransactionAsync(async (txn) => {
          for (const folder of folders) {
            await txn.runAsync(
              `INSERT OR REPLACE INTO bookshelf_items
               (folder_path, folder_name, custom_order, thumb_path, image_count, last_modified, has_trans_db)
               VALUES (?, ?, ?, ?, ?, ?, ?)`,
              [
                folder.path,
                folder.name,
                folder.customOrder,
                folder.thumbPath,
                folder.imageCount,
                Math.trunc(folder.lastModified),
                folder.hasTransDb ? 1 : 0,
              ]
            );
          }
        });
      } catch (e) {
        console.warn('BookshelfDb upsertFolders error:', e);
      }
    });
  }

  async updateCustomOrders(orderedPaths: string[]): Promise<void> {
    if (orderedPaths.length === 0) return;
    await this.enqueueWrite(async () => {
      try {
        const db = await this.ensureDb();
        if (!db) return;
        await db.withExclusiveTransactionAsync(async (txn) => {
          for (let i = 0; i < orderedPaths.length; i++) {
            await txn.runAsync(
              'UPDATE bookshelf_items SET custom_order = ? WHERE folder_path = ?',
              [i, orderedPaths[i]]
            );
          }
        });
      } catch (e) {
        console.warn('BookshelfDb updateCustomOrders error:', e);
      }
    });
  }

  async retainOnly(validPaths: string[]): Promise<void> {
    await this.enqueueWrite(async () => {
      try {
        const db = await this.ensureDb();
        if (!db) return;
        const validSet = new Set(validPaths);
        const existing = await db.getAllAsync<{ folder_path: string }>(
          'SELECT folder_path FROM bookshelf_items'
        );
        const toDelete = existing.map((r) => r.folder_path).filter((p) => !validSet.has(p));
        if (toDelete.length === 0) return;

        await db.withExclusiveTransactionAsync(async (txn) => {
          for (const p of toDelete) {
            await txn.runAsync('DELETE FROM bookshelf_items WHERE folder_path = ?', [p]);
            await txn.runAsync('DELETE FROM bookshelf_folder_categories WHERE folder_path = ?', [p]);
          }
        });
      } catch (e) {
        console.warn('BookshelfDb retainOnly error:', e);
      }
    });
  }

  async getAllCategories(): Promise<BookshelfCategory[]> {
    try {
      const db = await this.ensureDb();
      if (!db) return [];
      const rows = await db.getAllAsync<RawCategoryRow>(
        'SELECT id, name FROM bookshelf_categories ORDER BY created_at ASC, id ASC'
      );
      return rows.map((r) => ({ id: r.id, name: r.name }));
    } catch (e) {
      console.warn('BookshelfDb getAllCategories error:', e);
      return [];
    }
  }

  async addCategory(rawName: string): Promise<BookshelfCategory | null> {
    const trimmed = rawName.trim();
    if (!trimmed) return null;
    let created: BookshelfCategory | null = null;
    await this.enqueueWrite(async () => {
      try {
        const existingList = await this.getAllCategories();
        const duplicate = existingList.find(
          (c) => c.name.toLowerCase() === trimmed.toLowerCase()
        );
        if (duplicate) {
          created = duplicate;
          return;
        }
        const db = await this.ensureDb();
        if (!db) return;
        const res = await db.runAsync(
          'INSERT OR IGNORE INTO bookshelf_categories (name, created_at) VALUES (?, ?)',
          [trimmed, Date.now()]
        );
        if (res.lastInsertRowId > 0) {
          created = { id: res.lastInsertRowId, name: trimmed };
        } else {
          const refreshed = await this.getAllCategories();
          created = refreshed.find((c) => c.name.toLowerCase() === trimmed.toLowerCase()) ?? null;
        }
      } catch (e) {
        console.warn('BookshelfDb addCategory error:', e);
      }
    });
    return created;
  }

  async renameCategory(categoryId: number, newRawName: string): Promise<boolean> {
    const trimmed = newRawName.trim();
    if (!trimmed) return false;
    let updated = false;
    await this.enqueueWrite(async () => {
      try {
        const db = await this.ensureDb();
        if (!db) return;
        const res = await db.runAsync(
          'UPDATE bookshelf_categories SET name = ? WHERE id = ?',
          [trimmed, categoryId]
        );
        updated = res.changes > 0;
      } catch (e) {
        console.warn('BookshelfDb renameCategory error:', e);
      }
    });
    return updated;
  }

  async deleteCategory(categoryId: number): Promise<void> {
    await this.enqueueWrite(async () => {
      try {
        const db = await this.ensureDb();
        if (!db) return;
        await db.withExclusiveTransactionAsync(async (txn) => {
          await txn.runAsync('DELETE FROM bookshelf_folder_categories WHERE category_id = ?', [
            categoryId,
          ]);
          await txn.runAsync('DELETE FROM bookshelf_categories WHERE id = ?', [categoryId]);
        });
      } catch (e) {
        console.warn('BookshelfDb deleteCategory error:', e);
      }
    });
  }

  async getFolderCategoryIdsMap(): Promise<Map<string, number[]>> {
    const map = new Map<string, number[]>();
    try {
      const db = await this.ensureDb();
      if (!db) return map;
      const rows = await db.getAllAsync<RawFolderCategoryRow>(
        'SELECT folder_path, category_id FROM bookshelf_folder_categories'
      );
      for (const row of rows) {
        const list = map.get(row.folder_path);
        if (list) {
          list.push(row.category_id);
        } else {
          map.set(row.folder_path, [row.category_id]);
        }
      }
    } catch (e) {
      console.warn('BookshelfDb getFolderCategoryIdsMap error:', e);
    }
    return map;
  }

  async setFolderCategories(folderPath: string, categoryIds: number[]): Promise<void> {
    await this.enqueueWrite(async () => {
      try {
        const db = await this.ensureDb();
        if (!db) return;
        const unique = Array.from(new Set(categoryIds));
        await db.withExclusiveTransactionAsync(async (txn) => {
          await txn.runAsync('DELETE FROM bookshelf_folder_categories WHERE folder_path = ?', [
            folderPath,
          ]);
          for (const catId of unique) {
            await txn.runAsync(
              'INSERT OR IGNORE INTO bookshelf_folder_categories (folder_path, category_id) VALUES (?, ?)',
              [folderPath, catId]
            );
          }
        });
      } catch (e) {
        console.warn('BookshelfDb setFolderCategories error:', e);
      }
    });
  }

  async attachCategories(
    folders: FavoriteFolder[],
    categories: BookshelfCategory[]
  ): Promise<FavoriteFolder[]> {
    try {
      const categoryMap = new Map<number, BookshelfCategory>(categories.map((c) => [c.id, c]));
      const folderCatMap = await this.getFolderCategoryIdsMap();
      return folders.map((folder) => {
        const rawIds = folderCatMap.get(folder.path) ?? [];
        const validSet = new Set(rawIds.filter((id) => categoryMap.has(id)));
        const validCategories = categories.filter((c) => validSet.has(c.id));
        return {
          ...folder,
          categoryIds: validCategories.map((c) => c.id),
          categoryNames: validCategories.map((c) => c.name),
        };
      });
    } catch (e) {
      console.warn('BookshelfDb attachCategories error:', e);
      return folders;
    }
  }
}

export const BookshelfDb = new BookshelfDbImpl();
