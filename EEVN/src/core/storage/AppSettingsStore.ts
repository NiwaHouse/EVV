import AsyncStorage from '@react-native-async-storage/async-storage';
import { SortOrder, ThumbnailSize, ViewMode, parseSortOrder } from '../models/types';

/**
 * アプリ全体設定の統合スナップショット型。
 *
 * [責任]: AsyncStorage に保存される全設定値の表現。
 * [影響する状態]: エクスプローラ・本棚・ビューワ・設定の各 ViewModel。
 * [潜在的エラー]: なし。
 */
export interface AppSettingsSnapshot {
  folderSortOrder: SortOrder;
  fileSortOrder: SortOrder;
  viewerSortOrder: SortOrder;
  viewMode: ViewMode;
  thumbnailSize: ThumbnailSize;
  showItemInfo: boolean;
  showHiddenFiles: boolean;
  isMangaMode: boolean;
  favoriteFolders: string[];
}

const KEYS = {
  folderSortOrder: 'eevn_folder_sort_order',
  fileSortOrder: 'eevn_file_sort_order',
  viewerSortOrder: 'eevn_viewer_sort_order',
  viewMode: 'eevn_view_mode',
  thumbnailSize: 'eevn_thumbnail_size',
  showItemInfo: 'eevn_show_item_info',
  showHiddenFiles: 'eevn_show_hidden_files',
  isMangaMode: 'eevn_is_manga_mode',
  favoriteFolders: 'eevn_favorite_folders',
} as const;

export const DEFAULT_APP_SETTINGS: AppSettingsSnapshot = {
  folderSortOrder: SortOrder.NAME_ASC,
  fileSortOrder: SortOrder.NAME_ASC,
  viewerSortOrder: SortOrder.NAME_ASC,
  viewMode: 'LIST',
  thumbnailSize: 'MEDIUM',
  showItemInfo: false,
  showHiddenFiles: false,
  isMangaMode: false,
  favoriteFolders: [],
};

type SettingsListener = (snapshot: AppSettingsSnapshot) => void;

/**
 * AsyncStorage ベースのアプリケーション設定ストア（シングルトン）。
 *
 * 画面間で共有する設定変更（表示モード・ソート順・サムネイルサイズ・お気に入り等）を
 * 即座に全購読 ViewModel へ配信する。
 *
 * [責任]: 設定の永続化、初期読み込み、更新通知の配信。
 * [影響する状態]: AsyncStorage 永続値および全購読 ViewModel の設定状態。
 * [潜在的エラー]: AsyncStorage 読み書き・JSON パース失敗時は `try-catch` でデフォルト値へフォールバック。
 */
class AppSettingsStoreImpl {
  private cache: AppSettingsSnapshot = { ...DEFAULT_APP_SETTINGS };
  private initialized = false;
  private initPromise: Promise<AppSettingsSnapshot> | null = null;
  private listeners = new Set<SettingsListener>();

