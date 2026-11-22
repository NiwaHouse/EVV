import { useCallback, useEffect, useRef, useState } from 'react';
import { CropPosition, SortOrder } from '../../core/models/types';
import { AppSettingsStore, DEFAULT_APP_SETTINGS } from '../../core/storage/AppSettingsStore';
import {
  findNextDirectory,
  findPreviousDirectory,
  getSortedImagePaths,
} from './ViewerDirectoryNavigator';

/**
 * 画像ビューワ画面の UI State 型。
 */
export interface ViewerUiState {
  directoryPath: string;
  imagePaths: string[];
  currentIndex: number;
  cropPosition: CropPosition;
  viewerSortOrder: SortOrder;
  isMangaMode: boolean;
  showHiddenFiles: boolean;
  isFromBookshelf: boolean;
  isCurrentFolderFavorite: boolean;
  isBottomSheetVisible: boolean;
  isTripleTapDialogVisible: boolean;
  isLoading: boolean;
}

/**
 * 画像ビューワの状態管理・漫画モード半面送り・本棚カテゴリ連動フォルダ移動を担う ViewModel フック。
 *
 * [責任]: 画像一覧ロード、前後ページ/半面送り、次前フォルダ循環移動、独自ソート管理、お気に入り切替。
 * [影響する状態]: `ViewerUiState`、共有設定（`viewerSortOrder`, `isMangaMode`, `showHiddenFiles`, `favoriteFolders`）。
 * [潜在的エラー]: ストレージ走査・ソート例外時は `try-catch` で捕捉し安全状態へフォールバックする。
 */
