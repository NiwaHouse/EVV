import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AppState } from 'react-native';
import { EevStorage } from '../../../modules/eev-storage';
import { FileItem, SortOrder, ThumbnailSize, ViewMode } from '../../core/models/types';
import { AppSettingsStore, DEFAULT_APP_SETTINGS } from '../../core/storage/AppSettingsStore';
import { CachedFolderMetadata, FolderMetadataDb } from '../../core/storage/FolderMetadataDb';
import { sortFileItemsSeparately } from '../../core/storage/SortUtils';

const METADATA_BATCH_SIZE = 6;

/**
 * エクスプローラ画面の UI State および CRUD・一括削除ロジックを提供する ViewModel フック。
 *
 * 通常フォルダの永続サムネイル（`filesDir/folder_thumbs`）＋ SQLite DB（`folder_cache.db`）による
 * 2段階即時ロードと、本棚未登録フォルダの一括削除（サブフォルダ・本棚子孫保護）を担当する。
 *
 * [責任]: 階層ナビゲーション、2段階フォルダメタデータ読込、個別削除、本棚未登録フォルダ抽出/一括削除、共有設定反映。
 * [影響する状態]: エクスプローラ一覧、`folder_cache.db`、`filesDir/folder_thumbs`、端末ストレージ。
 * [潜在的エラー]: ストレージ/SQLite 例外時は `try-catch` で捕捉し安全状態へフォールバックする。
 */
