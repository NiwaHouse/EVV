import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { EevStorage } from '../../../modules/eev-storage';
import {
  BookshelfCategory,
  FavoriteFolder,
  SortOrder,
  ThumbnailSize,
  ViewMode,
} from '../../core/models/types';
import { AppSettingsStore, DEFAULT_APP_SETTINGS } from '../../core/storage/AppSettingsStore';
import { sortFavoriteFolders } from '../../core/storage/SortUtils';
import { BookshelfDb } from './BookshelfDb';

export const UNCATEGORIZED_CATEGORY_ID = -1;

/**
 * 本棚画面の UI State 型定義。
 */
export interface BookshelfUiState {
  favoriteFolders: FavoriteFolder[];
  categories: BookshelfCategory[];
  selectedCategoryIds: number[];
  folderSortOrder: SortOrder;
  fileSortOrder: SortOrder;
  viewMode: ViewMode;
  thumbnailSize: ThumbnailSize;
  showItemInfo: boolean;
  searchQuery: string;
  isSearchActive: boolean;
  isLoading: boolean;
}

function getFolderName(path: string): string {
  const idx = path.lastIndexOf('/');
  return idx >= 0 && idx < path.length - 1 ? path.slice(idx + 1) : path;
}

/**
 * 本棚画面の状態管理・2段階即時ロード・カテゴリ複数AND検索・カスタム順永続化を担当する ViewModel フック。
 *
 * [責任]: `bookshelf.db` からの即時表示、バックグラウンド差分同期、カテゴリCRUD・フォルダ紐付け、カスタム並び替え保存。
 * [影響する状態]: 本棚UI State、`bookshelf.db`、`filesDir/bookshelf_thumbs`、共有設定。
 * [潜在的エラー]: ネイティブI/O・DB・ソート例外はすべて `try-catch` で捕捉し安全状態にフォールバックする。
 */