export function useViewerViewModel() {
  const [directoryPath, setDirectoryPath] = useState('');
  const [imagePaths, setImagePaths] = useState<string[]>([]);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [cropPosition, setCropPosition] = useState<CropPosition>('FULL');
  const [viewerSortOrder, setViewerSortOrder] = useState<SortOrder>(
    DEFAULT_APP_SETTINGS.viewerSortOrder
  );
  const [isMangaMode, setIsMangaMode] = useState(DEFAULT_APP_SETTINGS.isMangaMode);
  const [showHiddenFiles, setShowHiddenFiles] = useState(DEFAULT_APP_SETTINGS.showHiddenFiles);
  const [favoriteFolders, setFavoriteFolders] = useState<string[]>([]);
  const [isFromBookshelf, setIsFromBookshelf] = useState(false);
  const [isBottomSheetVisible, setIsBottomSheetVisible] = useState(false);
  const [isTripleTapDialogVisible, setIsTripleTapDialogVisible] = useState(false);
  const [isLoading, setIsLoading] = useState(false);

  const bookshelfCustomOrderMapRef = useRef<Record<string, number>>({});
  const bookshelfTargetPathsRef = useRef<string[]>([]);

  const stateRef = useRef({
    directoryPath,
    imagePaths,
    currentIndex,
    cropPosition,
    viewerSortOrder,
    isMangaMode,
    showHiddenFiles,
    favoriteFolders,
    isFromBookshelf,
  });
  stateRef.current = {
    directoryPath,
    imagePaths,
    currentIndex,
    cropPosition,
    viewerSortOrder,
    isMangaMode,
    showHiddenFiles,
    favoriteFolders,
    isFromBookshelf,
  };

  useEffect(() => {
    let mounted = true;
    const sync = (snap: typeof DEFAULT_APP_SETTINGS) => {
      if (!mounted) return;
      setIsMangaMode(snap.isMangaMode);
      setShowHiddenFiles(snap.showHiddenFiles);
      setFavoriteFolders(snap.favoriteFolders);
      setCropPosition((prev) => {
        if (snap.isMangaMode) return prev === 'FULL' ? 'RIGHT_HALF' : prev;
        return 'FULL';
      });
    };
    void AppSettingsStore.load().then(sync);
    const unsub = AppSettingsStore.subscribe(sync);
    return () => {
      mounted = false;
      unsub();
    };
  }, []);

  const loadImagesFromDirectory = useCallback(
    async (
      dirPath: string,
      initialFilePath: string,
      sortOrder: SortOrder,
      fromBookshelf: boolean = false,
      customOrderMap: Record<string, number> = {},
      targetPaths: string[] = []
    ) => {
      setIsLoading(true);
      try {
        setIsFromBookshelf(fromBookshelf);
        bookshelfCustomOrderMapRef.current = fromBookshelf ? customOrderMap : {};
        bookshelfTargetPathsRef.current = fromBookshelf ? targetPaths : [];

        const snap = await AppSettingsStore.load();
        const paths = await getSortedImagePaths(dirPath, sortOrder, snap.showHiddenFiles);
        const initialIdx = Math.max(0, paths.indexOf(initialFilePath));

        setDirectoryPath(dirPath);
        setImagePaths(paths);
        setCurrentIndex(initialIdx);
        setViewerSortOrder(sortOrder);
        setIsMangaMode(snap.isMangaMode);
        setShowHiddenFiles(snap.showHiddenFiles);
        setFavoriteFolders(snap.favoriteFolders);
        setCropPosition(snap.isMangaMode ? 'RIGHT_HALF' : 'FULL');
        setIsBottomSheetVisible(false);
        setIsTripleTapDialogVisible(false);
      } catch (e) {
        console.warn('loadImagesFromDirectory error:', dirPath, e);
      } finally {
        setIsLoading(false);
      }
    },
    []
  );

  const navigateDirectory = useCallback(async (forward: boolean): Promise<boolean> => {
    try {
      const s = stateRef.current;
      const targets =
        bookshelfTargetPathsRef.current.length > 0
          ? bookshelfTargetPathsRef.current
          : s.favoriteFolders;

      const result = forward
        ? await findNextDirectory(
            s.directoryPath,
            s.viewerSortOrder,
            s.showHiddenFiles,
            s.isFromBookshelf,
            targets,
            bookshelfCustomOrderMapRef.current
          )
        : await findPreviousDirectory(
            s.directoryPath,
            s.viewerSortOrder,
            s.showHiddenFiles,
            s.isFromBookshelf,
            targets,
            bookshelfCustomOrderMapRef.current
          );

      if (!result || result.imagePaths.length === 0) return false;
      setDirectoryPath(result.directoryPath);
      setImagePaths(result.imagePaths);
      if (forward) {
        setCurrentIndex(0);
        setCropPosition(s.isMangaMode ? 'RIGHT_HALF' : 'FULL');
      } else {
        setCurrentIndex(result.imagePaths.length - 1);
        setCropPosition(s.isMangaMode ? 'LEFT_HALF' : 'FULL');
      }
      return true;
    } catch (e) {
      console.warn('Viewer navigateDirectory error:', e);
      return false;
    }
  }, []);

  const nextPage = useCallback(() => {
    const s = stateRef.current;
    if (s.imagePaths.length === 0) return;
    if (s.isMangaMode && s.cropPosition === 'RIGHT_HALF') {
      setCropPosition('LEFT_HALF');
      return;
    }
    if (s.currentIndex + 1 < s.imagePaths.length) {
      setCurrentIndex(s.currentIndex + 1);
      setCropPosition(s.isMangaMode ? 'RIGHT_HALF' : 'FULL');
    } else {
      void navigateDirectory(true);
    }
  }, [navigateDirectory]);

  const prevPage = useCallback(() => {
    const s = stateRef.current;
    if (s.imagePaths.length === 0) return;
    if (s.isMangaMode && s.cropPosition === 'LEFT_HALF') {
      setCropPosition('RIGHT_HALF');
      return;
    }
    if (s.currentIndex > 0) {
      setCurrentIndex(s.currentIndex - 1);
      setCropPosition(s.isMangaMode ? 'LEFT_HALF' : 'FULL');
    } else {
      void navigateDirectory(false);
    }
  }, [navigateDirectory]);

  const jumpToFirstImage = useCallback(() => {
    const s = stateRef.current;
    if (s.imagePaths.length === 0) return;
    setCurrentIndex(0);
    setCropPosition(s.isMangaMode ? 'RIGHT_HALF' : 'FULL');
  }, []);

  const updateViewerSortOrder = useCallback(async (order: SortOrder) => {
    try {
      const s = stateRef.current;
      const anchor = s.imagePaths[s.currentIndex];
      setViewerSortOrder(order);
      await AppSettingsStore.setViewerSortOrder(order);
      if (s.directoryPath) {
        const sorted = await getSortedImagePaths(s.directoryPath, order, s.showHiddenFiles);
        const nextIdx = anchor ? Math.max(0, sorted.indexOf(anchor)) : 0;
        setImagePaths(sorted);
        setCurrentIndex(nextIdx);
      }
    } catch (e) {
      console.warn('updateViewerSortOrder error:', e);
    }
  }, []);

  const updateMangaMode = useCallback(async (enabled: boolean) => {
    try {
      setIsMangaMode(enabled);
      setCropPosition(enabled ? 'RIGHT_HALF' : 'FULL');
      await AppSettingsStore.setIsMangaMode(enabled);
    } catch (e) {
      console.warn('updateMangaMode error:', e);
    }
  }, []);

  const updateShowHiddenFiles = useCallback(async (enabled: boolean) => {
    try {
      const s = stateRef.current;
      const anchor = s.imagePaths[s.currentIndex];
      setShowHiddenFiles(enabled);
      await AppSettingsStore.setShowHiddenFiles(enabled);
      if (s.directoryPath) {
        const refreshed = await getSortedImagePaths(s.directoryPath, s.viewerSortOrder, enabled);
        const nextIdx = anchor ? Math.max(0, refreshed.indexOf(anchor)) : 0;
        setImagePaths(refreshed);
        setCurrentIndex(nextIdx);
      }
    } catch (e) {
      console.warn('updateShowHiddenFiles error:', e);
    }
  }, []);

  const toggleFavoriteCurrentFolder = useCallback(async () => {
    try {
      const s = stateRef.current;
      if (!s.directoryPath) return;
      if (s.favoriteFolders.includes(s.directoryPath)) {
        await AppSettingsStore.removeFavoriteFolder(s.directoryPath);
      } else {
        await AppSettingsStore.addFavoriteFolder(s.directoryPath);
      }
    } catch (e) {
      console.warn('toggleFavoriteCurrentFolder error:', e);
    }
  }, []);

  const uiState: ViewerUiState = {
    directoryPath,
    imagePaths,
    currentIndex,
    cropPosition,
    viewerSortOrder,
    isMangaMode,
    showHiddenFiles,
    isFromBookshelf,
    isCurrentFolderFavorite: favoriteFolders.includes(directoryPath),
    isBottomSheetVisible,
    isTripleTapDialogVisible,
    isLoading,
  };

  return {
    uiState,
    loadImagesFromDirectory,
    nextPage,
    prevPage,
    jumpToFirstImage,
    navigateToNextDirectory: () => void navigateDirectory(true),
    navigateToPreviousDirectory: () => void navigateDirectory(false),
    updateViewerSortOrder: (order: SortOrder) => void updateViewerSortOrder(order),
    updateMangaMode: (enabled: boolean) => void updateMangaMode(enabled),
    updateShowHiddenFiles: (enabled: boolean) => void updateShowHiddenFiles(enabled),
    toggleFavoriteCurrentFolder: () => void toggleFavoriteCurrentFolder(),
    setBottomSheetVisible: setIsBottomSheetVisible,
    setTripleTapDialogVisible: setIsTripleTapDialogVisible,
  };
}
