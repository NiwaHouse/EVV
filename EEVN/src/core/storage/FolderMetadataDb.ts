import * as SQLite from 'expo-sqlite';

/**
 * 通常フォルダ用の永続メタデータキャッシュレコード。
 *
 * [責任]: `folder_cache.db` に保存するフォルダメタ情報の型定義。
 * [影響する状態]: エクスプローラ一覧の即時サムネイル・枚数・翻訳DBバッジ表示。
 * [潜在的エラー]: なし。
 */
export interface CachedFolderMetadata {
  folderPath: string;
  lastModified: number;
  imageCount: number;
  thumbPath: string | null;
  hasTransDb: boolean;
  hasSubDirs: boolean;
}

interface RawFolderRow {
  folder_path: string;
  last_modified: number;
  image_count: number;
  thumb_path: string | null;
  has_trans_db: number;
  has_sub_dirs: number;
}

/**
 * 通常フォルダ用永続メタデータキャッシュ DB（`folder_cache.db`）管理クラス。
 *
 * OS に消去されない `filesDir/folder_thumbs` へのパスとフォルダ統計情報を保存し、
 * エクスプローラ画面の 2 段階即時表示（キャッシュ即描画 → 差分更新）を実現する。
 *
 * [責任]: `folder_cache_items` テーブル初期化、複数パス一括参照、Upsert、不要行削除、全消去。
 * [影響する状態]: SQLite DB `folder_cache.db`。
 * [潜在的エラー]: SQLite 例外発生時は `try-catch` で捕捉し、空マップ／安全状態へフォールバックする。
 */
class FolderMetadataDbImpl {
  private db: SQLite.SQLiteDatabase | null = null;
  private initPromise: Promise<SQLite.SQLiteDatabase | null> | null = null;
  private writeQueue: Promise<void> = Promise.resolve();

  private async ensureDb(): Promise<SQLite.SQLiteDatabase | null> {
    if (this.db) return this.db;
    if (this.initPromise) return this.initPromise;

    this.initPromise = (async () => {
      try {
        const database = await SQLite.openDatabaseAsync('folder_cache.db');
        await database.execAsync(`
          CREATE TABLE IF NOT EXISTS folder_cache_items (
            folder_path TEXT PRIMARY KEY,
            last_modified INTEGER NOT NULL DEFAULT 0,
            image_count INTEGER NOT NULL DEFAULT 0,
            thumb_path TEXT,
            has_trans_db INTEGER NOT NULL DEFAULT 0,
            has_sub_dirs INTEGER NOT NULL DEFAULT 0
          );
        `);
        this.db = database;
        return database;
      } catch (e) {
        console.warn('FolderMetadataDb init error:', e);
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

  /**
   * 指定フォルダパス配列に一致するキャッシュ済みメタデータをマップ形式で取得する。
   */
  async getByPaths(paths: string[]): Promise<Map<string, CachedFolderMetadata>> {
    const result = new Map<string, CachedFolderMetadata>();
    if (paths.length === 0) return result;
    try {
      const db = await this.ensureDb();
      if (!db) return result;

      // SQLite パラメータ上限を考慮して 200 件ごとに分割取得
      const chunkSize = 200;
      for (let i = 0; i < paths.length; i += chunkSize) {
        const slice = paths.slice(i, i + chunkSize);
        const placeholders = slice.map(() => '?').join(',');
        const rows = await db.getAllAsync<RawFolderRow>(
          `SELECT folder_path, last_modified, image_count, thumb_path, has_trans_db, has_sub_dirs
           FROM folder_cache_items WHERE folder_path IN (${placeholders})`,
          slice
        );
        for (const row of rows) {
          result.set(row.folder_path, {
            folderPath: row.folder_path,
            lastModified: row.last_modified,
            imageCount: row.image_count,
            thumbPath: row.thumb_path,
            hasTransDb: row.has_trans_db !== 0,
            hasSubDirs: row.has_sub_dirs !== 0,
          });
        }
      }
    } catch (e) {
      console.warn('FolderMetadataDb getByPaths error:', e);
    }
    return result;
  }

  /**
   * 複数フォルダのメタデータをトランザクションで一括 Upsert する。
   */
  async upsertBatch(entries: CachedFolderMetadata[]): Promise<void> {
    if (entries.length === 0) return;
    await this.enqueueWrite(async () => {
      try {
        const db = await this.ensureDb();
        if (!db) return;
        await db.withExclusiveTransactionAsync(async (txn) => {
          for (const item of entries) {
            await txn.runAsync(
              `INSERT OR REPLACE INTO folder_cache_items
               (folder_path, last_modified, image_count, thumb_path, has_trans_db, has_sub_dirs)
               VALUES (?, ?, ?, ?, ?, ?)`,
              [
                item.folderPath,
                Math.trunc(item.lastModified),
                item.imageCount,
                item.thumbPath,
                item.hasTransDb ? 1 : 0,
                item.hasSubDirs ? 1 : 0,
              ]
            );
          }
        });
      } catch (e) {
        console.warn('FolderMetadataDb upsertBatch error:', e);
      }
    });
  }

  /**
   * 削除されたフォルダパスのキャッシュ行を DB から削除する。
   */
  async deleteByPaths(paths: string[]): Promise<void> {
    if (paths.length === 0) return;
    await this.enqueueWrite(async () => {
      try {
        const db = await this.ensureDb();
        if (!db) return;
        await db.withExclusiveTransactionAsync(async (txn) => {
          for (const path of paths) {
            await txn.runAsync('DELETE FROM folder_cache_items WHERE folder_path = ?', [path]);
          }
        });
      } catch (e) {
        console.warn('FolderMetadataDb deleteByPaths error:', e);
      }
    });
  }

  /**
   * 通常フォルダのメタデータキャッシュを全消去する（設定画面のキャッシュクリア用）。
   */
  async clearAll(): Promise<void> {
    await this.enqueueWrite(async () => {
      try {
        const db = await this.ensureDb();
        if (!db) return;
        await db.execAsync('DELETE FROM folder_cache_items');
      } catch (e) {
        console.warn('FolderMetadataDb clearAll error:', e);
      }
    });
  }
}

export const FolderMetadataDb = new FolderMetadataDbImpl();
