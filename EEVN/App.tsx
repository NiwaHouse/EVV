import React, { useCallback, useState } from 'react';
import { StyleSheet, ToastAndroid } from 'react-native';
import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context';
import { FavoriteFolder, Screen, SortOrder } from './src/core/models/types';
import { AppTheme } from './src/core/ui/AppTheme';
import { useAppBackHandler } from './src/core/ui/AppBackHandler';
import { BookshelfScreen } from './src/features/bookshelf/BookshelfScreen';
import { useBookshelfViewModel } from './src/features/bookshelf/useBookshelfViewModel';
import { ExplorerScreen } from './src/features/explorer/ExplorerScreen';
import { useExplorerViewModel } from './src/features/explorer/useExplorerViewModel';
import { SettingsScreen } from './src/features/settings/SettingsScreen';
import { ViewerScreen } from './src/features/viewer/ViewerScreen';
import { getSortedImagePaths } from './src/features/viewer/ViewerDirectoryNavigator';
import { useViewerViewModel } from './src/features/viewer/useViewerViewModel';

/**
 * EEVN アプリケーションルートコンポーネント。
 *
 * エクスプローラ・本棚・画像ビューワ・設定の 4 画面ルーティングと
 * システム戻るボタン制御、本棚カテゴリ絞り込み状態のビューワ引き継ぎを統合する。
 *
 * [責任]: 画面遷移制御、ViewModel 統合、システムバック制御。
 * [影響する状態]: 現在画面 (`Screen`)、ビューワ起動パラメータ。
 * [潜在的エラー]: ビューワ起動時の画像探索例外は `try-catch` で捕捉しトースト通知して保護する。
 */
export default function App() {
  const [currentScreen, setCurrentScreen] = useState<Screen>({ type: 'explorer' });

  const explorerVm = useExplorerViewModel();
  const bookshelfVm = useBookshelfViewModel();
  const viewerVm = useViewerViewModel();

  const handleOpenViewerFromExplorer = useCallback(
    (directoryPath: string, initialFilePath: string) => {
      void viewerVm.loadImagesFromDirectory(
        directoryPath,
        initialFilePath,
        explorerVm.settings.fileSortOrder,
        false
      );
      setCurrentScreen({
        type: 'viewer',
        directoryPath,
        initialFilePath,
        isFromBookshelf: false,
      });
    },
    [explorerVm.settings.fileSortOrder, viewerVm]
  );

  const handleOpenViewerFromBookshelf = useCallback(
    async (folder: FavoriteFolder) => {
      try {
        const uiState = bookshelfVm.uiState;
        const images = await getSortedImagePaths(
          folder.path,
          uiState.fileSortOrder,
          explorerVm.settings.showHiddenFiles
        );
        const firstImage = images[0];
        if (!firstImage) {
          ToastAndroid.show('フォルダ内に画像が見つかりません', ToastAndroid.SHORT);
          return;
        }
        const launchSortOrder =
          uiState.folderSortOrder === SortOrder.CUSTOM ||
          uiState.folderSortOrder === SortOrder.CUSTOM_DESC
            ? uiState.folderSortOrder
            : uiState.fileSortOrder;

        const customOrders: Record<string, number> = {};
        for (const f of uiState.favoriteFolders) {
          customOrders[f.path] = f.customOrder;
        }
        const targetPaths = bookshelfVm.filteredFavoriteFolders.map((f) => f.path);

        await viewerVm.loadImagesFromDirectory(
          folder.path,
          firstImage,
          launchSortOrder,
          true,
          customOrders,
          targetPaths
        );
        setCurrentScreen({
          type: 'viewer',
          directoryPath: folder.path,
          initialFilePath: firstImage,
          isFromBookshelf: true,
        });
      } catch (e) {
        console.warn('handleOpenViewerFromBookshelf error:', e);
        ToastAndroid.show('ビューワを開けませんでした', ToastAndroid.SHORT);
      }
    },
    [bookshelfVm.filteredFavoriteFolders, bookshelfVm.uiState, explorerVm.settings.showHiddenFiles, viewerVm]
  );

  const handleBackPress = useCallback((): boolean => {
    switch (currentScreen.type) {
      case 'explorer':
        if (explorerVm.isSearchActive) {
          explorerVm.toggleSearchActive(false);
          return true;
        }
        if (explorerVm.canNavigateBack) {
          return explorerVm.navigateBack();
        }
        return false;
      case 'bookshelf':
        if (bookshelfVm.uiState.isSearchActive) {
          bookshelfVm.toggleSearchActive(false);
          return true;
        }
        setCurrentScreen({ type: 'explorer' });
        return true;
      case 'viewer':
        if (viewerVm.uiState.isBottomSheetVisible) {
          viewerVm.setBottomSheetVisible(false);
          return true;
        }
        if (viewerVm.uiState.isTripleTapDialogVisible) {
          viewerVm.setTripleTapDialogVisible(false);
          return true;
        }
        setCurrentScreen(
          currentScreen.isFromBookshelf ? { type: 'bookshelf' } : { type: 'explorer' }
        );
        return true;
      case 'settings':
        setCurrentScreen(
          currentScreen.fromBookshelf ? { type: 'bookshelf' } : { type: 'explorer' }
        );
        return true;
    }
  }, [bookshelfVm, currentScreen, explorerVm, viewerVm]);

  useAppBackHandler(handleBackPress);

  const isViewer = currentScreen.type === 'viewer';

  return (
    <SafeAreaProvider>
      <SafeAreaView
        style={[styles.safeArea, isViewer && styles.viewerSafeArea]}
        edges={isViewer ? [] : ['top', 'bottom']}
      >
        <StatusBar style={isViewer ? 'light' : 'dark'} hidden={isViewer} />

        {currentScreen.type === 'explorer' && (
          <ExplorerScreen
            vm={explorerVm}
            onOpenBookshelf={() => setCurrentScreen({ type: 'bookshelf' })}
            onOpenSettings={() => setCurrentScreen({ type: 'settings', fromBookshelf: false })}
            onOpenViewerFromFolder={handleOpenViewerFromExplorer}
          />
        )}

        {currentScreen.type === 'bookshelf' && (
          <BookshelfScreen
            vm={bookshelfVm}
            onBackClick={() => setCurrentScreen({ type: 'explorer' })}
            onOpenFolderViewer={(folder) => void handleOpenViewerFromBookshelf(folder)}
            onSettingsClick={() => setCurrentScreen({ type: 'settings', fromBookshelf: true })}
          />
        )}

        {currentScreen.type === 'viewer' && (
          <ViewerScreen
            vm={viewerVm}
            onCloseViewer={() =>
              setCurrentScreen(
                currentScreen.isFromBookshelf ? { type: 'bookshelf' } : { type: 'explorer' }
              )
            }
          />
        )}

        {currentScreen.type === 'settings' && (
          <SettingsScreen
            onBackClick={() =>
              setCurrentScreen(
                currentScreen.fromBookshelf ? { type: 'bookshelf' } : { type: 'explorer' }
              )
            }
            onCacheCleared={explorerVm.refreshCurrentDirectory}
          />
        )}
      </SafeAreaView>
    </SafeAreaProvider>
  );
}

const styles = StyleSheet.create({
  safeArea: { flex: 1, backgroundColor: AppTheme.background },
  viewerSafeArea: { backgroundColor: AppTheme.viewerBg },
});