export function useExplorerViewModel() {
  const [rootPath, setRootPath] = useState('');
  const [currentPath, setCurrentPath] = useState('');
  const [historyStack, setHistoryStack] = useState<string[]>([]);
  const [items, setItems] = useState<FileItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [hasStoragePermission, setHasStoragePermission] = useState(true);
  const [settings, setSettings] = useState(AppSettingsStore.getSnapshot() ?? DEFAULT_APP_SETTINGS);
  const [searchQuery, setSearchQuery] = useState('');
  const [isSearchActive, setIsSearchActive] = useState(false);

  const loadTokenRef = useRef(0);
  const currentPathRef = useRef('');
  currentPathRef.current = currentPath;
  const settingsRef = useRef(settings);
  settingsRef.current = settings;

  const loadDirectory = useCallback(async (targetPath: string, showHidden: boolean) => {
    const token = ++loadTokenRef.current;
    setIsLoading(true);
    try {
      const rawEntries = await EevStorage.listDirectory(targetPath, showHidden);
      if (token !== loadTokenRef.current) return;

      const baseItems: FileItem[] = rawEntries.map((e) => ({
        path: e.path,
        name: e.name,
        isDirectory: e.isDirectory,
        lastModified: e.lastModified,
        size: e.size,
        isImage: e.isImage,
        imageCount: 0,
        thumbPath: null,
        hasTransDb: false,
        hasSubDirs: false,
      }));

      const dirPaths = baseItems.filter((i) => i.isDirectory).map((i) => i.path);
      const cachedMap = await FolderMetadataDb.getByPaths(dirPaths);
      if (token !== loadTokenRef.current) return;

      // サムネイルパスの実在確認を一括で実施
      const thumbPathsToCheck = Array.from(cachedMap.values())
        .map((c) => c.thumbPath)
        .filter((p): p is string => Boolean(p));
      const existingThumbMap = await EevStorage.checkExistingFiles(thumbPathsToCheck);
      if (token !== loadTokenRef.current) return;

      // 第1段階: キャッシュ済みメタデータをマージして即座に表示
      const mergedItems = baseItems.map((item) => {
        if (!item.isDirectory) return item;
        const cached = cachedMap.get(item.path);
        if (!cached) return item;
        const validThumb =
          cached.thumbPath && existingThumbMap[cached.thumbPath] ? cached.thumbPath : null;
        return {
          ...item,
          imageCount: cached.imageCount,
          thumbPath: validThumb,
          hasTransDb: cached.hasTransDb,
          hasSubDirs: cached.hasSubDirs,
        };
      });

      const currentSnap = settingsRef.current;
      setItems(
        sortFileItemsSeparately(mergedItems, currentSnap.folderSortOrder, currentSnap.fileSortOrder)
      );
      setIsLoading(false);

      // 第2段階: 更新があるか未キャッシュのフォルダのみバックグラウンドで差分算出＆永続化
      const staleDirs = baseItems.filter((item) => {
        if (!item.isDirectory) return false;
        const cached = cachedMap.get(item.path);
        if (!cached) return true;
        if (Math.trunc(cached.lastModified) !== Math.trunc(item.lastModified)) return true;
        if (cached.imageCount > 0 && (!cached.thumbPath || !existingThumbMap[cached.thumbPath])) {
          return true;
        }
        return false;
      });

      for (let i = 0; i < staleDirs.length; i += METADATA_BATCH_SIZE) {
        if (token !== loadTokenRef.current) return;
        const batch = staleDirs.slice(i, i + METADATA_BATCH_SIZE);
        const computedPairs = await Promise.all(
          batch.map(async (dir) => {
            const meta = await EevStorage.getFolderMetadata(dir.path, showHidden, 'folder_thumbs');
            return { path: dir.path, lastModified: dir.lastModified, meta };
          })
        );
        if (token !== loadTokenRef.current) return;

        const cacheUpserts: CachedFolderMetadata[] = [];
        const updateByPath = new Map<string, (typeof computedPairs)[number]['meta']>();
        for (const pair of computedPairs) {
          updateByPath.set(pair.path, pair.meta);
          cacheUpserts.push({
            folderPath: pair.path,
            lastModified: pair.lastModified,
            imageCount: pair.meta.imageCount,
            thumbPath: pair.meta.thumbPath,
            hasTransDb: pair.meta.hasTransDb,
            hasSubDirs: pair.meta.hasSubDirs,
          });
        }
        await FolderMetadataDb.upsertBatch(cacheUpserts);

        if (token !== loadTokenRef.current) return;
        setItems((prev) => {
          const updated = prev.map((it) => {
            const meta = updateByPath.get(it.path);
            if (!meta) return it;
            return {
              ...it,
              imageCount: meta.imageCount,
              thumbPath: meta.thumbPath,
              hasTransDb: meta.hasTransDb,
              hasSubDirs: meta.hasSubDirs,
            };
          });
          const s = settingsRef.current;
          return sortFileItemsSeparately(updated, s.folderSortOrder, s.fileSortOrder);
        });
      }
    } catch (e) {
      console.warn('loadDirectory error:', targetPath, e);
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    let mounted = true;
    let lastShowHidden = DEFAULT_APP_SETTINGS.showHiddenFiles;
    let lastFolderSort = DEFAULT_APP_SETTINGS.folderSortOrder;
    let lastFileSort = DEFAULT_APP_SETTINGS.fileSortOrder;

    const bootstrap = async () => {
      try {
        const [loadedSettings, granted, initialRoot] = await Promise.all([
          AppSettingsStore.load(),
          EevStorage.hasAllFilesAccess(),
          EevStorage.getInitialRootPath(),
        ]);
        if (!mounted) return;
        setSettings(loadedSettings);
        setHasStoragePermission(granted);
        setRootPath(initialRoot);
        setCurrentPath(initialRoot);
        lastShowHidden = loadedSettings.showHiddenFiles;
        lastFolderSort = loadedSettings.folderSortOrder;
        lastFileSort = loadedSettings.fileSortOrder;
        await loadDirectory(initialRoot, loadedSettings.showHiddenFiles);
      } catch (e) {
        console.warn('Explorer bootstrap error:', e);
        setIsLoading(false);
      }
    };
    void bootstrap();

    const unsubSettings = AppSettingsStore.subscribe((snap) => {
      if (!mounted) return;
      setSettings(snap);
      const path = currentPathRef.current;
      if (snap.showHiddenFiles !== lastShowHidden && path) {
        lastShowHidden = snap.showHiddenFiles;
        void loadDirectory(path, snap.showHiddenFiles);
      } else if (snap.folderSortOrder !== lastFolderSort || snap.fileSortOrder !== lastFileSort) {
        lastFolderSort = snap.folderSortOrder;
        lastFileSort = snap.fileSortOrder;
        setItems((prev) =>
          sortFileItemsSeparately(prev, snap.folderSortOrder, snap.fileSortOrder)
        );
      }
    });

    const appStateSub = AppState.addEventListener('change', (nextState) => {
      if (nextState === 'active') {
        void EevStorage.hasAllFilesAccess().then((granted) => {
          if (!mounted) return;
          setHasStoragePermission(granted);
          if (granted && currentPathRef.current) {
            void loadDirectory(currentPathRef.current, settingsRef.current.showHiddenFiles);
          }
        });
      }
    });

    return () => {
      mounted = false;
      unsubSettings();
      appStateSub.remove();
    };
  }, [loadDirectory]);

  const navigateToFolder = useCallback(
    (targetPath: string, pushHistory: boolean = true) => {
      if (!targetPath || targetPath === currentPathRef.current) return;
      if (pushHistory && currentPathRef.current) {
        setHistoryStack((prev) => [...prev, currentPathRef.current]);
      }
      setSearchQuery('');
      setIsSearchActive(false);
      setCurrentPath(targetPath);
      void loadDirectory(targetPath, settingsRef.current.showHiddenFiles);
    },
    [loadDirectory]
  );

  const navigateBack = useCallback((): boolean => {
    if (historyStack.length === 0) return false;
    const prev = historyStack[historyStack.length - 1];
    setHistoryStack((s) => s.slice(0, -1));
    setSearchQuery('');
    setIsSearchActive(false);
    setCurrentPath(prev);
    void loadDirectory(prev, settingsRef.current.showHiddenFiles);
    return true;
  }, [historyStack, loadDirectory]);

  const refreshCurrentDirectory = useCallback(() => {
    if (currentPathRef.current) {
      void loadDirectory(currentPathRef.current, settingsRef.current.showHiddenFiles);
    }
  }, [loadDirectory]);

  const toggleFavorite = useCallback(async (item: FileItem) => {
    if (!item.isDirectory) return;
    try {
      const isFav = settingsRef.current.favoriteFolders.includes(item.path);
      if (isFav) {
        await AppSettingsStore.removeFavoriteFolder(item.path);
        await EevStorage.deleteThumbnail(item.path, 'bookshelf_thumbs');
      } else {
        await AppSettingsStore.addFavoriteFolder(item.path);
      }
    } catch (e) {
      console.warn('toggleFavorite error:', e);
    }
  }, []);

  const deleteItem = useCallback(
    async (item: FileItem) => {
      try {
        const ok = await EevStorage.deletePath(item.path);
        if (ok && item.isDirectory) {
          await Promise.all([
            EevStorage.deleteThumbnail(item.path, 'folder_thumbs'),
            EevStorage.deleteThumbnail(item.path, 'bookshelf_thumbs'),
            FolderMetadataDb.deleteByPaths([item.path]),
            AppSettingsStore.removeFavoriteFolder(item.path),
          ]);
        }
        refreshCurrentDirectory();
      } catch (e) {
        console.warn('deleteItem error:', e);
      }
    },
    [refreshCurrentDirectory]
  );

  /**
   * 現在フォルダ直下の削除可能な本棚未登録フォルダ一覧を抽出する（安全検証付き）。
   * - 直下の通常ファイルは対象外
   * - 本棚登録済みフォルダ・サブフォルダを含むフォルダ・本棚登録済み子孫を持つフォルダはすべて除外
   */
  const collectDeletableNonFavoriteFolders = useCallback(async (): Promise<FileItem[]> => {
    try {
      const path = currentPathRef.current;
      if (!path) return [];
      const entries = await EevStorage.listDirectory(path, settingsRef.current.showHiddenFiles);
      const favSet = new Set(settingsRef.current.favoriteFolders);
      const favArray = settingsRef.current.favoriteFolders;
      const results: FileItem[] = [];

      for (const entry of entries) {
        if (!entry.isDirectory) continue;
        if (favSet.has(entry.path)) continue;
        const prefix = `${entry.path}/`;
        if (favArray.some((fav) => fav.startsWith(prefix))) continue;

        // 隠しサブフォルダも含め直下にサブディレクトリが無いことを実ストレージで検証
        const subDirs = await EevStorage.listSubDirectories(entry.path, true);
        if (subDirs.length > 0) continue;

        results.push({
          path: entry.path,
          name: entry.name,
          isDirectory: true,
          lastModified: entry.lastModified,
          size: 0,
          isImage: false,
          imageCount: 0,
          thumbPath: null,
          hasTransDb: false,
          hasSubDirs: false,
        });
      }
      return results;
    } catch (e) {
      console.warn('collectDeletableNonFavoriteFolders error:', e);
      return [];
    }
  }, []);

  /**
   * ユーザー承認済みの本棚未登録フォルダを一括削除し、永続キャッシュも掃除する。
   */
  const executeDeleteNonFavoriteFolders = useCallback(
    async (targets: FileItem[]) => {
      if (targets.length === 0) return;
      try {
        const deletedPaths: string[] = [];
        for (const folder of targets) {
          const ok = await EevStorage.deletePath(folder.path);
          if (ok) {
            deletedPaths.push(folder.path);
            await EevStorage.deleteThumbnail(folder.path, 'folder_thumbs');
          }
        }
        await FolderMetadataDb.deleteByPaths(deletedPaths);
        refreshCurrentDirectory();
      } catch (e) {
        console.warn('executeDeleteNonFavoriteFolders error:', e);
      }
    },
    [refreshCurrentDirectory]
  );

  const filteredItems = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    if (!q) return items;
    return items.filter((it) => it.name.toLowerCase().includes(q));
  }, [items, searchQuery]);

  return {
    rootPath,
    currentPath,
    canNavigateBack: historyStack.length > 0,
    items: filteredItems,
    isLoading,
    hasStoragePermission,
    settings,
    searchQuery,
    isSearchActive,
    requestAllFilesAccess: () => void EevStorage.requestAllFilesAccess(),
    navigateToFolder,
    navigateBack,
    refreshCurrentDirectory,
    toggleFavorite,
    deleteItem,
    collectDeletableNonFavoriteFolders,
    executeDeleteNonFavoriteFolders,
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