  async load(): Promise<AppSettingsSnapshot> {
    if (this.initialized) return this.cache;
    if (this.initPromise) return this.initPromise;

    this.initPromise = (async () => {
      try {
        const pairs = await AsyncStorage.multiGet(Object.values(KEYS));
        const map = new Map<string, string | null>(pairs);

        const rawFav = map.get(KEYS.favoriteFolders);
        let parsedFav: string[] = [];
        if (rawFav) {
          try {
            const arr = JSON.parse(rawFav);
            if (Array.isArray(arr)) {
              parsedFav = arr.filter((v): v is string => typeof v === 'string');
            }
          } catch {
            parsedFav = [];
          }
        }

        const rawViewMode = map.get(KEYS.viewMode);
        const viewMode: ViewMode = rawViewMode === 'GRID' ? 'GRID' : 'LIST';

        const rawThumb = map.get(KEYS.thumbnailSize);
        const thumbnailSize: ThumbnailSize =
          rawThumb === 'SMALL' || rawThumb === 'LARGE' ? rawThumb : 'MEDIUM';

        this.cache = {
          folderSortOrder: parseSortOrder(map.get(KEYS.folderSortOrder)),
          fileSortOrder: parseSortOrder(map.get(KEYS.fileSortOrder)),
          viewerSortOrder: parseSortOrder(map.get(KEYS.viewerSortOrder)),
          viewMode,
          thumbnailSize,
          showItemInfo: map.get(KEYS.showItemInfo) === 'true',
          showHiddenFiles: map.get(KEYS.showHiddenFiles) === 'true',
          isMangaMode: map.get(KEYS.isMangaMode) === 'true',
          favoriteFolders: parsedFav,
        };
      } catch (e) {
        console.warn('AppSettingsStore load error:', e);
        this.cache = { ...DEFAULT_APP_SETTINGS };
      } finally {
        this.initialized = true;
        this.initPromise = null;
      }
      this.emit();
      return this.cache;
    })();

    return this.initPromise;
  }

  getSnapshot(): AppSettingsSnapshot {
    return this.cache;
  }

  subscribe(listener: SettingsListener): () => void {
    this.listeners.add(listener);
    if (this.initialized) {
      try {
        listener(this.cache);
      } catch (e) {
        console.warn('SettingsListener notify error:', e);
      }
    }
    return () => {
      this.listeners.delete(listener);
    };
  }

  private emit(): void {
    const snap = this.cache;
    this.listeners.forEach((listener) => {
      try {
        listener(snap);
      } catch (e) {
        console.warn('SettingsListener emit error:', e);
      }
    });
  }

  private async updateScalar<K extends keyof typeof KEYS>(
    keyName: K,
    value: AppSettingsSnapshot[K],
    serialized: string
  ): Promise<void> {
    try {
      await this.load();
      this.cache = { ...this.cache, [keyName]: value };
      this.emit();
      await AsyncStorage.setItem(KEYS[keyName], serialized);
    } catch (e) {
      console.warn(`AppSettingsStore update error (${String(keyName)}):`, e);
    }
  }

  setFolderSortOrder(order: SortOrder): Promise<void> {
    return this.updateScalar('folderSortOrder', order, order);
  }

  setFileSortOrder(order: SortOrder): Promise<void> {
    return this.updateScalar('fileSortOrder', order, order);
  }

  setViewerSortOrder(order: SortOrder): Promise<void> {
    return this.updateScalar('viewerSortOrder', order, order);
  }

  setViewMode(mode: ViewMode): Promise<void> {
    return this.updateScalar('viewMode', mode, mode);
  }

  setThumbnailSize(size: ThumbnailSize): Promise<void> {
    return this.updateScalar('thumbnailSize', size, size);
  }

  setShowItemInfo(enabled: boolean): Promise<void> {
    return this.updateScalar('showItemInfo', enabled, String(enabled));
  }

  setShowHiddenFiles(enabled: boolean): Promise<void> {
    return this.updateScalar('showHiddenFiles', enabled, String(enabled));
  }

  setIsMangaMode(enabled: boolean): Promise<void> {
    return this.updateScalar('isMangaMode', enabled, String(enabled));
  }

  async setFavoriteFolders(paths: string[]): Promise<void> {
    const unique = Array.from(new Set(paths));
    return this.updateScalar('favoriteFolders', unique, JSON.stringify(unique));
  }

  async addFavoriteFolder(path: string): Promise<void> {
    await this.load();
    if (this.cache.favoriteFolders.includes(path)) return;
    await this.setFavoriteFolders([...this.cache.favoriteFolders, path]);
  }

  async removeFavoriteFolder(path: string): Promise<void> {
    await this.load();
    const next = this.cache.favoriteFolders.filter((p) => p !== path);
    await this.setFavoriteFolders(next);
  }
}

export const AppSettingsStore = new AppSettingsStoreImpl();