export function useBookshelfViewModel() {
  const [favoriteFolders, setFavoriteFolders] = useState<FavoriteFolder[]>([]);
  const [categories, setCategories] = useState<BookshelfCategory[]>([]);
  const [selectedCategoryIds, setSelectedCategoryIds] = useState<number[]>([]);
  const [settings, setSettings] = useState(AppSettingsStore.getSnapshot() ?? DEFAULT_APP_SETTINGS);
  const [searchQuery, setSearchQuery] = useState('');
  const [isSearchActive, setIsSearchActive] = useState(false);
  const [isLoading, setIsLoading] = useState(false);

  const folderSortRef = useRef<SortOrder>(settings.folderSortOrder);
  folderSortRef.current = settings.folderSortOrder;
  const foldersRef = useRef<FavoriteFolder[]>([]);
  foldersRef.current = favoriteFolders;
  const syncTokenRef = useRef(0);

  const syncFavoriteFolders = useCallback(async (folderPaths: string[], showHidden: boolean) => {
    const token = ++syncTokenRef.current;
    try {
      const [cats, cachedList] = await Promise.all([
        BookshelfDb.getAllCategories(),
        BookshelfDb.getAllCachedFolders(),
      ]);
      if (token !== syncTokenRef.current) return;

      const attachedCached = await BookshelfDb.attachCategories(cachedList, cats);
      const cachedMap = new Map(attachedCached.map((f) => [f.path, f]));

      // 第1段階: DB キャッシュのみで即時描画
      const instant = folderPaths
        .map((p) => cachedMap.get(p))
        .filter((f): f is FavoriteFolder => f !== undefined);
      setCategories(cats);
      if (instant.length > 0) {
        setFavoriteFolders(sortFavoriteFolders(instant, folderSortRef.current));
        setIsLoading(false);
      } else {
        setIsLoading(folderPaths.length > 0);
      }

      // 第2段階: フォルダ実在・サムネイル実在・更新日時の差分確認
      const checkTargets: string[] = [];
      for (const p of folderPaths) {
        checkTargets.push(p);
        const tp = cachedMap.get(p)?.thumbPath;
        if (tp) checkTargets.push(tp);
      }
      const existMap = await EevStorage.checkExistingFiles(checkTargets);
      if (token !== syncTokenRef.current) return;

      let nextCustomOrder =
        attachedCached.reduce((max, item) => (item.customOrder < 2147483647 ? Math.max(max, item.customOrder) : max), -1) +
        1;

      const validPaths: string[] = [];
      const syncedList: FavoriteFolder[] = [];

      for (const path of folderPaths) {
        try {
          if (existMap[path] === false) continue;
          const cached = cachedMap.get(path);
          const thumbReady = Boolean(cached?.thumbPath && existMap[cached.thumbPath]);
          const dirsCheck = await EevStorage.getFolderMetadata(path, showHidden, 'bookshelf_thumbs');
          if (token !== syncTokenRef.current) return;

          // 実在しない、または権限で読めない場合の補助チェック
          if (existMap[path] !== true && dirsCheck.lastModified === 0) {
            continue;
          }
          validPaths.push(path);

          if (cached && cached.lastModified === dirsCheck.lastModified && thumbReady) {
            syncedList.push(cached);
          } else {
            const customOrder =
              cached && cached.customOrder < 2147483647 ? cached.customOrder : nextCustomOrder++;
            syncedList.push({
              path,
              name: getFolderName(path),
              lastModified: dirsCheck.lastModified,
              thumbPath: dirsCheck.thumbPath,
              imageCount: dirsCheck.imageCount,
              hasTransDb: dirsCheck.hasTransDb,
              customOrder,
              categoryIds: cached?.categoryIds ?? [],
              categoryNames: cached?.categoryNames ?? [],
            });
          }
        } catch (e) {
          console.warn('Bookshelf sync folder error:', path, e);
        }
      }

      if (token !== syncTokenRef.current) return;

      if (validPaths.length !== folderPaths.length) {
        await AppSettingsStore.setFavoriteFolders(validPaths);
      }
      await BookshelfDb.retainOnly(validPaths);
      await BookshelfDb.upsertFolders(syncedList);

      const latestCats = await BookshelfDb.getAllCategories();
      const withCategories = await BookshelfDb.attachCategories(syncedList, latestCats);
      if (token !== syncTokenRef.current) return;

      setCategories(latestCats);
      setFavoriteFolders(sortFavoriteFolders(withCategories, folderSortRef.current));
    } catch (e) {
      console.warn('syncFavoriteFolders error:', e);
    } finally {
      if (token === syncTokenRef.current) {
        setIsLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    let lastFavKey = '';
    let lastHidden = DEFAULT_APP_SETTINGS.showHiddenFiles;
    let lastSort = DEFAULT_APP_SETTINGS.folderSortOrder;
    let mounted = true;

    const handleSnapshot = (snap: typeof settings) => {
      if (!mounted) return;
      setSettings(snap);

      const favKey = snap.favoriteFolders.join('\n');
      if (favKey !== lastFavKey || snap.showHiddenFiles !== lastHidden) {
        lastFavKey = favKey;
        lastHidden = snap.showHiddenFiles;
        void syncFavoriteFolders(snap.favoriteFolders, snap.showHiddenFiles);
      } else if (snap.folderSortOrder !== lastSort) {
        lastSort = snap.folderSortOrder;
        setFavoriteFolders((prev) => sortFavoriteFolders(prev, snap.folderSortOrder));
      }
    };

    void AppSettingsStore.load().then((snap) => {
      handleSnapshot(snap);
    });
    const unsubscribe = AppSettingsStore.subscribe(handleSnapshot);
    return () => {
      mounted = false;
      unsubscribe();
    };
  }, [syncFavoriteFolders]);

  const refreshCategoryAssignmentsInMemory = useCallback(async () => {
    try {
      const latestCats = await BookshelfDb.getAllCategories();
      const updated = await BookshelfDb.attachCategories(foldersRef.current, latestCats);
      setCategories(latestCats);
      setFavoriteFolders(sortFavoriteFolders(updated, folderSortRef.current));
    } catch (e) {
      console.warn('refreshCategoryAssignmentsInMemory error:', e);
    }
  }, []);

  const toggleCategoryFilter = useCallback((categoryId: number | null) => {
    setSelectedCategoryIds((prev) => {
      if (categoryId === null) return [];
      if (categoryId === UNCATEGORIZED_CATEGORY_ID) {
        return prev.includes(UNCATEGORIZED_CATEGORY_ID) ? [] : [UNCATEGORIZED_CATEGORY_ID];
      }
      const base = prev.filter((id) => id !== UNCATEGORIZED_CATEGORY_ID);
      return base.includes(categoryId)
        ? base.filter((id) => id !== categoryId)
        : [...base, categoryId];
    });
  }, []);

  const addCategory = useCallback(
    async (name: string): Promise<BookshelfCategory | null> => {
      const created = await BookshelfDb.addCategory(name);
      await refreshCategoryAssignmentsInMemory();
      return created;
    },
    [refreshCategoryAssignmentsInMemory]
  );

  const renameCategory = useCallback(
    async (categoryId: number, newName: string) => {
      await BookshelfDb.renameCategory(categoryId, newName);
      await refreshCategoryAssignmentsInMemory();
    },
    [refreshCategoryAssignmentsInMemory]
  );

  const deleteCategory = useCallback(
    async (categoryId: number) => {
      await BookshelfDb.deleteCategory(categoryId);
      setSelectedCategoryIds((prev) => prev.filter((id) => id !== categoryId));
      await refreshCategoryAssignmentsInMemory();
    },
    [refreshCategoryAssignmentsInMemory]
  );

  const updateFolderCategories = useCallback(
    async (folderPath: string, categoryIds: number[]) => {
      await BookshelfDb.setFolderCategories(folderPath, categoryIds);
      await refreshCategoryAssignmentsInMemory();
    },
    [refreshCategoryAssignmentsInMemory]
  );

  /**
   * カスタム並び替え：指定フォルダを前（-1）または後（+1）へ移動して即座に DB 保存する。
   */
  const moveFolderStep = useCallback((folderPath: string, delta: number) => {
    try {
      const current = [...foldersRef.current];
      const idx = current.findIndex((f) => f.path === folderPath);
      const nextIdx = idx + delta;
      if (idx < 0 || nextIdx < 0 || nextIdx >= current.length) return;

      const [moved] = current.splice(idx, 1);
      current.splice(nextIdx, 0, moved);
      const reindexed = current.map((item, i) => ({ ...item, customOrder: i }));
      setFavoriteFolders(reindexed);
      void BookshelfDb.updateCustomOrders(reindexed.map((item) => item.path));
      if (folderSortRef.current !== SortOrder.CUSTOM) {
        void AppSettingsStore.setFolderSortOrder(SortOrder.CUSTOM);
      }
    } catch (e) {
      console.warn('moveFolderStep error:', e);
    }
  }, []);

  /**
   * ドラッグ＆ドロップ中に2つのフォルダ位置をメモリ上で入れ替える。
   */
  const moveFolderInMemory = useCallback((fromPath: string, toPath: string) => {
    try {
      if (fromPath === toPath) return;
      const current = [...foldersRef.current];
      const fromIdx = current.findIndex((f) => f.path === fromPath);
      const toIdx = current.findIndex((f) => f.path === toPath);
      if (fromIdx < 0 || toIdx < 0) return;

      const [moved] = current.splice(fromIdx, 1);
      current.splice(toIdx, 0, moved);
      const reindexed = current.map((item, idx) => ({ ...item, customOrder: idx }));
      setFavoriteFolders(reindexed);
    } catch (e) {
      console.warn('moveFolderInMemory error:', e);
    }
  }, []);

  /**
   * ドラッグ終了時に現在のカスタム並び順を `bookshelf.db` に永続化し、ソート順を `CUSTOM` へ設定する。
   */
  const commitCustomOrder = useCallback(async () => {
    try {
      const orderedPaths = foldersRef.current.map((f) => f.path);
      await BookshelfDb.updateCustomOrders(orderedPaths);
      await AppSettingsStore.setFolderSortOrder(SortOrder.CUSTOM);
    } catch (e) {
      console.warn('commitCustomOrder error:', e);
    }
  }, []);

  const removeFavorite = useCallback(async (folderPath: string) => {
    try {
      await AppSettingsStore.removeFavoriteFolder(folderPath);
      await EevStorage.deleteThumbnail(folderPath, 'bookshelf_thumbs');
    } catch (e) {
      console.warn('removeFavorite error:', e);
    }
  }, []);

  const filteredFavoriteFolders = useMemo(() => {
    try {
      let byCategory = favoriteFolders;
      if (selectedCategoryIds.length > 0) {
        if (selectedCategoryIds.includes(UNCATEGORIZED_CATEGORY_ID)) {
          byCategory = favoriteFolders.filter((f) => f.categoryIds.length === 0);
        } else {
          byCategory = favoriteFolders.filter((f) =>
            selectedCategoryIds.every((id) => f.categoryIds.includes(id))
          );
        }
      }
      const q = searchQuery.trim().toLowerCase();
      if (!q) return byCategory;
      return byCategory.filter((f) => f.name.toLowerCase().includes(q));
    } catch (e) {
      console.warn('filteredFavoriteFolders error:', e);
      return favoriteFolders;
    }
  }, [favoriteFolders, selectedCategoryIds, searchQuery]);

  const uiState: BookshelfUiState = {
    favoriteFolders,
    categories,
    selectedCategoryIds,
    folderSortOrder: settings.folderSortOrder,
    fileSortOrder: settings.fileSortOrder,
    viewMode: settings.viewMode,
    thumbnailSize: settings.thumbnailSize,
    showItemInfo: settings.showItemInfo,
    searchQuery,
    isSearchActive,
    isLoading,
  };

  return {
    uiState,
    filteredFavoriteFolders,
    toggleCategoryFilter,
    addCategory,
    renameCategory,
    deleteCategory,
    updateFolderCategories,
    moveFolderStep,
    moveFolderInMemory,
    commitCustomOrder,
    removeFavorite,
    updateSearchQuery: setSearchQuery,
    toggleSearchActive: (active: boolean) => {
      setIsSearchActive(active);
      if (!active) setSearchQuery('');
    },
    updateFolderSortOrder: (order: SortOrder) => void AppSettingsStore.setFolderSortOrder(order),
    updateFileSortOrder: (order: SortOrder) => void AppSettingsStore.setFileSortOrder(order),
    updateViewMode: (mode: ViewMode) => void AppSettingsStore.setViewMode(mode),
    updateThumbnailSize: (size: ThumbnailSize) => void AppSettingsStore.setThumbnailSize(size),
    updateShowItemInfo: (enabled: boolean) => void AppSettingsStore.setShowItemInfo(enabled),
  };
}
